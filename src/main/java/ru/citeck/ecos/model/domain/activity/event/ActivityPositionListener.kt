package ru.citeck.ecos.model.domain.activity.event

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

/**
 * Assigns `activity:position` whenever an `activity-atts` aspect record
 * is created or its `activity:parent` assoc changes. The new value is
 * `count(siblings excluding self) + 1`, so the freshly placed record
 * always lands at the end of its (new) parent's ordered list.
 *
 * "Siblings" are records in the same source whose `activity:parent`
 * matches the new parent. When the new parent is empty the record is
 * considered top-level and siblings are scoped by `_workspace`
 * (also restricted to records with no `activity:parent`), so each
 * workspace gets its own top-level numbering.
 *
 * The aspect itself ships a computed `position = 1` with
 * `storingType: ON_EMPTY` (see `aspect/timeline/activity-atts.yml`).
 * This listener overrides that initial 1 with the real count-based
 * position, and re-assigns on every parent move.
 */
@Component
class ActivityPositionListener(
    eventsService: EventsService,
    private val recordsService: RecordsService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val ACTIVITY_ATTS_ASPECT = "activity-atts"
        private const val ACTIVITY_ATT_PARENT = "activity:parent"
        private const val ACTIVITY_ATT_POSITION = "activity:position"

        private const val FILTER_HAS_ACTIVITY_ATTS = "record._aspects._has.$ACTIVITY_ATTS_ASPECT?bool!"
        private const val FILTER_DIFF_HAS_PARENT = "diff._has.$ACTIVITY_ATT_PARENT?bool!"
    }

    init {
        eventsService.addListener<CreatedEvent> {
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(CreatedEvent::class.java)
            withFilter(Predicates.eq(FILTER_HAS_ACTIVITY_ATTS, true))
            withTransactional(true)
            withAction { event -> handleCreated(event) }
        }

        eventsService.addListener<ChangedEvent> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(ChangedEvent::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq(FILTER_HAS_ACTIVITY_ATTS, true),
                    Predicates.eq(FILTER_DIFF_HAS_PARENT, true)
                )
            )
            withTransactional(true)
            withAction { event -> handleChanged(event) }
        }
    }

    private fun handleCreated(event: CreatedEvent) {
        if (event.record.isEmpty()) return
        assignPosition(event.record, event.parent, event.workspace)
    }

    private fun handleChanged(event: ChangedEvent) {
        if (event.record.isEmpty()) return
        // Only fire when the parent assoc actually moved. An empty diff entry
        // could in theory show up if both added and removed lists are empty —
        // skip that no-op case rather than re-stamping the same position.
        val parentDiff = event.diff.firstOrNull { it.id == ACTIVITY_ATT_PARENT } ?: return
        val previous = parentDiff.removed.firstOrNull() ?: EntityRef.EMPTY
        if (previous == event.currentParent) return
        assignPosition(event.record, event.currentParent, event.workspace)
    }

    private fun assignPosition(self: EntityRef, parent: EntityRef, workspace: String) {
        AuthContext.runAsSystem {
            // By the time both create and change events fire the record is
            // already persisted with its new parent, so the sibling query
            // includes self. We use the count as-is — self being the newest
            // sibling, that count is exactly the slot the newcomer should
            // occupy at the end of the ordered list.
            val newPosition = countSiblingsIncludingSelf(self, parent, workspace)
            recordsService.mutate(
                RecordAtts(self).apply { setAtt(ACTIVITY_ATT_POSITION, newPosition) }
            )
            log.debug { "Assigned activity:position=$newPosition to $self under parent=$parent" }
        }
    }

    private fun countSiblingsIncludingSelf(self: EntityRef, parent: EntityRef, workspace: String): Long {
        // Sibling scope: same source as the record itself. Cross-source
        // nesting via activity:parent is not a supported case here — and
        // restricting to the record's own source keeps the query cheap
        // and predictable.
        val predicate = if (parent.isNotEmpty()) {
            Predicates.eq(ACTIVITY_ATT_PARENT, parent.toString())
        } else {
            // Top-level (no parent): numbering is per-workspace, not global
            // across the source. Workspace filter is applied below.
            Predicates.empty(ACTIVITY_ATT_PARENT)
        }
        val workspaces = if (workspace.isNotBlank()) listOf(workspace) else emptyList()
        val query = RecordsQuery.create {
            withSourceId(self.getSourceId())
            withLanguage(PredicateService.LANGUAGE_PREDICATE)
            withQuery(predicate)
            withMaxItems(0)
            withWorkspaces(workspaces)
        }
        return recordsService.query(query).getTotalCount()
    }

    private data class CreatedEvent(
        @AttName("record?id")
        val record: EntityRef = EntityRef.EMPTY,
        @AttName("record.$ACTIVITY_ATT_PARENT?id!''")
        val parent: EntityRef = EntityRef.EMPTY,
        @AttName("record.${RecordConstants.ATT_WORKSPACE}?localId")
        val workspace: String = ""
    )

    private data class ChangedEvent(
        @AttName("record?id")
        val record: EntityRef = EntityRef.EMPTY,
        @AttName("record.$ACTIVITY_ATT_PARENT?id!''")
        val currentParent: EntityRef = EntityRef.EMPTY,
        @AttName("record.${RecordConstants.ATT_WORKSPACE}?localId")
        val workspace: String = "",
        @AttName("diff.list[]?json")
        val diff: List<DiffEntry> = emptyList()
    )

    private data class DiffEntry(
        val id: String = "",
        val added: List<EntityRef> = emptyList(),
        val removed: List<EntityRef> = emptyList()
    )
}
