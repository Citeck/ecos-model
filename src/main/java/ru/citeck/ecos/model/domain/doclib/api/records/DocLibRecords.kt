package ru.citeck.ecos.model.domain.doclib.api.records

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.lib.type.dto.WorkspaceScope
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
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.ifEmpty
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.time.Duration

@Component
class DocLibRecords @Autowired constructor(
    val typesRegistry: EcosTypesRegistry,
    val webAppApi: EcosWebAppApi
) : AbstractRecordsDao(), RecordsQueryDao, RecordMutateWithAnyResDao, RecordAttsDao {

    companion object {
        const val ID = "doclib"
        private const val LANG_CHILDREN = "children"
        private const val PARENT_ATT_ALIAS = "__parentRef"
        private const val ATT_NOT_EXISTS = RecordConstants.ATT_NOT_EXISTS + ScalarType.BOOL_SCHEMA
        private const val ATT_PARENT_ID = RecordConstants.ATT_PARENT + ScalarType.ID_SCHEMA
        private const val ATT_CHILDREN = "children"
        private const val ATT_CONTENT_TEMPLATE = "_contentTemplate"
        private const val TEMPLATED_CONTENT_SRC_ID = "transformations/templated-content"

        private const val DEFAULT_DIR_TYPE_ID = "doclib-directory"
        private const val DEFAULT_DIR_SRC_ID = "doclib-directory"

        private val IN_MEM_ATTS = hashSetOf(
            DocLibRecord.ATT_NODE_TYPE
        )

        private val log = KotlinLogging.logger {}
    }

    private val dirSrcIdCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(20))
        .maximumSize(100)
        .build<String, DocLibDirInfo> { typeId ->
            val typeDef = typesRegistry.getValue(typeId)
            val dirTypeRef = typeDef?.aspects
                ?.find { it.ref.getLocalId() == "doclib" }
                ?.config
                ?.get("dirTypeRef")
                ?.asText()
                .toEntityRef()
                .ifEmpty { ModelUtils.getTypeRef(DEFAULT_DIR_TYPE_ID) }

            val dirTypeDef = typesRegistry.getValue(dirTypeRef.getLocalId())
            val srcId = dirTypeDef?.sourceId ?: DEFAULT_DIR_SRC_ID
            DocLibDirInfo(
                typeId = dirTypeRef.getLocalId(),
                sourceId = srcId,
                privateWorkspaceScope = dirTypeDef?.workspaceScope == WorkspaceScope.PRIVATE
            )
        }

    @PostConstruct
    fun init() {
        webAppApi.doWhenAppReady {
            typesRegistry.listenEvents { typeId, before, after ->
                dirSrcIdCache.invalidate(typeId)
                if (before?.sourceId != after?.sourceId && typesRegistry.isSubType(typeId, DEFAULT_DIR_TYPE_ID)) {
                    dirSrcIdCache.invalidateAll()
                }
            }
        }
    }

    override fun queryRecords(recsQuery: RecordsQuery): List<EntityRef>? {
        if (LANG_CHILDREN == recsQuery.language) {
            val childrenQuery = recsQuery.getQuery(DocLibChildrenQuery::class.java)
            return getChildren(
                childrenQuery,
                recsQuery.sortBy,
                recsQuery.page.skipCount,
                recsQuery.page.maxItems,
                recsQuery.workspaces.firstOrNull() ?: ""
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

    fun hasChildrenDirs(entityRef: EntityRef, workspace: String): Boolean {

        val query = DocLibChildrenQuery(
            parentRef = entityRef,
            filter = VoidPredicate.INSTANCE,
            nodeType = DocLibNodeType.DIR
        )
        return getChildren(query, emptyList(), 0, 1, workspace).isNotEmpty()
    }

    fun getChildren(
        query: DocLibChildrenQuery,
        sortBy: List<SortBy>,
        skipCount: Int,
        maxItems: Int,
        workspace: String
    ): List<EntityRef> {

        val normalizedMaxItems = if (maxItems < 0) {
            1000
        } else {
            maxItems
        }

        val parentDocLibId = DocLibRecordId.valueOf(query.parentRef?.getLocalId())
        val docLibTypeDef = typesRegistry.getValue(parentDocLibId.typeId) ?: return emptyList()
        val dirInfo = dirSrcIdCache[parentDocLibId.typeId]

        var parentIsRoot = false

        val filterPredicate = AndPredicate()

        val parentLocalRef = if (parentDocLibId.entityRef.isEmpty()) {
            parentIsRoot = true
            getInternalRootForType(parentDocLibId.typeId, dirInfo, workspace)
        } else {
            parentDocLibId.entityRef
        }

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

            val dirsFilter = filterPredicate.copy<AndPredicate>()
            if (query.recursive) {
                dirsFilter.addPredicate(Predicates.contains("dirPath", parentLocalRef.toString()))
            } else {
                dirsFilter.addPredicate(Predicates.eq(RecordConstants.ATT_PARENT, parentLocalRef))
            }

            val dirsQuery = recsQuery.copy()
                .withSourceId(dirInfo.sourceId)
                .withEcosType(dirInfo.typeId)
                .withQuery(dirsFilter)
                .build()

            resultRecordsInnerRefs.addAll(recordsService.query(dirsQuery).getRecords())
        }
        if (query.nodeType == null || query.nodeType == DocLibNodeType.FILE) {

            val filesFilter = filterPredicate.copy<AndPredicate>()

            val parentCondition = OrPredicate.of(
                Predicates.eq(RecordConstants.ATT_PARENT, parentLocalRef),
                EmptyPredicate(RecordConstants.ATT_PARENT)
            )
            if (query.recursive) {
                parentCondition.addPredicate(
                    Predicates.contains("${RecordConstants.ATT_PARENT}.dirPath", parentLocalRef.toString())
                )
                filesFilter.addPredicate(
                    Predicates.eq(
                        "${RecordConstants.ATT_PARENT}._type",
                        ModelUtils.getTypeRef(dirInfo.typeId)
                    )
                )
            }
            filesFilter.addPredicate(parentCondition)

            recsQuery = recsQuery.copy {
                withSourceId(docLibTypeDef.sourceId)
                withQuery(filesFilter)
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
        val parentRef = if (parentRefStr.isBlank() ||
            parentRefStr.endsWith("\$ROOT") ||
            parentRefStr.contains("\$ROOT\$")
        ) {
            getDocLibRootForType(dlId.typeId)
        } else {
            EntityRef.create(AppName.EMODEL, ID, dlId.typeId + "$" + parentRefStr)
        }

        val dirSourceIdRef = EntityRef.create(dirSrcIdCache[dlId.typeId].sourceId, "")
        val entitySourceIdRef = dlId.entityRef.withLocalId("")
        val nodeType = if (entitySourceIdRef == dirSourceIdRef) {
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

        val contentTemplate = attributes[ATT_CONTENT_TEMPLATE].asText()
        attributes.remove(ATT_CONTENT_TEMPLATE)

        val newRecordId = if (recordId.entityRef.isEmpty()) {
            val entityRef = createEntity(parentId, attributes)
            if (contentTemplate.isNotBlank()) {
                val templateRef = EntityRef.create(AppName.TRANSFORMATIONS, "template", contentTemplate)
                recordsService.mutate(
                    "$TEMPLATED_CONTENT_SRC_ID@",
                    mapOf(
                        "record" to entityRef,
                        "template" to templateRef,
                        "attribute" to RecordConstants.ATT_CONTENT
                    )
                )
            }
            recordId.withEntityRef(entityRef)
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
            val dirInfo = dirSrcIdCache[parentId.typeId]
            var workspace = ""
            if (dirInfo.privateWorkspaceScope) {
                workspace = attributes[DocLibRecord.ATT_WORKSPACE].asText()
                if (workspace.isBlank()) {
                    error("${DocLibRecord.ATT_WORKSPACE} att is missing")
                }
            }
            val parentRef = getInternalRootForType(parentId.typeId, dirInfo, workspace)
            if (recordsService.getAtt(parentRef, ATT_NOT_EXISTS).asBoolean()) {

                log.info { "Create root directory for type '${parentId.typeId}' in workspace '$workspace'" }

                val rootAtts = ObjectData.create()
                    .set("id", parentRef.getLocalId())
                    .set("name", "Root directory for '${parentId.typeId}'")
                    .set(DocLibRecord.ATT_WORKSPACE, workspace)
                recordsService.create(dirInfo.sourceId, rootAtts)
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

    private fun getInternalRootForType(typeId: String, dirInfo: DocLibDirInfo, workspace: String): EntityRef {
        var id = "$typeId\$ROOT"
        if (dirInfo.privateWorkspaceScope &&
            workspace.isNotBlank() &&
            workspace != WorkspaceDesc.DEFAULT_WORKSPACE_ID
        ) {
            id += '$' + workspace.trim()
        }
        return EntityRef.create(dirInfo.sourceId, id).withDefaultAppName(AppName.EMODEL)
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

    private class DocLibDirInfo(
        val typeId: String,
        val sourceId: String,
        val privateWorkspaceScope: Boolean
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
