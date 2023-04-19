package ru.citeck.ecos.model.type.api.records

import ecos.com.fasterxml.jackson210.databind.JsonNode
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.events2.type.RecordEventsService
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.nio.charset.StandardCharsets

@Component
class TypesRepoRecordsDao(
    private val typeService: TypesService,
    private val recordEventsService: RecordEventsService? = null
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {
        const val ID = "types-repo"
    }

    init {
        typeService.addListener { before, after ->
            if (after != null) {
                onTypeDefChanged(before, after)
            }
        }
    }

    override fun getId() = ID

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<TypeRecord> {

        val result = RecsQueryRes<TypeRecord>()

        when (recsQuery.language) {

            PredicateService.LANGUAGE_PREDICATE -> {

                val predicate = recsQuery.getQuery(Predicate::class.java)

                val types = typeService.getAll(
                    recsQuery.page.maxItems,
                    recsQuery.page.skipCount,
                    predicate,
                    recsQuery.sortBy
                )

                result.setRecords(types.map { TypeRecord(it, typeService) })
                result.setTotalCount(typeService.getCount(predicate))
            }
            else -> {

                val max: Int = recsQuery.page.maxItems
                val types = if (max < 0) {
                    typeService.getAll()
                } else {
                    typeService.getAll(max, recsQuery.page.skipCount)
                }
                result.setRecords(types.map { TypeRecord(it, typeService) })
            }
        }

        return result
    }

    override fun getRecordAtts(recordId: String): TypeRecord? {
        return typeService.getByIdOrNull(recordId)?.let { TypeRecord(it, typeService) }
    }

    private fun onTypeDefChanged(before: TypeDef?, after: TypeDef) {
        recordEventsService?.emitRecChanged(before, after, getId()) {
            TypeRecord(it, typeService)
        }
    }

    class TypeRecord(
        @AttName("...")
        val typeDef: TypeDef,
        val typeService: TypesService
    ) {

        fun getFormRef(): EntityRef {
            if (typeDef.id.isNotBlank() && typeDef.formRef.getLocalId() == TypeDefResolver.DEFAULT_FORM) {
                return typeDef.formRef.withLocalId("type$" + typeDef.id)
            }
            return typeDef.formRef
        }

        fun getJournalRef(): EntityRef {
            if (typeDef.id.isNotBlank() && typeDef.journalRef.getLocalId() == TypeDefResolver.DEFAULT_JOURNAL) {
                return typeDef.journalRef.withLocalId("type$" + typeDef.id)
            }
            return typeDef.journalRef
        }

        fun getData(): ByteArray {
            return YamlUtils.toNonDefaultString(typeDef).toByteArray(StandardCharsets.UTF_8)
        }

        @AttName("?json")
        fun getJson(): JsonNode {
            return mapper.toNonDefaultJson(typeDef)
        }

        @AttName("?disp")
        fun getDisplayName(): Any {
            if (!MLText.isEmpty(typeDef.name)) {
                return typeDef.name
            }
            return typeDef.id
        }

        @AttName("_type")
        fun getEcosType(): RecordRef {
            return ModelUtils.getTypeRef("type")
        }
    }
}
