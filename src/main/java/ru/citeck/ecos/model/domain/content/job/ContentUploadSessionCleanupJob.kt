package ru.citeck.ecos.model.domain.content.job

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.content.storage.ChunkedUploadGoneException
import ru.citeck.ecos.data.sql.content.upload.DbContentUploadSessionEntity
import ru.citeck.ecos.data.sql.context.DbSchemaContext
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import ru.citeck.ecos.webapp.lib.spring.context.content.upload.ContentUploadProps
import ru.citeck.ecos.webapp.lib.web.webapi.exception.EcosWebException
import java.io.IOException
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * Deletes chunked-upload sessions untouched for longer than the configured idle timeout, releasing
 * what they still hold in the storage. What a session owns is read off the row (`storageState` /
 * `dataKey`), never off its status: `ABORTED` also covers an upload whose storage abort failed.
 */
@Component
class ContentUploadSessionCleanupJob(
    private val dbDomainFactory: DbDomainFactory,
    private val taskScheduler: EcosTaskSchedulerApi,
    private val appLockService: EcosAppLockService,
    private val contentUploadProps: ContentUploadProps
) {

    companion object {
        private const val LOCK_KEY = "content-upload-session-cleanup"
        private const val BATCH_SIZE = 100

        /**
         * Wall-clock budget for one run: aborts are remote calls made inside a single transaction
         * while the distributed lock is held. Sessions not reached in time wait for the next tick.
         */
        private val RUN_BUDGET: Duration = Duration.ofMinutes(2)

        /** After this many consecutive failed aborts the session is quarantined, not retried again. */
        internal const val MAX_ABORT_ATTEMPTS = 5

        private const val MAX_TRACKED_FAILURES = 10_000

        private val log = KotlinLogging.logger {}
    }

    @Value("\${ecos.job.contentUploadSessionCleanup.cron}")
    private lateinit var cron: String

    /** Failed aborts per session (`schema|uploadId`). Losing them only delays a quarantine. */
    private val abortFailures = ConcurrentHashMap<String, Int>()

    @PostConstruct
    fun init() {
        taskScheduler.schedule(
            "ContentUploadSessionCleanupJob",
            Schedules.cron(cron)
        ) {
            appLockService.doInSyncOrSkip(LOCK_KEY, Duration.ofSeconds(10)) { cleanup() }
        }
    }

    fun cleanup() {
        cleanup(Instant.now().minus(contentUploadProps.getSessionIdleTimeout()))
    }

    internal fun cleanup(expiredBefore: Instant) {
        val deadline = Instant.now().plus(RUN_BUDGET)
        // only contexts created so far in this JVM, but emodel DAOs create theirs eagerly
        dbDomainFactory.getDataSourceContext().getSchemaContexts().forEach { schemaCtx ->
            // per-schema isolation: a failure for one schema must not skip the schemas after it
            try {
                // a scheduled job has no ambient transaction and cleanupExpired opens none
                val removed = schemaCtx.doInNewTxn {
                    schemaCtx.uploadSessionService.cleanupExpired(expiredBefore, BATCH_SIZE) { session ->
                        abortSession(schemaCtx, session, deadline)
                    }
                }
                if (removed > 0) {
                    log.info { "Removed $removed expired upload session(s) in schema '${schemaCtx.schema}'" }
                }
                if (Instant.now().isAfter(deadline)) {
                    log.warn {
                        "Upload session cleanup run hit its ${RUN_BUDGET.toMinutes()}m budget in schema " +
                            "'${schemaCtx.schema}', remaining expired sessions are left for the next run"
                    }
                }
            } catch (e: Exception) {
                log.warn(e) { "Upload session cleanup failed for schema '${schemaCtx.schema}', skipping it this run" }
            }
        }
    }

    /**
     * Called by `cleanupExpired` for every expired session except a DONE one. `true` deletes the row,
     * `false` leaves it for the next run (bounded, see [onAbortFailed]). No storage call past
     * [deadline] - the run budget from [cleanup].
     */
    internal fun abortSession(
        schemaCtx: DbSchemaContext,
        session: DbContentUploadSessionEntity,
        deadline: Instant
    ): Boolean {
        if (session.dataKey.isBlank() && session.storageState.isBlank()) {
            // nothing in the storage belongs to this row (never opened, or quarantined)
            forgetFailures(schemaCtx, session)
            return true
        }
        if (Instant.now().isAfter(deadline)) {
            // out of budget: release the txn and the lock instead of making more remote calls
            return false
        }
        val storageRef = resolveStorageRef(schemaCtx, session)
        if (storageRef == null) {
            forgetFailures(schemaCtx, session)
            return true
        }
        if (session.dataKey.isNotBlank()) {
            return reclaimAssembledObject(schemaCtx, session, storageRef)
        }
        return try {
            AuthContext.runAsSystem {
                schemaCtx.contentStorageService.chunkedAbort(storageRef, session.storageState)
            }
            forgetFailures(schemaCtx, session)
            true
        } catch (_: ChunkedUploadGoneException) {
            // the storage already reports the upload gone - nothing to reclaim, so this is success
            forgetFailures(schemaCtx, session)
            true
        } catch (e: Exception) {
            onAbortFailed(schemaCtx, session, e)
        }
    }

    /**
     * Reclaims a session whose object was already assembled - `dataKey` is written before the record,
     * so completion crashed in between. The object goes only if no `ed_content` row references it.
     */
    private fun reclaimAssembledObject(
        schemaCtx: DbSchemaContext,
        session: DbContentUploadSessionEntity,
        storageRef: EntityRef
    ): Boolean {
        val dataKey = session.dataKey
        return try {
            val referenced = AuthContext.runAsSystem {
                schemaCtx.contentService.findContentByStorageAndDataKey(storageRef, dataKey) != null
            }
            if (referenced) {
                log.info {
                    "Assembled object of expired upload session '${session.extId}' in schema " +
                        "'${schemaCtx.schema}' is still referenced by a content row, deleting the " +
                        "session row only: storage=$storageRef dataKey=$dataKey"
                }
            } else {
                AuthContext.runAsSystem {
                    schemaCtx.contentStorageService.deleteContent(storageRef, dataKey)
                }
                log.warn {
                    "Deleted the orphaned assembled object of expired upload session " +
                        "'${session.extId}' in schema '${schemaCtx.schema}': storage=$storageRef " +
                        "dataKey=$dataKey"
                }
            }
            forgetFailures(schemaCtx, session)
            true
        } catch (e: Exception) {
            onAbortFailed(schemaCtx, session, e)
        }
    }

    /**
     * A row that is never deleted blocks `findExpired`'s oldest-first, batch-capped queue, so a
     * failure is either permanent ([permanentFailureReason]) or retried at most
     * [MAX_ABORT_ATTEMPTS] times - both terminal cases quarantine the session.
     */
    private fun onAbortFailed(
        schemaCtx: DbSchemaContext,
        session: DbContentUploadSessionEntity,
        error: Exception
    ): Boolean {
        val attempts = registerFailure(schemaCtx, session)
        val permanentReason = permanentFailureReason(error)
        if (permanentReason != null || attempts >= MAX_ABORT_ATTEMPTS) {
            val reason = permanentReason ?: "$attempts consecutive abort attempts failed"
            if (quarantine(schemaCtx, session, reason, error)) {
                return false
            }
        }
        log.warn(error) {
            "Chunked abort failed for upload session '${session.extId}' in schema " +
                "'${schemaCtx.schema}', leaving it for the next run"
        }
        return false
    }

    /**
     * Gives up on a session: retires it to `ABORTED` and clears the `storageState` / `dataKey`.
     * Clearing them is what makes it stick - the sweep decides what to call the storage about from
     * those two fields, and `ABORTED` alone also means "retry the abort".
     */
    private fun quarantine(
        schemaCtx: DbSchemaContext,
        session: DbContentUploadSessionEntity,
        reason: String,
        error: Exception
    ): Boolean {
        val updated = try {
            // the stored status is the CAS expectation as-is: a value this version cannot parse
            // must still be retirable, or the row blocks the head of the queue
            schemaCtx.uploadSessionService.retire(session.extId, session.status)
        } catch (e: Exception) {
            log.warn(e) {
                "Failed to quarantine upload session '${session.extId}' in schema " +
                    "'${schemaCtx.schema}', leaving it for the next run"
            }
            return false
        }
        if (!updated) {
            // moved concurrently or already gone - the next run picks up whatever state it is in
            return false
        }
        forgetFailures(schemaCtx, session)
        // retiring cleared the handle off the row, so this log line is all an operator has to go on
        val leftBehind = if (session.dataKey.isNotBlank()) {
            "its assembled object (dataKey=${session.dataKey}) stays in the storage, out of reach " +
                "of the bucket lifecycle rule, and has to be removed manually"
        } else {
            "its multipart upload (storageState=${session.storageState}), if any, is left to the " +
                "bucket lifecycle rule"
        }
        log.warn(error) {
            "Quarantined upload session '${session.extId}' in schema '${schemaCtx.schema}': $reason. " +
                "It is marked ABORTED and will be deleted on the next run without a storage abort - " +
                leftBehind
        }
        return true
    }

    /**
     * Non-null iff the failure cannot be fixed by retrying. I/O, timeout, DB and interrupt errors and
     * a retryable [EcosWebException] are transient and win over everything else; IllegalArgument /
     * IllegalState is how the storage layer reports deterministic misconfiguration, hence permanent.
     * Everything else is transient - a rolling restart of the storage app looks exactly like it.
     */
    private fun permanentFailureReason(error: Throwable): String? {
        var current: Throwable? = error
        var permanent: String? = null
        var guard = 0
        while (current != null && guard++ < 32) {
            when {
                current is IOException ||
                    current is TimeoutException ||
                    current is SQLException ||
                    current is InterruptedException ||
                    (current is EcosWebException && current.retryable) -> return null

                permanent == null &&
                    (current is IllegalArgumentException || current is IllegalStateException) ->
                    permanent = "permanent failure (${current.javaClass.simpleName}: ${current.message})"
            }
            current = current.cause
        }
        return permanent
    }

    private fun failureKey(schemaCtx: DbSchemaContext, session: DbContentUploadSessionEntity): String {
        return schemaCtx.schema + "|" + session.extId
    }

    private fun registerFailure(schemaCtx: DbSchemaContext, session: DbContentUploadSessionEntity): Int {
        if (abortFailures.size >= MAX_TRACKED_FAILURES) {
            abortFailures.clear()
        }
        return abortFailures.merge(failureKey(schemaCtx, session), 1, Int::plus) ?: 1
    }

    private fun forgetFailures(schemaCtx: DbSchemaContext, session: DbContentUploadSessionEntity) {
        if (abortFailures.isNotEmpty()) {
            abortFailures.remove(failureKey(schemaCtx, session))
        }
    }

    /**
     * Resolves [DbContentUploadSessionEntity.storageRef], an id into the ref table. `null` when the
     * id is unset or no longer registered. Checked via `getExtIdById` rather than by catching
     * `getEntityRefById`, so a transient DB failure is not read as "no storage ref".
     */
    internal fun resolveStorageRef(schemaCtx: DbSchemaContext, session: DbContentUploadSessionEntity): EntityRef? {
        val id = session.storageRef
        if (id < 0) {
            return null
        }
        val extId = schemaCtx.recordRefService.getExtIdById(id)
        if (extId.isEmpty()) {
            log.warn {
                "Storage ref id $id for upload session '${session.extId}' in schema " +
                    "'${schemaCtx.schema}' no longer resolves - deleting the row without a storage abort"
            }
            return null
        }
        return EntityRef.valueOf(extId.toEntityRef())
    }
}
