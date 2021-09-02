package ru.citeck.ecos.model.type.api.records

import ecos.com.fasterxml.jackson210.databind.JsonNode
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import java.nio.charset.StandardCharsets

@Component
class TypeRecordsDao(
    val typeService: TypeService
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {
        const val ID = "type"

        private const val LANG_EXPAND_TYPES = "expand-types";
    }

    override fun getId() = ID

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<TypeRecord> {

        val result = RecsQueryRes<TypeRecord>()

        when (recsQuery.language) {

            LANG_EXPAND_TYPES -> {

                val expandTypesQuery: ExpandTypesQuery = recsQuery.getQuery(ExpandTypesQuery::class.java)
                val resultTypesDto = typeService.expandTypes(expandTypesQuery.typeRefs.map { it.id })

                result.setRecords(resultTypesDto.map { TypeRecord(it, typeService) })
            }
            PredicateService.LANGUAGE_PREDICATE -> {

                val predicate = recsQuery.getQuery(Predicate::class.java)

                var max: Int = recsQuery.page.maxItems
                if (max <= 0) {
                    max = 10000
                }
                val order: List<Sort.Order> = recsQuery.sortBy
                    .mapNotNull { sortBy ->
                        var attribute = sortBy.attribute
                        attribute = if (RecordConstants.ATT_MODIFIED == attribute) {
                            "lastModifiedDate"
                        } else {
                            ""
                        }
                        if (attribute.isNotBlank()) {
                            if (sortBy.ascending) {
                                Sort.Order.asc(attribute)
                            } else {
                                Sort.Order.desc(attribute)
                            }
                        } else {
                            null
                        }
                    }

                val types = typeService.getAll(
                    max,
                    recsQuery.page.skipCount,
                    predicate,
                    if (order.isNotEmpty()) {
                        Sort.by(order)
                    } else {
                        null
                    }
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

    class TypeRecord(
        @AttName("...")
        val typeDef: TypeDef,
        val typeService: TypeService
    ) {

        fun getParents(): List<RecordRef> {
            return typeService.getParentIds(typeDef.id).map { TypeUtils.getTypeRef(it) }
        }

        fun getChildren(): List<RecordRef> {
            return typeService.getChildren(typeDef.id).map { TypeUtils.getTypeRef(it) }
        }

        fun getData(): ByteArray {
            return YamlUtils.toNonDefaultString(typeDef).toByteArray(StandardCharsets.UTF_8)
        }

        @AttName("?json")
        fun getJson(): JsonNode {
            return mapper.toNonDefaultJson(typeDef)
        }

        @AttName("?disp")
        fun getDisplayName(): MLText {
            return typeDef.name
        }

        @AttName("_type")
        fun getEcosType() : RecordRef {
            return TypeUtils.getTypeRef("type")
        }
    }

    class ExpandTypesQuery(
        val typeRefs: List<RecordRef> = emptyList()
    )
}
