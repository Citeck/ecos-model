package ru.citeck.ecos.model.domain.treesearch

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max

@Component
class TreeSearchPathUpdateJob(
    private val appLockService: EcosAppLockService,
    private val recordsService: RecordsService,
    private val typesRegistry: EcosTypesRegistry,
    private val webAppApi: EcosWebAppApi
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val DEFAULT_FULL_UPDATE_DELAY = Duration.ofMinutes(10).toMillis()

        private const val LOCK_KEY = "tree-search-updater"
    }

    private val manualUpdateUntil = AtomicLong(0L)
    private val fullUpdateRequired = AtomicBoolean()
    private val lastFullUpdateCompletedAt = AtomicLong(0L)

    @Volatile
    private var typesWithTreeSearchAspect: Set<String> = emptySet()
    private val typesWithTreeSearchUpdateLock = ReentrantLock()

    @PostConstruct
    fun init() {
        webAppApi.doWhenAppReady {
            updateTypesWithTreeSearchAspect()
            typesRegistry.listenEvents(this::handleTypeUpdateEvent)
        }
    }

    private fun handleTypeUpdateEvent(id: String, before: TypeDef?, after: TypeDef?) {

        fun hasTreeSearchAspect(typeDef: TypeDef?): Boolean {
            typeDef ?: return false
            return typeDef.aspects.any { it.ref.getLocalId() == TreeSearchDesc.ASPECT_ID }
        }

        val hasBefore = hasTreeSearchAspect(before)
        val hasAfter = hasTreeSearchAspect(after)

        if (hasBefore != hasAfter) {
            if (typesWithTreeSearchUpdateLock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    val newTypesSet = HashSet(typesWithTreeSearchAspect)
                    if (hasAfter) {
                        if (newTypesSet.all { !typesRegistry.isSubType(id, it) }) {
                            log.info { "Type '$id' was registered to tree search update job" }
                            newTypesSet.add(id)
                        }
                    } else {
                        log.info { "Type '$id' was unregistered from tree search update job" }
                        newTypesSet.remove(id)
                    }
                    typesWithTreeSearchAspect = newTypesSet
                } finally {
                    typesWithTreeSearchUpdateLock.unlock()
                }
            } else {
                log.info {
                    "typesWithTreeSearchUpdateLock can't be acquired in 5 seconds. " +
                            "Type '$id' with tree search aspect will be registered later"
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "PT30S")
    fun update() {
        if (!fullUpdateRequired.compareAndSet(true, false) &&
            (System.currentTimeMillis() - lastFullUpdateCompletedAt.get()) < DEFAULT_FULL_UPDATE_DELAY
        ) {
            return
        }
        log.debug { "Begin updating" }
        val manualUpdateUntil = this.manualUpdateUntil.get()
        if (!updateAllByJob(2000)) {
            if (manualUpdateUntil == 0L) {
                log.debug { "Other job already started. Skipping..." }
            } else {
                log.debug { "Manual update in progress. Wait until end and try to do our job again" }
                val manualUpdateDuration = max(0, manualUpdateUntil - System.currentTimeMillis())
                if (!updateAllByJob(manualUpdateDuration + 10_000)) {
                    log.debug { "Updating was skipped because lock can't be acquired." }
                }
            }
        }
        lastFullUpdateCompletedAt.set(System.currentTimeMillis())
    }

    @Scheduled(fixedDelayString = "PT10M")
    fun updateTypesWithTreeSearchAspect() {
        if (!typesWithTreeSearchUpdateLock.tryLock(30, TimeUnit.SECONDS)) {
            log.warn { "typesWithTreeSearchUpdateLock can't be acquired after 30 seconds." }
            return
        }
        try {
            val typesWithAspect = typesRegistry.getAllValues().values.filter { entry ->
                entry.entity.aspects.any { it.ref.getLocalId() == TreeSearchDesc.ASPECT_ID }
            }
            val typeIdsWithAspect = typesWithAspect.mapTo(HashSet()) { it.entity.id }
            val checkedPath = ArrayList<TypeDef>()
            val filteredTypeIds = HashSet<String>()

            // filter children types if parent already registered to update
            for (typeDefWithMeta in typesWithAspect) {
                val typeId = typeDefWithMeta.entity.id
                if (!typeIdsWithAspect.contains(typeId) || filteredTypeIds.contains(typeId)) {
                    continue
                }
                checkedPath.clear()
                checkedPath.add(typeDefWithMeta.entity)

                var parentTypeId = typeDefWithMeta.entity.parentRef.getLocalId()
                while (parentTypeId.isNotBlank() && parentTypeId != "base") {
                    if (typeIdsWithAspect.contains(parentTypeId) || filteredTypeIds.contains(parentTypeId)) {
                        checkedPath.forEach {
                            filteredTypeIds.add(it.id)
                            typeIdsWithAspect.remove(it.id)
                        }
                        break
                    } else {
                        val parentDef = typesRegistry.getValue(parentTypeId) ?: break
                        checkedPath.add(parentDef)
                        parentTypeId = parentDef.parentRef.getLocalId()
                    }
                }
            }
            typesWithTreeSearchAspect = typeIdsWithAspect
        } finally {
            typesWithTreeSearchUpdateLock.unlock()
        }
    }

    private fun updateAllByJob(timeoutMs: Long): Boolean {
        log.debug { "Full updating started" }
        val updatingStartedAt = System.currentTimeMillis()
        return appLockService.doInSyncOrSkip(LOCK_KEY, Duration.ofMillis(timeoutMs)) {
            val lockAcquiredAt = System.currentTimeMillis()
            val updateUntilMs = System.currentTimeMillis() + Duration.ofMinutes(30).toMillis()
            var jobDone = true
            log.debug { "Update types: " + typesWithTreeSearchAspect.joinToString() }
            for (typeId in typesWithTreeSearchAspect) {
                jobDone = updateAllForTypeImpl(
                    typeId = typeId,
                    processTreeRoots = true,
                    processChildTypes = true,
                    updateUntilMs = updateUntilMs
                )
                if (!jobDone) {
                    log.warn { "Tree updating interrupted by timeout after 30 minutes. Type: '$typeId'" }
                    break
                }
            }
            log.debug {
                "Job completed in ${System.currentTimeMillis() - lockAcquiredAt}ms. " +
                        "Lock waiting: ${lockAcquiredAt - updatingStartedAt}ms. Job done: $jobDone"
            }
        }
    }

    fun manualUpdateForExactTypes(types: Set<String>, timeout: Duration) {
        log.debug { "Manual update for exact type started for $types with timeout $timeout" }
        val startedAt = System.currentTimeMillis()
        val updateUntilMs = System.currentTimeMillis() + (max(timeout.toMillis(), 30_000))
        manualUpdateUntil.set(updateUntilMs)
        try {
            appLockService.doInSyncOrSkip(LOCK_KEY, timeout) {
                val lockAcquiredAt = System.currentTimeMillis()
                val processedSourceIds = HashSet<String>()
                for (typeId in types) {
                    if (!updateAllForTypeImpl(
                            typeId = typeId,
                            processTreeRoots = false,
                            processChildTypes = false,
                            updateUntilMs = updateUntilMs,
                            processedSourceIds = processedSourceIds
                        )
                    ) {
                        fullUpdateRequired.set(true)
                        log.debug { "Manual updating doesn't completed in $timeout. Full updating will be performed." }
                        break
                    }
                }
                log.debug {
                    "Manual updating completed. Elapsed time: ${System.currentTimeMillis() - startedAt}. " +
                            "Lock acquisition time: ${lockAcquiredAt - startedAt}"
                }
            }
        } finally {
            manualUpdateUntil.set(0)
        }
        log.debug { "Manual updating doesn't completed in $timeout. Schedule full updating." }
    }

    /**
     * @return return true if all work was successfully done. false if work was interrupted by timeout.
     */
    private fun updateAllForTypeImpl(
        typeId: String,
        processTreeRoots: Boolean,
        processChildTypes: Boolean,
        updateUntilMs: Long,
        processedSourceIds: MutableSet<String> = HashSet()
    ): Boolean {
        if (typeId.isBlank()) {
            return true
        } else if (System.currentTimeMillis() > updateUntilMs) {
            return false
        }
        val typeDef = typesRegistry.getValue(typeId) ?: return true
        val typeRef = ModelUtils.getTypeRef(typeDef.id)
        if (processedSourceIds.add(typeDef.sourceId)) {
            val baseQuery = RecordsQuery.create()
                .withEcosType(typeDef.id)
                .withSourceId(typeDef.sourceId)
                .withMaxItems(100)
                .build()

            var queryIdx = 0
            val queryPredicates = ArrayList<Predicate>()
            if (processTreeRoots) {
                queryPredicates.add(
                    Predicates.and(
                        Predicates.notEmpty(RecordConstants.ATT_PARENT),
                        Predicates.notEq("${RecordConstants.ATT_PARENT}.${RecordConstants.ATT_TYPE}", typeRef),
                        Predicates.empty(TreeSearchDesc.ATT_PATH_HASH)
                    )
                )
            }
            queryPredicates.add(
                Predicates.and(
                    Predicates.eq("${RecordConstants.ATT_PARENT}.${RecordConstants.ATT_TYPE}", typeRef),
                    Predicates.or(
                        Predicates.eq(
                            "(${RecordConstants.ATT_PARENT}.\"${TreeSearchDesc.ATT_PATH_HASH}\" " +
                                    "= \"${TreeSearchDesc.ATT_PARENT_PATH_HASH}\")",
                            false
                        ),
                        Predicates.and(
                            Predicates.notEmpty(RecordConstants.ATT_PARENT),
                            Predicates.empty(TreeSearchDesc.ATT_PATH_HASH)
                        )
                    )
                )
            )
            val recordsQueries = queryPredicates.map { baseQuery.copy().withQuery(it).build() }

            fun queryNext(): Map<EntityRef, DirToUpdateAtts> {
                return TxnContext.doInNewTxn {
                    var result: Map<EntityRef, DirToUpdateAtts> = emptyMap()
                    while (queryIdx < recordsQueries.size) {
                        result = recordsService.query(recordsQueries[queryIdx], DirToUpdateAtts::class.java)
                            .getRecords()
                            .filter { it.parentRef.isNotEmpty() }
                            .associateBy { it.id }
                        if (result.isNotEmpty()) {
                            log.trace { "Found ${result.size} records by query ${recordsQueries[queryIdx]}" }
                            break
                        } else {
                            queryIdx++
                        }
                    }
                    result
                }
            }

            var directoriesToUpdate = queryNext()
            while (directoriesToUpdate.isNotEmpty()) {
                log.info { "Found ${directoriesToUpdate.size} records to update for type '$typeId'" }
                for (directoryAtts in directoriesToUpdate.values) {
                    val newPath = listOf(*directoryAtts.parentTreePath.toTypedArray(), directoryAtts.parentRef)
                    TxnContext.doInNewTxn {
                        recordsService.mutate(
                            directoryAtts.id,
                            mapOf(
                                TreeSearchDesc.ATT_PATH to newPath,
                                TreeSearchDesc.ATT_PARENT_PATH_HASH to directoryAtts.parentTreePathHash,
                                TreeSearchDesc.ATT_PATH_HASH to TreeSearchDesc.calculatePathHash(newPath)
                            )
                        )
                    }
                    if (System.currentTimeMillis() > updateUntilMs) {
                        return false
                    }
                }
                directoriesToUpdate = queryNext()
            }
        }
        if (System.currentTimeMillis() > updateUntilMs) {
            return false
        }
        if (processChildTypes) {
            typesRegistry.getChildren(typeRef).forEach {
                if (!updateAllForTypeImpl(
                        typeId = it.getLocalId(),
                        processTreeRoots = processTreeRoots,
                        processChildTypes = true,
                        updateUntilMs = updateUntilMs,
                        processedSourceIds = processedSourceIds
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    private class DirToUpdateAtts(
        val id: EntityRef,
        @AttName("_parent?id!")
        val parentRef: EntityRef,
        @AttName("_parent.${TreeSearchDesc.ATT_PATH}[]?id!")
        val parentTreePath: List<EntityRef>,
        @AttName("_parent.${TreeSearchDesc.ATT_PATH_HASH}!")
        val parentTreePathHash: String
    )
}
