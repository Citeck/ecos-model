package ru.citeck.ecos.model.domain.doclib.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.doclib.desc.DocLibDirDesc
import ru.citeck.ecos.model.domain.doclib.service.DocLibDirUtils
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Component
class DocLibDirPathUpdateJob(
    private val appLockService: EcosAppLockService,
    private val recordsService: RecordsService,
    private val typesRegistry: EcosTypesRegistry
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val DEFAULT_FULL_UPDATE_DELAY = Duration.ofMinutes(10).toMillis()

        private const val LOCK_KEY = "doclib-dir-updater"
        private const val DOCLIB_DIRECTORY_TYPE_ID = "doclib-directory"
    }

    private val manualUpdateUntil = AtomicLong(0L)
    private val fullUpdateRequired = AtomicBoolean()
    private val lastFullUpdateCompletedAt = AtomicLong(0L)

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

    private fun updateAllByJob(timeoutMs: Long): Boolean {
        log.debug { "Full updating started" }
        val updatingStartedAt = System.currentTimeMillis()
        return appLockService.doInSyncOrSkip(LOCK_KEY, Duration.ofMillis(timeoutMs)) {
            val lockAcquiredAt = System.currentTimeMillis()
            updateAllForTypeImpl(
                DOCLIB_DIRECTORY_TYPE_ID,
                true,
                System.currentTimeMillis() + Duration.ofMinutes(30).toMillis()
            )
            log.debug {
                "Job completed in ${System.currentTimeMillis() - lockAcquiredAt}ms. " +
                    "Lock waiting: ${lockAcquiredAt - updatingStartedAt}ms"
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
                for (typeId in types) {
                    if (!updateAllForTypeImpl(typeId, false, updateUntilMs)) {
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
            val query = RecordsQuery.create()
                .withEcosType(typeDef.id)
                .withSourceId(typeDef.sourceId)
                .withQuery(
                    Predicates.and(
                        Predicates.eq("${RecordConstants.ATT_PARENT}.${RecordConstants.ATT_TYPE}", typeRef),
                        Predicates.or(
                            Predicates.eq(
                                "(${RecordConstants.ATT_PARENT}.${DocLibDirDesc.ATT_DIR_PATH_HASH} " +
                                    "= ${DocLibDirDesc.ATT_PARENT_DIR_PATH_HASH})",
                                false
                            ),
                            Predicates.and(
                                Predicates.notEmpty(RecordConstants.ATT_PARENT),
                                Predicates.empty(DocLibDirDesc.ATT_DIR_PATH_HASH)
                            )
                        )
                    )
                ).withMaxItems(100).build()

            fun queryNext(): Map<EntityRef, DirToUpdateAtts> {
                return TxnContext.doInNewTxn {
                    recordsService.query(query, DirToUpdateAtts::class.java)
                        .getRecords()
                        .filter { it.parentRef.isNotEmpty() }
                        .associateBy { it.id }
                }
            }

            var directoriesToUpdate = queryNext()
            while (directoriesToUpdate.isNotEmpty()) {
                log.info { "Found ${directoriesToUpdate.size} directories to update for type $typeId" }
                for (directoryAtts in directoriesToUpdate.values) {
                    val newPath = listOf(*directoryAtts.parentDirPath.toTypedArray(), directoryAtts.parentRef)
                    TxnContext.doInNewTxn {
                        recordsService.mutate(
                            directoryAtts.id,
                            mapOf(
                                DocLibDirDesc.ATT_DIR_PATH to newPath,
                                DocLibDirDesc.ATT_PARENT_DIR_PATH_HASH to directoryAtts.parentDirPathHash,
                                DocLibDirDesc.ATT_DIR_PATH_HASH to DocLibDirUtils.calculatePathHash(newPath)
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
                if (!updateAllForTypeImpl(it.getLocalId(), true, updateUntilMs, processedSourceIds)) {
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
        @AttName("_parent.dirPath[]?id!")
        val parentDirPath: List<EntityRef>,
        @AttName("_parent.dirPathHash!")
        val parentDirPathHash: String
    )
}
