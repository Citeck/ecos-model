package ru.citeck.ecos.model.type.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.procstages.dto.ProcStageDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.TypeAspectDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypeDesc
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.value.factory.bean.BeanTypeUtils
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateWithAnyResDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

@Component
class TypesRepoRecordsMutDao(
    private val typeService: TypesService,
    private val typesRepoPermsService: TypesRepoPermsService? = null
) : RecordMutateWithAnyResDao, RecordDeleteDao {

    override fun getId() = "types-repo"

    override fun mutateForAnyRes(record: LocalRecordAtts): Any? {

        val recToMutate = getRecToMutate(record.id)

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
        val customAspects = record.attributes["customAspects"].asList(TypeAspectDef::class.java)
        val newAspects = ArrayList(customAspects)
        recToMutate.aspects.forEach {
            if (TypeDesc.NON_CUSTOM_ASPECTS.contains(it.ref.getLocalId())) {
                newAspects.add(it)
            }
        }
        applyAspectsData(recToMutate, newAspects, aspectsConfigs)

        val ctx = BeanTypeUtils.getTypeContext(TypeMutRecord::class.java)
        ctx.applyData(recToMutate, mutAtts)

        recToMutate.aspects = newAspects

        return saveMutatedRec(recToMutate)
    }

    private fun applyAspectsData(
        record: TypeMutRecord,
        newAspects: MutableList<TypeAspectDef>,
        aspectsConfig: Map<String, ObjectData>
    ) {

        if (aspectsConfig.isEmpty()) {
            return
        }
        for ((aspectId, config) in aspectsConfig) {
            val added = config.get(TypeDesc.ASPECT_CONFIG_ADDED_FLAG, true)
            config.remove(TypeDesc.ASPECT_CONFIG_ADDED_FLAG)
            val idxOfExistingAspect = newAspects.indexOfFirst { it.ref.getLocalId() == aspectId }
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

    private fun getRecToMutate(recordId: String): TypeMutRecord {

        if (recordId.isEmpty()) {
            return TypeMutRecord(TypeDef.EMPTY)
        }
        return TypeMutRecord(typeService.getById(recordId))
    }

    private fun saveMutatedRec(record: TypeMutRecord): String {

        val newTypeDef = record.build()

        if (newTypeDef.id.isEmpty()) {
            error("Type identifier is empty")
        }

        typesRepoPermsService?.checkMutationPermissions(record)

        val baseId = record.baseTypeDef.id
        val clonedRecord = baseId.isNotEmpty() && baseId != newTypeDef.id

        return typeService.save(newTypeDef, clonedRecord).id
    }

    override fun delete(recordId: String): DelStatus {
        typesRepoPermsService?.checkDeletePermissions(recordId)
        typeService.delete(recordId)
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
