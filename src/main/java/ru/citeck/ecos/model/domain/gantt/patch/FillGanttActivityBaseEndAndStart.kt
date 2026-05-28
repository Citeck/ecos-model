package ru.citeck.ecos.model.domain.gantt.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosLocalPatch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable

/**
 * Fills empty baseline-end and start dates for all gantt-activity records:
 *  - if activity:baseEnd is empty, set it to activity:end;
 *  - if it is still empty, set it to _created;
 *  - if activity:start is empty, set it to _created;
 *  - if activity:baseEnd is less than activity:start, set it to activity:start.
 */
@Component
@EcosLocalPatch("fill-gantt-activity-base-end-and-start", "2026-05-21T00:00:00Z", afterStart = true)
class FillGanttActivityBaseEndAndStart(
    private val recordsService: RecordsService,
    private val typesRegistry: EcosTypesRegistry
) : Callable<Any> {

    companion object {
        private const val TYPE_ID = "gantt-activity"

        private const val ATT_START = "activity:start"
        private const val ATT_END = "activity:end"
        private const val ATT_BASE_END = "activity:baseEnd"

        private const val PAGE_SIZE = 100
        private const val LOG_CHUNK_SIZE = 500

        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val sourceId = typesRegistry.getValue(TYPE_ID)?.sourceId
        if (sourceId.isNullOrBlank()) {
            log.warn { "Type '$TYPE_ID' is not registered. Patch skipped" }
            return 0
        }

        val totalUpdated = AuthContext.runAsSystem {
            fillBaseEndAndStart(sourceId)
        }

        log.info { "Updating completed. Updated $totalUpdated gantt-activity record(s)" }
        return totalUpdated
    }

    private fun fillBaseEndAndStart(sourceId: String): Int {

        val updatedChunk = mutableListOf<String>()
        var totalUpdated = 0
        var skipCount = 0

        fun flushUpdatedChunk(force: Boolean = false) {
            if (updatedChunk.isEmpty() || (!force && updatedChunk.size < LOG_CHUNK_SIZE)) {
                return
            }
            log.info { "Updated gantt-activities (total $totalUpdated): $updatedChunk" }
            updatedChunk.clear()
        }

        while (true) {
            val activities = recordsService.query(
                RecordsQuery.create()
                    .withSourceId(sourceId)
                    .withEcosType(TYPE_ID)
                    .withSortBy(SortBy(RecordConstants.ATT_CREATED, true))
                    .withMaxItems(PAGE_SIZE)
                    .withSkipCount(skipCount)
                    .build(),
                ActivityAtts::class.java
            ).getRecords()

            if (activities.isEmpty()) {
                break
            }

            for (activity in activities) {
                val attsToMutate = computeAttsToMutate(activity)
                if (attsToMutate.isEmpty()) {
                    continue
                }
                TxnContext.doInNewTxn {
                    recordsService.mutate(
                        activity.id,
                        attsToMutate + mapOf(
                            DbRecordsControlAtts.DISABLE_AUDIT to true,
                            DbRecordsControlAtts.DISABLE_EVENTS to true
                        )
                    )
                }
                updatedChunk.add(activity.id.toString())
                totalUpdated++
                flushUpdatedChunk()
            }

            skipCount += activities.size
        }

        flushUpdatedChunk(force = true)

        return totalUpdated
    }

    private fun computeAttsToMutate(activity: ActivityAtts): Map<String, Any> {

        // baseEnd and start are stored as date only, so every source value is truncated to 00:00:00 UTC
        val baseEnd = activity.baseEnd?.truncatedTo(ChronoUnit.DAYS)
        val end = activity.end?.truncatedTo(ChronoUnit.DAYS)
        val start = activity.start?.truncatedTo(ChronoUnit.DAYS)
        val created = activity.created?.truncatedTo(ChronoUnit.DAYS)

        var newBaseEnd = baseEnd ?: end ?: created
        val newStart = start ?: created

        if (newBaseEnd != null && newStart != null && newBaseEnd.isBefore(newStart)) {
            newBaseEnd = newStart
        }

        val attsToMutate = LinkedHashMap<String, Any>()
        if (newBaseEnd != null && newBaseEnd != activity.baseEnd) {
            attsToMutate[ATT_BASE_END] = newBaseEnd
        }
        if (newStart != null && newStart != activity.start) {
            attsToMutate[ATT_START] = newStart
        }
        return attsToMutate
    }

    private class ActivityAtts(
        @AttName("?id")
        val id: EntityRef,
        @AttName(ATT_START)
        val start: Instant?,
        @AttName(ATT_END)
        val end: Instant?,
        @AttName(ATT_BASE_END)
        val baseEnd: Instant?,
        @AttName("_created")
        val created: Instant?
    )
}
