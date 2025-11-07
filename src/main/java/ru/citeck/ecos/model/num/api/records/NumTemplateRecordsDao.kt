package ru.citeck.ecos.model.num.api.records

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils.toNonDefaultString
import ru.citeck.ecos.commons.utils.TmplUtils.getAtts
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.type.RecordEventsService
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.num.dto.NumTemplateDto
import ru.citeck.ecos.model.num.dto.NumTemplateWithMetaDto
import ru.citeck.ecos.model.num.service.NumTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.AndPredicate
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.factory.bean.BeanTypeUtils
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.nio.charset.StandardCharsets

@Component
class NumTemplateRecordsDao(
    private val numTemplateService: NumTemplateService,
    private val recordEventsService: RecordEventsService,
    private val workspaceService: WorkspaceService
) : AbstractRecordsDao(),
    RecordAttsDao,
    RecordsQueryDao,
    RecordsDeleteDao,
    RecordMutateDao {

    companion object {
        private const val ID = "num-template"
    }

    init {
        numTemplateService.addListener { before, after ->
            this.onTemplateChanged(before, after)
        }
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val result = RecsQueryRes<NumTemplateRecord>()

        if ("predicate" == recsQuery.language) {
            var predicate = recsQuery.getQuery(
                Predicate::class.java
            )
            if (recsQuery.workspaces.isNotEmpty()) {
                val newPredicate = AndPredicate()
                newPredicate.addPredicate(predicate)
                newPredicate.addPredicate(
                    Predicates.inVals(
                        "workspace",
                        recsQuery.workspaces.map {
                            if (workspaceService.isWorkspaceWithGlobalEntities(it)) "" else it
                        }
                    )
                )
                predicate = newPredicate
            }

            val types = numTemplateService.getAll(
                recsQuery.page.maxItems,
                recsQuery.page.skipCount,
                predicate,
                recsQuery.sortBy
            )

            result.setRecords(types.map { NumTemplateRecord(it) })
            result.setTotalCount(numTemplateService.getCount(predicate))
            return result
        }

        if ("criteria" == recsQuery.language) {
            result.setRecords(
                numTemplateService.getAll(
                    recsQuery.page.maxItems,
                    recsQuery.page.skipCount
                ).map { NumTemplateRecord(it) }
            )

            result.setTotalCount(numTemplateService.getCount())

            return result
        }

        return RecsQueryRes<Any>()
    }

    override fun mutate(record: LocalRecordAtts): String {
        val dto = getRecordAtts(record.id)

        val dtoCtx = BeanTypeUtils.getTypeContext(dto::class.java)
        dtoCtx.applyData(dto, record.attributes)

        require(dto.id.isNotBlank()) { "Attribute 'id' is mandatory" }

        checkMutPerms(dto)

        val resEntity = numTemplateService.save(dto).entity
        return workspaceService.addWsPrefixToId(resEntity.id, resEntity.workspace)
    }

    private fun checkMutPerms(record: NumTemplateRecord) {
        if (AuthContext.isRunAsSystemOrAdmin()) {
            return
        }
        if (record.workspace.isNotBlank()) {
            if (workspaceService.isUserManagerOf(AuthContext.getCurrentUser(), record.workspace)) {
                return
            } else {
                error("Permission denied. You can't create number templates in workspace '${record.workspace}'")
            }
        } else {
            error("Permission denied. You can't create number templates in global scope.")
        }
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        val results: MutableList<DelStatus> = ArrayList()

        for (recordId in recordIds) {
            numTemplateService.delete(workspaceService.convertToIdInWs(recordId))
            results.add(DelStatus.OK)
        }
        return results
    }

    override fun getRecordAtts(recordId: String): NumTemplateRecord {
        val idInWs = workspaceService.convertToIdInWs(recordId)
        val dto = numTemplateService.getByIdOrNull(idInWs)?.let {
            NumTemplateWithMetaDto(it)
        } ?: NumTemplateWithMetaDto(recordId)

        return NumTemplateRecord(dto)
    }

    private fun onTemplateChanged(
        before: EntityWithMeta<NumTemplateDef>?,
        after: EntityWithMeta<NumTemplateDef>?
    ) {
        if (after != null) {
            recordEventsService.emitRecChanged(
                before,
                after,
                getId()
            ) { dto -> NumTemplateRecord(NumTemplateWithMetaDto(dto)) }
        }
    }

    override fun getId(): String {
        return ID
    }

    inner class NumTemplateRecord : NumTemplateWithMetaDto {

        private var modelAttributes: List<String>

        private val originalId = this.id

        constructor(model: NumTemplateWithMetaDto) : super(model) {
            modelAttributes = ArrayList(getAtts(this.counterKey))
        }

        constructor(model: EntityWithMeta<NumTemplateDef>) : super(model) {
            modelAttributes = model.entity.modelAttributes
        }

        fun getModelAttributes(): List<String> {
            return modelAttributes
        }

        @JsonProperty(RecordConstants.ATT_WORKSPACE)
        fun setCtxWorkspace(workspace: String?) {
            if (originalId != this.id) {
                this.workspace = workspace ?: ""
            } else {
                this.workspace = workspaceService.getUpdatedWsInMutation(this.workspace, workspace)
            }
        }

        @AttName("?id")
        fun getRef(): EntityRef {
            return EntityRef.create(
                AppName.EMODEL,
                ID,
                workspaceService.convertToStrId(IdInWs.create(workspace, id))
            )
        }

        @AttName("_type")
        fun getEcosType(): EntityRef {
            return EntityRef.create(AppName.EMODEL, "type", "number-template")
        }

        @AttName("?disp")
        fun getDispName(): String {
            return name.ifBlank { id }
        }

        @JsonProperty("_content")
        fun setContent(content: List<ObjectData>) {
            val dataUriContent: String = content.first().get("url", "")
            val data = mapper.read(dataUriContent, ObjectData::class.java)
            mapper.applyData(this, data)
        }

        @JsonValue
        fun toJson(): NumTemplateDto {
            val dto = NumTemplateDto(this)
            dto.workspace = ""
            return dto
        }

        fun getData(): ByteArray {
            return toNonDefaultString(toJson()).toByteArray(StandardCharsets.UTF_8)
        }
    }
}
