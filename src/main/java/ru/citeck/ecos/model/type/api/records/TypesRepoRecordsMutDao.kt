package ru.citeck.ecos.model.type.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.procstages.dto.ProcStageDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.TypeAspectDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.lib.workspace.convertToIdInWsSafe
import ru.citeck.ecos.model.type.service.TypeDesc
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.value.factory.bean.BeanTypeUtils
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateWithAnyResDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

@Component
class TypesRepoRecordsMutDao(
    private val typeService: TypesService,
    private val typesRepoPermsService: TypesRepoPermsService? = null,
    private val workspaceService: WorkspaceService? = null
) : RecordMutateWithAnyResDao, RecordDeleteDao {

    companion object {
        private const val ARTIFACT_UPLOAD_FORM_ID = "ecos-artifact-upload"

        private const val WORKSPACE_ATT = "workspace"
        private const val FORM_REF_ATT = "formRef"
        private const val JOURNAL_REF_ATT = "journalRef"
        private const val NUM_TEMPLATE_REF_ATT = "numTemplateRef"
    }

    override fun getId() = "types-repo"

    override fun mutateForAnyRes(record: LocalRecordAtts): Any? {

        val recToMutate = if (record.id.isNotBlank()) {
            typeService.getById(workspaceService.convertToIdInWsSafe(record.id))
        } else {
            val customId = record.attributes["id"].asText()
            var workspace = record.attributes[WORKSPACE_ATT].asText().ifBlank {
                record.attributes[RecordConstants.ATT_WORKSPACE].asText().toEntityRef().getLocalId()
            }
            if (!isWorkspaceShouldHasScopedTypes(workspace)) {
                workspace = ""
            }
            val existingDef = typeService.getByIdOrNull(IdInWs.create(workspace, customId))
            if (existingDef != null) {
                if (AuthContext.isNotRunAsSystem()) {
                    val formId = record.getAtt("/_formInfo/formId").asText()
                    if (formId.isNotEmpty() && formId != ARTIFACT_UPLOAD_FORM_ID) {
                        error("Type with id '$customId' already exists")
                    }
                }
                existingDef
            } else {
                TypeDef.EMPTY
            }
        }.let { TypeMutRecord(it) }

        val mutAtts = ObjectData.create()
        val aspectsConfigs = hashMapOf<String, ObjectData>()
        record.attributes.forEach { k, v ->
            val aspectKey = TypeDesc.parseAspectCfgKey(k)
            if (aspectKey != null) {
                aspectsConfigs.computeIfAbsent(aspectKey.aspectId) { ObjectData.create() }[aspectKey.configKey] = v
            } else {
                mutAtts[k] = v
            }
        }
        val mutAspects = if (record.attributes.has("customAspects")) {
            val customAspects = record.attributes["customAspects"].asList(TypeAspectDef::class.java)
                .filter { it.ref.getLocalId().isNotEmpty() }
            val newAspects = ArrayList(customAspects)
            recToMutate.aspects.forEach {
                if (TypeDesc.NON_CUSTOM_ASPECTS.contains(it.ref.getLocalId())) {
                    newAspects.add(it)
                }
            }
            applyAspectsData(newAspects, aspectsConfigs)
            newAspects
        } else if (record.attributes.has("aspects")) {
            record.attributes["aspects"].asList(TypeAspectDef::class.java)
        } else {
            recToMutate.aspects
        }

        val isNewRec = record.id.isEmpty()
        if (!isNewRec) {
            mutAtts.remove(WORKSPACE_ATT)
            mutAtts.remove(RecordConstants.ATT_WORKSPACE)
        } else if (
            !mutAtts.has(WORKSPACE_ATT) &&
            mutAtts.has(RecordConstants.ATT_WORKSPACE)
        ) {
            val workspaceId = mutAtts[RecordConstants.ATT_WORKSPACE].asText().toEntityRef().getLocalId()
            if (isWorkspaceShouldHasScopedTypes(workspaceId)) {
                mutAtts[WORKSPACE_ATT] = workspaceId
            }
            mutAtts.remove(RecordConstants.ATT_WORKSPACE)
        }

        val workspace = mutAtts[WORKSPACE_ATT].asText()
        if (mutAtts.has(FORM_REF_ATT)) {
            var formRef = mutAtts[FORM_REF_ATT].asText().toEntityRef()
            formRef = formRef.withLocalId(
                workspaceService?.replaceMaskFromIdToWsPrefix(formRef.getLocalId(), workspace)
                    ?: formRef.getLocalId()
            )
            mutAtts[FORM_REF_ATT] = formRef
        }

        if (mutAtts.has(JOURNAL_REF_ATT)) {
            var journalRef = mutAtts[JOURNAL_REF_ATT].asText().toEntityRef()
            journalRef = journalRef.withLocalId(
                workspaceService?.replaceMaskFromIdToWsPrefix(journalRef.getLocalId(), workspace)
                    ?: journalRef.getLocalId()
            )
            mutAtts[JOURNAL_REF_ATT] = journalRef
        }

        if (mutAtts.has(NUM_TEMPLATE_REF_ATT)) {
            var numTemplateRef = mutAtts[NUM_TEMPLATE_REF_ATT].asText().toEntityRef()
            numTemplateRef = numTemplateRef.withLocalId(
                workspaceService?.replaceMaskFromIdToWsPrefix(numTemplateRef.getLocalId(), workspace)
                    ?: numTemplateRef.getLocalId()
            )
            mutAtts[NUM_TEMPLATE_REF_ATT] = numTemplateRef
        }

        val ctx = BeanTypeUtils.getTypeContext(TypeMutRecord::class.java)
        ctx.applyData(recToMutate, mutAtts)

        recToMutate.aspects = mutAspects

        return saveMutatedRec(recToMutate)
    }

    private fun isWorkspaceShouldHasScopedTypes(workspaceId: String): Boolean {
        return workspaceId != ModelUtils.DEFAULT_WORKSPACE_ID && !workspaceId.startsWith("admin$")
    }

    private fun applyAspectsData(
        newAspects: MutableList<TypeAspectDef>,
        aspectsConfig: Map<String, ObjectData>
    ) {

        if (aspectsConfig.isEmpty()) {
            return
        }
        for ((aspectId, config) in aspectsConfig) {
            val idxOfExistingAspect = newAspects.indexOfFirst { it.ref.getLocalId() == aspectId }
            val added = config.get(TypeDesc.ASPECT_CONFIG_ADDED_FLAG, idxOfExistingAspect > -1)
            config.remove(TypeDesc.ASPECT_CONFIG_ADDED_FLAG)
            if (idxOfExistingAspect == -1) {
                if (added) {
                    newAspects.add(
                        TypeAspectDef.create()
                            .withRef(ModelUtils.getAspectRef(aspectId))
                            .withConfig(config)
                            .build()
                    )
                }
            } else if (!added) {
                newAspects.removeIf { it.ref.getLocalId() == aspectId }
            } else {
                val newAspectCfg = newAspects[idxOfExistingAspect].config.deepCopy()
                config.forEach { k, v -> newAspectCfg[k] = v }
                newAspects[idxOfExistingAspect] = newAspects[idxOfExistingAspect].copy()
                    .withConfig(newAspectCfg)
                    .build()
            }
        }
    }

    private fun saveMutatedRec(record: TypeMutRecord): String {

        val newTypeDef = record.build()

        if (newTypeDef.id.isEmpty()) {
            error("Type identifier is empty")
        }

        typesRepoPermsService?.checkMutationPermissions(record)

        val baseId = record.baseTypeDef.id
        val clonedRecord = baseId.isNotEmpty() && baseId != newTypeDef.id

        val defAfterSave = typeService.save(newTypeDef, clonedRecord)
        return workspaceService?.addWsPrefixToId(defAfterSave.id, defAfterSave.workspace) ?: defAfterSave.id
    }

    override fun delete(recordId: String): DelStatus {
        typesRepoPermsService?.checkDeletePermissions(recordId)
        typeService.delete(workspaceService.convertToIdInWsSafe(recordId))
        return DelStatus.OK
    }

    class TypeMutRecord(val baseTypeDef: TypeDef) : TypeDef.Builder(baseTypeDef) {

        fun isNewRec(): Boolean {
            return this.id != baseTypeDef.id
        }

        fun withLocalId(localId: String) {
            this.withId(localId)
        }

        fun withModuleId(moduleId: String) {
            this.withId(moduleId)
        }

        fun withModelRoles(roles: List<RoleDef>) {
            model.withRoles(roles)
        }

        fun withModelStatuses(statuses: List<StatusDef>) {
            model.withStatuses(statuses)
        }

        fun withModelStages(stages: List<ProcStageDef>) {
            model.withStages(stages)
        }

        fun withModelAttributes(attributes: List<AttributeDef>) {
            model.withAttributes(attributes)
        }

        fun withDocLibEnabled(enabled: Boolean) {
            withDocLib(docLib.copy().withEnabled(enabled).build())
        }

        fun withDocLibFileTypeRefs(fileTypeRefs: List<EntityRef>) {
            withDocLib(docLib.copy().withFileTypeRefs(fileTypeRefs).build())
        }

        fun withDocLibDirTypeRef(typeRef: EntityRef) {
            withDocLib(docLib.copy().withDirTypeRef(typeRef).build())
        }
    }
}
