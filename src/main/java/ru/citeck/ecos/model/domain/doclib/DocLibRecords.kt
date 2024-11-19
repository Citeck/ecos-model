package ru.citeck.ecos.model.domain.doclib

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.*
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.atts.value.impl.InnerAttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateWithAnyResDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry

@Component
class DocLibRecords @Autowired constructor(
    val typesRegistry: EcosTypesRegistry
) : AbstractRecordsDao(), RecordsQueryDao, RecordMutateWithAnyResDao, RecordAttsDao {

    companion object {
        const val ID = "doclib"
        private const val LANG_CHILDREN = "children"
        private const val DIR_SRC_ID = "doclib-directory"
        private const val PARENT_ATT_ALIAS = "__parentRef"
        private const val ATT_NOT_EXISTS = RecordConstants.ATT_NOT_EXISTS + ScalarType.BOOL_SCHEMA
        private const val ATT_PARENT_ID = RecordConstants.ATT_PARENT + ScalarType.ID_SCHEMA
        private const val ATT_CHILDREN = "children"

        private val IN_MEM_ATTS = hashSetOf(
            DocLibRecord.ATT_NODE_TYPE
        )

        private val log = KotlinLogging.logger {}
    }

    override fun queryRecords(recsQuery: RecordsQuery): List<EntityRef>? {
        if (LANG_CHILDREN == recsQuery.language) {
            val childrenQuery = recsQuery.getQuery(DocLibChildrenQuery::class.java)
            return getChildren(
                childrenQuery,
                recsQuery.sortBy,
                recsQuery.page.skipCount,
                recsQuery.page.maxItems,
                recsQuery.workspaces
            )
        }
        return null
    }

    fun getPath(entityRef: EntityRef): List<EntityRef> {

        val path = ArrayList<EntityRef>()
        val entityId = DocLibRecordId.valueOf(entityRef.getLocalId())
        val rootRef = getDocLibRootForType(entityId.typeId)

        var currentEntity = entityRef
        var iterationsLimit = 20
        do {
            var parentRef = recordsService.getAtt(currentEntity, ATT_PARENT_ID)
                .asText()
                .toEntityRef()
            if (parentRef.isEmpty()) {
                parentRef = rootRef
            }
            path.add(parentRef)
            currentEntity = parentRef
        } while (--iterationsLimit > 0 && parentRef != rootRef)

        return path.reversed()
    }

    fun hasChildrenDirs(entityRef: EntityRef, workspaces: List<String>): Boolean {

        val query = DocLibChildrenQuery(
            parentRef = entityRef,
            filter = VoidPredicate.INSTANCE,
            nodeType = DocLibNodeType.DIR
        )
        return getChildren(query, emptyList(), 0, 1, workspaces).isNotEmpty()
    }

    fun getChildren(
        query: DocLibChildrenQuery,
        sortBy: List<SortBy>,
        skipCount: Int,
        maxItems: Int,
        workspaces: List<String>
    ): List<EntityRef> {

        val normalizedMaxItems = if (maxItems < 0) {
            1000
        } else {
            maxItems
        }

        val parentDocLibId = DocLibRecordId.valueOf(query.parentRef?.getLocalId())
        val docLibTypeDef = typesRegistry.getValue(parentDocLibId.typeId) ?: return emptyList()

        var parentIsRoot = false
        val parentLocalRef = if (parentDocLibId.entityRef.isEmpty()) {
            parentIsRoot = true
            EntityRef.create(AppName.EMODEL, DIR_SRC_ID, parentDocLibId.typeId + DocLibRecordId.TYPE_DELIM + "ROOT")
        } else {
            parentDocLibId.entityRef
        }

        val parentOrPredicates = OrPredicate()
        parentOrPredicates.addPredicate(Predicates.eq(RecordConstants.ATT_PARENT, parentLocalRef))

        val filterPredicate = AndPredicate.of(parentOrPredicates)

        val preProcessedFilterPredicate = PredicateUtils.mapValuePredicates(query.filter) {
            if (it.getAttribute() == "ALL") {
                ValuePredicate(
                    "_name",
                    it.getType(),
                    it.getValue()
                )
            } else {
                it
            }
        } ?: VoidPredicate.INSTANCE

        if (preProcessedFilterPredicate !is VoidPredicate) {
            filterPredicate.addPredicate(preProcessedFilterPredicate)
        }

        val normalizedSorting = sortBy.mapNotNull {
            if (it.attribute == DocLibRecord.ATT_NODE_TYPE && query.nodeType != null) {
                null
            } else {
                it
            }
        }

        val isInMemSortRequired = query.nodeType == null || normalizedSorting.any { it.attribute in IN_MEM_ATTS }
        val (innerQuerySkipCount, innerQueryMaxItems) = if (isInMemSortRequired) {
            0 to (skipCount + normalizedMaxItems)
        } else {
            skipCount to normalizedMaxItems
        }

        var recsQuery = RecordsQuery.create {
            withLanguage(PredicateService.LANGUAGE_PREDICATE)
            withQuery(filterPredicate)
            withSortBy(
                normalizedSorting.mapNotNull {
                    when (it.attribute) {
                        DocLibRecord.ATT_NODE_TYPE -> null
                        "?disp" -> SortBy("_name", it.ascending)
                        else -> it
                    }
                }
            )
            withSkipCount(innerQuerySkipCount)
            withMaxItems(innerQueryMaxItems)
            withWorkspaces(workspaces)
        }

        val resultRecordsInnerRefs = ArrayList<EntityRef>()

        if (query.nodeType == null || query.nodeType == DocLibNodeType.DIR) {
            resultRecordsInnerRefs.addAll(recordsService.query(recsQuery.withSourceId(DIR_SRC_ID)).getRecords())
        }
        if (query.nodeType == null || query.nodeType == DocLibNodeType.FILE) {
            if (parentIsRoot) {
                parentOrPredicates.addPredicate(EmptyPredicate(RecordConstants.ATT_PARENT))
            }
            recsQuery = recsQuery.copy {
                withSourceId(docLibTypeDef.sourceId)
                withQuery(filterPredicate)
            }
            resultRecordsInnerRefs.addAll(recordsService.query(recsQuery).getRecords())
        }

        val resultRecords = resultRecordsInnerRefs.map {
            EntityRef.create(ID, parentDocLibId.withEntityRef(it).toString())
        }

        return if (isInMemSortRequired) {
            predicateService.filterAndSort(
                resultRecords,
                VoidPredicate.INSTANCE,
                sortBy,
                skipCount,
                normalizedMaxItems
            )
        } else {
            resultRecords
        }
    }

    override fun getRecordAtts(recordId: String): Any? {

        val dlId = DocLibRecordId.valueOf(recordId)
        if (dlId.entityRef.isEmpty()) {
            val docLibTypeDef = typesRegistry.getValue(dlId.typeId) ?: return null
            return RootRecord(EntityRef.create(AppName.EMODEL, ID, recordId), docLibTypeDef)
        }

        val innerAttsMap = LinkedHashMap(AttContext.getInnerAttsMap())
        innerAttsMap[PARENT_ATT_ALIAS] = RecordConstants.ATT_PARENT + ScalarType.ID_SCHEMA
        val innerAtts = recordsService.getAtts(listOf(dlId.entityRef), innerAttsMap, true).first()

        val parentRefStr = innerAtts["/$PARENT_ATT_ALIAS/?id"].asText()
        val parentRef = if (parentRefStr.isBlank() || parentRefStr.endsWith("\$ROOT")) {
            getDocLibRootForType(dlId.typeId)
        } else {
            EntityRef.create(AppName.EMODEL, ID, dlId.typeId + "$" + parentRefStr)
        }

        val nodeType = if (dlId.entityRef.getSourceId() == DIR_SRC_ID) {
            DocLibNodeType.DIR
        } else {
            DocLibNodeType.FILE
        }

        return DocLibRecord(
            EntityRef.create(ID, recordId),
            InnerAttValue(innerAtts.getAtts()),
            nodeType,
            parentRef,
            this
        )
    }

    override fun mutateForAnyRes(record: LocalRecordAtts): Any? {

        val parentRef = EntityRef.valueOf(record.attributes[RecordConstants.ATT_PARENT].asText())

        val recordId = DocLibRecordId.valueOf(record.id)
        val parentId = DocLibRecordId.valueOf(parentRef.getLocalId())

        if (parentId.typeId != recordId.typeId) {
            error("You can't create record of one doclib type in another. Parent: $parentId Entity: $recordId")
        }
        val attributes = prepareForMutation(record.attributes)

        val newRecordId = if (recordId.entityRef.isEmpty()) {
            recordId.withEntityRef(createEntity(parentId, attributes))
        } else {
            updateEntity(recordId, attributes)
            recordId
        }
        return EntityRef.create(AppName.EMODEL, ID, newRecordId.toString())
    }

    private fun prepareForMutation(data: ObjectData): ObjectData {
        val dataCopy = data.deepCopy()
        val dispAtt = ScalarType.DISP.mirrorAtt
        if (dataCopy.has(dispAtt)) {
            dataCopy["name"] = dataCopy[dispAtt]
            dataCopy.remove(dispAtt)
        }
        return dataCopy
    }

    private fun updateEntity(entityId: DocLibRecordId, attributes: ObjectData): EntityRef {
        if (attributes.has(RecordConstants.ATT_PARENT)) {
            val parentRef = EntityRef.valueOf(attributes[RecordConstants.ATT_PARENT].asText())
            if (parentRef.getLocalId().isEmpty()) {
                error("Empty parent attribute in mutation of record ${entityId.entityRef}")
            }
            val parentId = DocLibRecordId.valueOf(parentRef.getLocalId())
            if (parentId.entityRef == entityId.entityRef) {
                error("Cyclic parent reference: ${parentId.entityRef}")
            }
            attributes[RecordConstants.ATT_PARENT] = parentId.entityRef
            attributes[RecordConstants.ATT_PARENT_ATT] = ATT_CHILDREN
        }
        return recordsService.mutate(EntityRef.valueOf(entityId.entityRef), attributes)
    }

    private fun createEntity(parentId: DocLibRecordId, attributes: ObjectData): EntityRef {

        val docLibType: EntityRef = ModelUtils.getTypeRef(parentId.typeId)
        val docLibTypeAtts = recordsService.getAtts(docLibType, DocLibInfoAtts::class.java)

        var newEntityTypeRef = EntityRef.valueOf(attributes[RecordConstants.ATT_TYPE].asText())
        if (newEntityTypeRef.isEmpty()) {
            newEntityTypeRef = docLibType
        }

        val typeDefForNewEntity = typesRegistry.getValue(newEntityTypeRef.getLocalId())
            ?: error("Type '$newEntityTypeRef' doesn't found")

        val allowedTypes = HashSet(docLibTypeAtts.fileTypeRefs)
        allowedTypes.add(docLibTypeAtts.dirTypeRef)
        if (!allowedTypes.contains(newEntityTypeRef)) {
            error("Invalid type '$newEntityTypeRef'. Allowed types: $allowedTypes")
        }

        if (parentId.entityRef.isEmpty()) {
            val parentRef = getInternalRootForType(parentId.typeId)
            if (recordsService.getAtt(parentRef, ATT_NOT_EXISTS).asBoolean()) {

                log.info { "Create root directory for type '${parentId.typeId}'" }

                val rootAtts = ObjectData.create()
                    .set("id", parentRef.getLocalId())
                    .set("name", "Root directory for '${parentId.typeId}'")
                recordsService.create(DIR_SRC_ID, rootAtts)
            }
            attributes[RecordConstants.ATT_PARENT] = parentRef
        } else {
            attributes[RecordConstants.ATT_PARENT] = parentId.entityRef
        }
        attributes[RecordConstants.ATT_PARENT_ATT] = ATT_CHILDREN

        return recordsService.create(typeDefForNewEntity.sourceId, attributes)
    }

    private fun getDocLibRootForType(typeId: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, ID, "$typeId$")
    }

    private fun getInternalRootForType(typeId: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, DIR_SRC_ID, "$typeId\$ROOT")
    }

    override fun getId(): String {
        return ID
    }

    private class DocLibInfoAtts(
        @AttName("docLibInfo.fileTypeRefs[]?id")
        val fileTypeRefs: List<EntityRef>,
        @AttName("docLibInfo.dirTypeRef?id")
        val dirTypeRef: EntityRef
    )

    class RootRecord(
        private val id: EntityRef,
        private val typeDef: TypeDef
    ) {

        fun getId(): EntityRef {
            return id
        }

        fun getDisplayName(): MLText {
            return typeDef.name
        }

        fun getEcosType(): EntityRef {
            return ModelUtils.getTypeRef(typeDef.id)
        }
    }
}
