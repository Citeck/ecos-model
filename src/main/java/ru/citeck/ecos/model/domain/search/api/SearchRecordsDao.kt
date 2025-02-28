package ru.citeck.ecos.model.domain.search.api

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.ctx.EcosContext
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.OrPredicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.Consistency
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class SearchRecordsDao(
    private val recordsService: RecordsService,
    private val typesRegistry: EcosTypesRegistry,
    private val ecosContext: EcosContext,
    private val workspaceService: EmodelWorkspaceService,
    modelServiceFactory: ModelServiceFactory
) : RecordsQueryDao {

    companion object {
        const val ID = "search"

        private const val DISP_NAME_ALIAS = "dispName"
        private const val ECOS_TYPE_ALIAS = "ecosType"
        private const val CREATED_ALIAS = "created"
        private const val MODIFIED_ALIAS = "modified"

        private const val GROUP_TYPE_PEOPLE = "PEOPLE"
        private const val GROUP_TYPE_DOCUMENTS = "DOCUMENTS"
        private const val GROUP_TYPE_TASKS = "TASKS"
        private const val GROUP_TYPE_WORKSPACES = "WORKSPACES"

        private const val MAX_ITEMS_FOR_TYPE = 50

        private val BASE_ATTS_TO_REQUEST = mapOf(
            DISP_NAME_ALIAS to ScalarType.DISP.schema,
            ECOS_TYPE_ALIAS to RecordConstants.ATT_TYPE + ScalarType.ID.schema,
            CREATED_ALIAS to RecordConstants.ATT_CREATED,
            MODIFIED_ALIAS to RecordConstants.ATT_MODIFIED
        )

        private val log = KotlinLogging.logger {}
    }

    private val computedAttsService = modelServiceFactory.computedAttsService

    private var globalSearchConfig: GlobalSearchConfig = GlobalSearchConfig()
    private val activeRequestsByAppSemaphore = ConcurrentHashMap<String, Semaphore>()
    private val activeRequestsByAppLock = ReentrantLock()

    private fun readQuery(recsQuery: RecordsQuery): SearchQuery? {
        val query: SearchQuery?
        if (recsQuery.language == PredicateService.LANGUAGE_PREDICATE) {
            var text = ""
            var types = listOf(GROUP_TYPE_DOCUMENTS)
            PredicateUtils.mapValuePredicates(recsQuery.getPredicate(), { p ->
                if (p.getAttribute() == "ALL") {
                    text = p.getValue().asText()
                }
                if (p.getAttribute() == "types") {
                    types = p.getValue().toStrList()
                }
                p
            }, onlyAnd = true, optimize = false, filterEmptyComposite = true)
            query = SearchQuery(text, types, recsQuery.page.maxItems)
        } else {
            query = recsQuery.getQueryOrNull(SearchQuery::class.java)
        }
        return if (query?.text.isNullOrBlank()) null else query
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val query = readQuery(recsQuery)?: return null

        val maxItems = recsQuery.page.maxItems
        val maxItemsForType = query.maxItemsForType.coerceAtMost(MAX_ITEMS_FOR_TYPE)

        val records = ArrayList<SearchRecord>()
        var totalCount = 0L
        fun addResult(searchRes: SearchRes) {
            records.addAll(searchRes.records)
            totalCount += searchRes.totalCount
        }

        val textToSearch = query.text.trim()

        for (type in query.types) {
            when (type) {
                GROUP_TYPE_PEOPLE -> addResult(queryPersons(textToSearch, maxItemsForType))
                GROUP_TYPE_DOCUMENTS -> addResult(queryDocuments(textToSearch, maxItemsForType))
                GROUP_TYPE_TASKS -> addResult(queryTasks(textToSearch, maxItemsForType))
                GROUP_TYPE_WORKSPACES -> addResult(queryWorkspaces(textToSearch, maxItemsForType))
            }
            if (maxItems >= 0 && records.size >= maxItems) {
                break
            }
        }

        val resultRecords = if (maxItems >= 0 && records.size > maxItems) {
            records.subList(0, maxItems - 1)
        } else {
            records
        }

        val queryRes = RecsQueryRes(resultRecords)
        queryRes.setTotalCount(totalCount)
        return queryRes
    }

    private fun queryWorkspaces(text: String, maxItems: Int): SearchRes {

        val userWorkspaces = workspaceService.getUserWorkspaces(
            AuthContext.getCurrentUser(),
            Predicates.or(
                Predicates.contains(WorkspaceDesc.ATT_NAME, text),
                Predicates.contains(WorkspaceDesc.ATT_DESCRIPTION, text)
            ),
            includePersonal = false,
            maxItems = maxItems
        )

        val workspaces = userWorkspaces.workspaces.map { WorkspaceDesc.getRef(it) }
        if (workspaces.isEmpty()) {
            return SearchRes.EMPTY
        }

        return SearchRes(
            recordsService.getAtts(workspaces, BASE_ATTS_TO_REQUEST).map {
                createSearchRecord(it, GROUP_TYPE_WORKSPACES)
            },
            userWorkspaces.totalCount
        )
    }

    private fun queryTasks(text: String, maxItems: Int): SearchRes {

        val query = RecordsQuery.create()
            .withSourceId("alfresco/")
            .withQuery(
                Predicates.and(
                    Predicates.eq("TYPE", "bpm:task"),
                    Predicates.eq("_actors", "\$CURRENT"),
                    Predicates.empty("bpm:completionDate"),
                    Predicates.not(
                        Predicates.eq("samwf:processingStatus", "FULLY_PROCESSED")
                    ),
                    Predicates.contains("cm:title", text)
                )
            )
            .withMaxItems(maxItems)
            .withConsistency(Consistency.EVENTUAL)
            .build()

        val taskIdAlias = "taskId"
        val tasks = recordsService.query(
            query,
            mapOf(
                taskIdAlias to "cm:name"
            )
        )
        val taskRecords = tasks.getRecords().mapNotNull {
            val taskId = it.getAtt(taskIdAlias).asText()
            if (taskId.isBlank()) {
                null
            } else {
                EntityRef.create("eproc", "wftask", taskId)
            }
        }
        val tasksAtts = recordsService.getAtts(taskRecords, BASE_ATTS_TO_REQUEST)

        return SearchRes(
            tasksAtts.map { createSearchRecord(it, GROUP_TYPE_TASKS) },
            tasks.getTotalCount()
        )
    }

    private fun queryDocuments(text: String, maxItems: Int): SearchRes {

        val docTypesToSearch = globalSearchConfig.documentTypesToSearch
        if (docTypesToSearch.isEmpty()) {
            return SearchRes.EMPTY
        }

        val scopeData = ecosContext.getScopeData()
        val searchPromises: List<Promise<SearchRes>> = docTypesToSearch.map { typeToSearch ->
            doInExecutor(Executors.newVirtualThreadPerTaskExecutor()) {
                ecosContext.newScope(scopeData).use {
                    queryDocumentsForType(typeToSearch, text, maxItems)
                }
            }
        }

        val waitUntil = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < waitUntil) {
            if (searchPromises.all { it.isDone() }) {
                break
            }
            Thread.sleep(500)
        }
        val results = ArrayList<SearchRecord>(maxItems)
        var totalCount = 0L
        fun addResults(searchRes: SearchRes) {
            results.addAll(searchRes.records)
            totalCount += searchRes.totalCount
        }

        for ((idx, promise) in searchPromises.withIndex()) {
            if (promise.isDone()) {
                promise.catch {
                    val type = docTypesToSearch[idx]
                    log.error(it) {
                        "Exception occurred while query records of type '${type.typeRef.getLocalId()}'"
                    }
                    SearchRes.EMPTY
                }.get().let {
                    addResults(it)
                }
            } else {
                val type = docTypesToSearch[idx]
                log.warn { "Cancel long search query for type '${type.typeRef.getLocalId()}'" }
                promise.cancel(true)
            }
        }
        results.sortByDescending { it.getCreated() }
        return SearchRes(results.take(maxItems), totalCount)
    }

    private fun queryDocumentsForType(
        typeToSearch: GlobalSearchConfig.DocumentTypeToSearch,
        text: String,
        maxItems: Int
    ): SearchRes {

        if (typeToSearch.typeRef.isEmpty()) {
            return SearchRes.EMPTY
        }
        val typeInfo = typesRegistry.getValue(typeToSearch.typeRef.getLocalId()) ?: return SearchRes.EMPTY

        val appNameDelimIdx = typeInfo.sourceId.indexOf(EntityRef.APP_NAME_DELIMITER)
        val appName = if (appNameDelimIdx != -1) {
            typeInfo.sourceId.substring(appNameDelimIdx + 1)
        } else {
            AppName.EMODEL
        }
        val semaphore = activeRequestsByAppLock.withLock {
            if (activeRequestsByAppSemaphore.size > 1000) {
                // protection against memory leak
                log.warn { "Found too many semaphores: ${activeRequestsByAppSemaphore.size}. Start cleaning" }
                val it = activeRequestsByAppSemaphore.entries.iterator()
                while (it.hasNext()) {
                    val next = it.next()
                    if (next.key != appName
                        && next.value.availablePermits() == globalSearchConfig.maxConcurrentRequestsPerApp
                        && !next.value.hasQueuedThreads()
                    ) { it.remove() }
                }
                log.warn { "Cleaning completed. Semaphores after cleaning: ${activeRequestsByAppSemaphore.size}" }
            }
            activeRequestsByAppSemaphore.computeIfAbsent(appName) {
                Semaphore(globalSearchConfig.maxConcurrentRequestsPerApp)
            }
        }

        val optionsAttsById = typeInfo.model.getAllAttributes()
            .asSequence()
            .filter { it.type == AttributeType.OPTIONS && typeToSearch.attsToSearch.contains(it.id) }
            .associate { it.id to computedAttsService.getAttOptions(EntityRef.EMPTY, it.config) }
        val attsToSearch = typeToSearch.attsToSearch.ifEmpty { listOf("_name") }

        val predicate = OrPredicate()
        for (att in attsToSearch) {
            val variants = optionsAttsById[att]
            if (!variants.isNullOrEmpty()) {
                variants.forEach { variant ->
                    if (variant.label.getValues().values.any { it.contains(text, true) }) {
                        predicate.addPredicate(Predicates.contains(att, variant.value))
                    }
                }
            } else {
                predicate.addPredicate(Predicates.contains(att, text))
            }
        }

        semaphore.acquire()
        val records = try {
            recordsService.query(
                RecordsQuery.create()
                    .withEcosType(typeToSearch.typeRef.getLocalId())
                    .withSourceId(typeInfo.sourceId)
                    .withQuery(predicate)
                    .withMaxItems(maxItems)
                    .withSortBy(SortBy(RecordConstants.ATT_CREATED, false))
                    .build(),
                BASE_ATTS_TO_REQUEST
            )
        } finally {
            semaphore.release()
        }

        val resRecs = records.getRecords().map {
            SearchRecord(
                it.getId(),
                it[DISP_NAME_ALIAS].asText().ifBlank { it.getId().getLocalId() },
                GROUP_TYPE_DOCUMENTS,
                typeToSearch.typeRef,
                it[MODIFIED_ALIAS].getAsInstantOrEpoch(),
                it[CREATED_ALIAS].getAsInstantOrEpoch()
            )
        }
        return SearchRes(resRecs, records.getTotalCount())
    }

    private fun queryPersons(text: String, maxItems: Int): SearchRes {
        val parts = text.split(" ").map { it.trim() }
        val predicate = if (parts.isEmpty()) {
            Predicates.alwaysFalse()
        } else if (parts.size == 1) {
            Predicates.or(
                Predicates.contains("id", parts[0]),
                Predicates.contains(PersonConstants.ATT_FIRST_NAME, parts[0]),
                Predicates.contains(PersonConstants.ATT_LAST_NAME, parts[0]),
                Predicates.contains(PersonConstants.ATT_MIDDLE_NAME, parts[0]),
                Predicates.contains(PersonConstants.ATT_EMAIL, parts[0])
            )
        } else if (parts.size == 2) {
            Predicates.or(
                Predicates.and(
                    Predicates.contains(PersonConstants.ATT_FIRST_NAME, parts[0]),
                    Predicates.contains(PersonConstants.ATT_LAST_NAME, parts[1])
                ),
                Predicates.and(
                    Predicates.contains(PersonConstants.ATT_LAST_NAME, parts[0]),
                    Predicates.contains(PersonConstants.ATT_FIRST_NAME, parts[1])
                ),
                Predicates.and(
                    Predicates.contains(PersonConstants.ATT_FIRST_NAME, parts[0]),
                    Predicates.contains(PersonConstants.ATT_MIDDLE_NAME, parts[1])
                )
            )
        } else if (parts.size == 3) {
            Predicates.and(
                Predicates.contains(PersonConstants.ATT_LAST_NAME, parts[0]),
                Predicates.contains(PersonConstants.ATT_FIRST_NAME, parts[1]),
                Predicates.contains(PersonConstants.ATT_MIDDLE_NAME, parts[2])
            )
        } else {
            Predicates.alwaysFalse()
        }
        if (PredicateUtils.isAlwaysFalse(predicate)) {
            return SearchRes.EMPTY
        }
        return queryImpl(
            sourceId = AuthorityType.PERSON.sourceId,
            language = PredicateService.LANGUAGE_PREDICATE,
            query = predicate,
            maxItems,
            GROUP_TYPE_PEOPLE
        )
    }

    private fun queryImpl(
        sourceId: String,
        language: String,
        query: Any,
        maxItems: Int,
        groupType: String
    ): SearchRes {

        val recsQuery = RecordsQuery.create()
            .withQuery(query)
            .withConsistency(Consistency.EVENTUAL)
            .withLanguage(language)
            .withMaxItems(maxItems)
            .withSourceId(sourceId)
            .build()

        val results = recordsService.query(recsQuery, BASE_ATTS_TO_REQUEST)

        return SearchRes(
            results.getRecords().map { createSearchRecord(it, groupType) },
            results.getTotalCount()
        )
    }

    private fun createSearchRecord(rec: RecordAtts, groupType: String): SearchRecord {
        return SearchRecord(
            rec.getId(),
            rec.getAtt(DISP_NAME_ALIAS).asText(),
            groupType,
            EntityRef.valueOf(rec.getAtt(ECOS_TYPE_ALIAS).asText()),
            getInstant(rec, MODIFIED_ALIAS),
            getInstant(rec, CREATED_ALIAS)
        )
    }

    private fun getInstant(atts: RecordAtts, name: String): Instant {
        val result = atts.getAtt(name)
        if (result.isNull() || (result.isTextual() && result.asText().isBlank())) {
            return Instant.EPOCH
        }
        return result.getAs(Instant::class.java) ?: Instant.EPOCH
    }

    override fun getId(): String {
        return ID
    }

    @EcosConfig("global-search-config")
    fun setGlobalSearchConfig(newConfig: GlobalSearchConfig) {
        if (this.globalSearchConfig.maxConcurrentRequestsPerApp != newConfig.maxConcurrentRequestsPerApp) {
            activeRequestsByAppSemaphore.clear()
        }
        this.globalSearchConfig = newConfig
    }

    private fun <T> doInExecutor(executor: ExecutorService, task: Function0<T>): Promise<T> {
        val future = CompletableFutureBride<T>()
        future.future = executor.submit {
            try {
                future.complete(task.invoke())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return Promises.create(future)
    }

    private class CompletableFutureBride<T> : CompletableFuture<T>() {

        lateinit var future: Future<*>

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            val result = future.cancel(mayInterruptIfRunning)
            super.cancel(mayInterruptIfRunning)
            return result
        }
    }

    class SearchQuery(
        val text: String,
        val types: List<String> = emptyList(),
        val maxItemsForType: Int = 5
    )

    class SearchRecord(
        val rec: EntityRef,
        private val displayName: String,
        val groupType: String,
        private val ecosType: EntityRef,
        private val modified: Instant,
        private val created: Instant
    ) {
        fun getId(): EntityRef {
            return rec
        }

        fun getDisplayName(): String {
            return displayName
        }

        fun getEcosType(): EntityRef {
            return ecosType
        }

        @AttName(RecordConstants.ATT_CREATED)
        fun getCreated(): Instant {
            return created
        }

        @AttName(RecordConstants.ATT_MODIFIED)
        fun getModified(): Instant {
            return modified
        }
    }

    class GlobalSearchConfig(
        val maxConcurrentRequestsPerApp: Int = 10,
        val documentTypesToSearch: List<DocumentTypeToSearch> = emptyList()
    ) {
        class DocumentTypeToSearch(
            val typeRef: EntityRef = EntityRef.EMPTY,
            val attsToSearch: List<String> = emptyList()
        )
    }

    private class SearchRes(
        val records: List<SearchRecord>,
        val totalCount: Long
    ) {
        companion object {
            val EMPTY = SearchRes(emptyList(), 0)
        }
    }
}
