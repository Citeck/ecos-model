package ru.citeck.ecos.model.domain.activity.event

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration
import java.time.Instant

/**
 * Keeps `activity:start` / `activity:baseEnd` of every "summary" activity
 * equal to the aggregate of its descendants:
 *
 *   start   = min(descendant.activity:start)    fallback: summary._created
 *   baseEnd = max(descendant.activity:baseEnd)  fallback: summary._created + 1 day
 *
 * A "summary" is any record carrying the `activity-atts` aspect with
 * `activity:type` = `summary`. Descendants are collected via depth-first
 * walk through plain task children; nested summaries are treated as opaque
 * leaves (their own dates are aggregated by this same listener whenever
 * they change).
 *
 * Every event that may change the aggregate marks the relevant summary on
 * a txn-scoped set; right before commit each marked summary is recomputed
 * once via a children query. Summaries deleted in the same txn are tracked
 * in a separate set and skipped — otherwise the platform's source-assoc
 * cleanup in DbRecordsDeleteDao (which mutates every task pointing to the
 * deleted summary to clear `activity:parent`) would mark the summary about
 * to disappear and the flush would crash with "Record not found" trying
 * to mutate it.
 *
 * Outside any transaction the flush runs inline via
 * [TxnContext.doBeforeCommit].
 */
@Component
class SummaryActivityDatesListener(
    eventsService: EventsService,
    private val recordsService: RecordsService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val ACTIVITY_ATTS_ASPECT = "activity-atts"
        private const val ACTIVITY_ATT_PARENT = "activity:parent"
        private const val ACTIVITY_ATT_START = "activity:start"
        private const val ACTIVITY_ATT_BASE_END = "activity:baseEnd"
        private const val ACTIVITY_ATT_TYPE = "activity:type"
        private const val SUMMARY_TYPE = "summary"

        private val FALLBACK_END_OFFSET: Duration = Duration.ofDays(1)
        private const val MAX_CHAIN_DEPTH = 50
        private const val MAX_CHILDREN_PER_PARENT = 10_000
        private const val MAX_DESCENDANTS_PER_SUMMARY = 100_000

        private val TXN_PENDING_KEY = Any()
        private val TXN_DELETED_KEY = Any()

        private const val FILTER_HAS_ACTIVITY_ATTS = "record._aspects._has.$ACTIVITY_ATTS_ASPECT?bool!"
        private const val FILTER_DIFF_HAS_START = "diff._has.$ACTIVITY_ATT_START?bool!"
        private const val FILTER_DIFF_HAS_END = "diff._has.$ACTIVITY_ATT_BASE_END?bool!"
        private const val FILTER_DIFF_HAS_PARENT = "diff._has.$ACTIVITY_ATT_PARENT?bool!"
    }

    init {

        eventsService.addListener<ActivityCreatedEvent> {
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(ActivityCreatedEvent::class.java)
            withFilter(Predicates.eq(FILTER_HAS_ACTIVITY_ATTS, true))
            withTransactional(true)
            withAction { event -> handleActivityCreated(event) }
        }

        eventsService.addListener<ActivityChangedEvent> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(ActivityChangedEvent::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq(FILTER_HAS_ACTIVITY_ATTS, true),
                    Predicates.or(
                        Predicates.eq(FILTER_DIFF_HAS_START, true),
                        Predicates.eq(FILTER_DIFF_HAS_END, true),
                        Predicates.eq(FILTER_DIFF_HAS_PARENT, true)
                    )
                )
            )
            withTransactional(true)
            withAction { event -> handleActivityChanged(event) }
        }

        eventsService.addListener<ActivityDeletedEvent> {
            withEventType(RecordDeletedEvent.TYPE)
            withDataClass(ActivityDeletedEvent::class.java)
            withFilter(Predicates.eq(FILTER_HAS_ACTIVITY_ATTS, true))
            withTransactional(true)
            withAction { event -> handleActivityDeleted(event) }
        }
    }

    private fun handleActivityCreated(event: ActivityCreatedEvent) {
        if (event.record.isEmpty()) return

        if (event.isSummary) {
            markForFlush(event.record)
        }
        if (event.parent.isNotEmpty()) {
            findNearestSummaryAncestor(event.parent)?.let(::markForFlush)
        }
    }

    private fun handleActivityChanged(event: ActivityChangedEvent) {
        // activity:parent is an ASSOC and is therefore not in event.before / event.after
        // maps — assoc changes live in diff.list[] (added / removed). We always trust
        // event.currentParent for the after-state and only look at diff.list to detect
        // a parent move and recover the previous parent.
        val parentDiff = event.diff.firstOrNull { it.id == ACTIVITY_ATT_PARENT }
        val previousParent = parentDiff?.removed?.firstOrNull() ?: EntityRef.EMPTY
        val parentMoved = parentDiff != null && previousParent != event.currentParent

        if (parentMoved) {
            if (previousParent.isNotEmpty()) {
                findNearestSummaryAncestor(previousParent)?.let(::markForFlush)
            }
            if (event.currentParent.isNotEmpty()) {
                findNearestSummaryAncestor(event.currentParent)?.let(::markForFlush)
            }
            return
        }
        // same parent, dates may have changed
        if (event.currentParent.isEmpty()) return
        val startChanged = event.beforeStart != event.afterStart
        val endChanged = event.beforeBaseEnd != event.afterBaseEnd
        if (!startChanged && !endChanged) return
        findNearestSummaryAncestor(event.currentParent)?.let(::markForFlush)
    }

    private fun handleActivityDeleted(event: ActivityDeletedEvent) {
        if (event.isSummary) {
            // DbRecordsDeleteDao clears every source assoc (each task's
            // activity:parent → this summary) right before deleting the
            // record. Each clear fires a RecordChangedEvent whose parentMoved
            // branch marks this summary as needing a re-aggregation. By the
            // time the before-commit flush runs the record is gone, so we
            // record the deletion here and have flushPending skip it.
            getTxnDeletedSummaries().add(event.record)
        }
        if (event.parent.isEmpty()) return
        findNearestSummaryAncestor(event.parent)?.let(::markForFlush)
    }

    /**
     * Add `summaryRef` to the per-txn set of summaries that need their dates
     * re-aggregated, and make sure exactly one before-commit flush is
     * registered for the txn.
     *
     * When called outside any transaction, [TxnContext.doBeforeCommit]
     * runs the flush immediately. The pending and deleted sets are
     * captured by the doBeforeCommit closure so the flush sees them — in
     * no-txn mode every call to [getTxnPendingSummaries] returns a fresh
     * throwaway set, so the closure has to carry the references through.
     */
    private fun markForFlush(summaryRef: EntityRef) {
        val pending = getTxnPendingSummaries()
        val deleted = getTxnDeletedSummaries()
        val isFirstEntry = pending.isEmpty()
        pending.add(summaryRef)
        if (isFirstEntry) {
            TxnContext.doBeforeCommit(0f) { flushPending(pending, deleted) }
        }
    }

    private fun getTxnPendingSummaries(): MutableSet<EntityRef> {
        return TxnContext.getTxnOrNull()?.getData(TXN_PENDING_KEY) { LinkedHashSet<EntityRef>() }
            ?: LinkedHashSet()
    }

    private fun getTxnDeletedSummaries(): MutableSet<EntityRef> {
        return TxnContext.getTxnOrNull()?.getData(TXN_DELETED_KEY) { HashSet<EntityRef>() }
            ?: HashSet()
    }

    private fun flushPending(pending: MutableSet<EntityRef>, deleted: Set<EntityRef>) {
        // Wave-based: each summary mutation we issue here fires a change event
        // synchronously (transactional listener + same-txn) which can mark
        // additional ancestor summaries. The before-commit executor snapshots
        // its action list, so a fresh doBeforeCommit registration would be
        // silently ignored — we just loop until no new summaries appear.
        val processed = HashSet<EntityRef>()
        AuthContext.runAsSystem {
            while (true) {
                val toProcess = pending.filter { it !in processed }
                if (toProcess.isEmpty()) return@runAsSystem
                for (summaryRef in toProcess) {
                    processed.add(summaryRef)
                    if (summaryRef in deleted) continue
                    flushSummary(summaryRef)
                }
            }
        }
    }

    private fun flushSummary(summaryRef: EntityRef) {
        val current = recordsService.getAtts(summaryRef, SummaryDatesAtts::class.java)
        // Safety: summary record is gone (deleted by a parallel mutation or a
        // remote DAO whose delete event we didn't observe). Nothing to mutate.
        val created = current.created ?: return

        val descendants = collectDescendantsForAggregation(summaryRef)
        val finalStart = descendants.mapNotNull { it.start }.minOrNull() ?: created
        val finalEnd = descendants.mapNotNull { it.baseEnd }.maxOrNull()
            ?: created.plus(FALLBACK_END_OFFSET)

        val toMutate = RecordAtts(summaryRef)
        var changed = false
        if (finalStart != current.start) {
            toMutate.setAtt(ACTIVITY_ATT_START, finalStart)
            changed = true
        }
        if (finalEnd != current.baseEnd) {
            toMutate.setAtt(ACTIVITY_ATT_BASE_END, finalEnd)
            changed = true
        }
        if (!changed) return

        recordsService.mutate(toMutate)
        log.debug { "Flushed summary $summaryRef dates: start=$finalStart baseEnd=$finalEnd" }
    }

    /**
     * Collect dates of every relevant descendant under `summaryRef`.
     *
     * The descent stops at every nested summary — the listener already keeps
     * each summary's `activity:start` / `activity:baseEnd` aligned with its
     * own subtree, so a sub-summary is treated as an opaque "summary" node.
     * Plain tasks have no such alignment, so we keep walking through them
     * to reach their grandchildren.
     */
    private fun collectDescendantsForAggregation(summaryRef: EntityRef): List<ChildDatesAtts> {
        // Siblings under a given parent live in the same source as the
        // summary itself (cross-source nesting via activity:parent is not
        // a supported case for this aggregator). Querying by source keeps
        // the predicate cheap and avoids accidentally crossing into
        // unrelated DAOs that happen to expose activity-atts.
        val sourceId = summaryRef.getSourceId()
        val workspace = recordsService.getAtt(summaryRef, "${RecordConstants.ATT_WORKSPACE}?localId").asText()
        val workspaces = if (workspace.isNotBlank()) listOf(workspace) else emptyList()

        val collected = mutableListOf<ChildDatesAtts>()
        val toVisit = ArrayDeque<EntityRef>()
        val visited = HashSet<EntityRef>()
        toVisit.add(summaryRef)

        while (toVisit.isNotEmpty() && collected.size < MAX_DESCENDANTS_PER_SUMMARY) {
            val parent = toVisit.removeFirst()
            if (!visited.add(parent)) continue

            val children = queryDirectChildren(parent, sourceId, workspaces)
            for (child in children) {
                collected.add(child)
                if (!child.isSummary && child.ref.isNotEmpty()) {
                    toVisit.addLast(child.ref)
                }
                if (collected.size >= MAX_DESCENDANTS_PER_SUMMARY) {
                    log.warn {
                        "Summary $summaryRef has more than $MAX_DESCENDANTS_PER_SUMMARY descendants; " +
                            "aggregation may be incomplete"
                    }
                    break
                }
            }
        }
        return collected
    }

    private fun queryDirectChildren(
        parentRef: EntityRef,
        sourceId: String,
        workspaces: List<String>
    ): List<ChildDatesAtts> {
        val query = RecordsQuery.create {
            withSourceId(sourceId)
            withLanguage(PredicateService.LANGUAGE_PREDICATE)
            withQuery(Predicates.eq(ACTIVITY_ATT_PARENT, parentRef.toString()))
            withMaxItems(MAX_CHILDREN_PER_PARENT)
            withWorkspaces(workspaces)
        }
        return recordsService.query(query, ChildDatesAtts::class.java).getRecords()
    }

    /**
     * Walks the `activity:parent` chain starting from `from` (inclusive)
     * and returns the first ref whose `activity:type` equals `summary`.
     * Returns null if no summary is reached within [MAX_CHAIN_DEPTH] hops.
     */
    private fun findNearestSummaryAncestor(from: EntityRef): EntityRef? {
        var current = from
        var depth = 0
        while (current.isNotEmpty() && depth < MAX_CHAIN_DEPTH) {
            depth++
            val info = recordsService.getAtts(current, ChainStepAtts::class.java)
            if (info.isSummary) return current
            current = info.parent
        }
        return null
    }

    private data class ActivityCreatedEvent(
        @AttName("record?id")
        val record: EntityRef = EntityRef.EMPTY,
        @AttName("record.$ACTIVITY_ATT_TYPE")
        val activityType: String = "",
        @AttName("record.$ACTIVITY_ATT_PARENT?id!''")
        val parent: EntityRef = EntityRef.EMPTY
    ) {
        val isSummary: Boolean get() = activityType == SUMMARY_TYPE
    }

    private data class ActivityChangedEvent(
        @AttName("record?id")
        val record: EntityRef = EntityRef.EMPTY,
        @AttName("before.$ACTIVITY_ATT_START")
        val beforeStart: Instant? = null,
        @AttName("after.$ACTIVITY_ATT_START")
        val afterStart: Instant? = null,
        @AttName("before.$ACTIVITY_ATT_BASE_END")
        val beforeBaseEnd: Instant? = null,
        @AttName("after.$ACTIVITY_ATT_BASE_END")
        val afterBaseEnd: Instant? = null,
        // ASSOCs are not present in before / after maps — only via diff.list
        // (and the current value via record.X).
        @AttName("record.$ACTIVITY_ATT_PARENT?id!''")
        val currentParent: EntityRef = EntityRef.EMPTY,
        @AttName("diff.list[]?json")
        val diff: List<DiffEntry> = emptyList()
    )

    private data class DiffEntry(
        val id: String = "",
        val added: List<EntityRef> = emptyList(),
        val removed: List<EntityRef> = emptyList()
    )

    private data class ActivityDeletedEvent(
        @AttName("record?id")
        val record: EntityRef = EntityRef.EMPTY,
        @AttName("record.$ACTIVITY_ATT_TYPE")
        val activityType: String = "",
        @AttName("record.$ACTIVITY_ATT_PARENT?id!''")
        val parent: EntityRef = EntityRef.EMPTY
    ) {
        val isSummary: Boolean get() = activityType == SUMMARY_TYPE
    }

    private data class SummaryDatesAtts(
        @AttName("_created")
        val created: Instant? = null,
        @AttName(ACTIVITY_ATT_START)
        val start: Instant? = null,
        @AttName(ACTIVITY_ATT_BASE_END)
        val baseEnd: Instant? = null
    )

    private data class ChildDatesAtts(
        @AttName("?id")
        val ref: EntityRef = EntityRef.EMPTY,
        @AttName(ACTIVITY_ATT_START)
        val start: Instant? = null,
        @AttName(ACTIVITY_ATT_BASE_END)
        val baseEnd: Instant? = null,
        @AttName(ACTIVITY_ATT_TYPE)
        val activityType: String = ""
    ) {
        val isSummary: Boolean get() = activityType == SUMMARY_TYPE
    }

    private data class ChainStepAtts(
        @AttName("$ACTIVITY_ATT_PARENT?id!''")
        val parent: EntityRef = EntityRef.EMPTY,
        @AttName(ACTIVITY_ATT_TYPE)
        val activityType: String = ""
    ) {
        val isSummary: Boolean get() = activityType == SUMMARY_TYPE
    }
}
