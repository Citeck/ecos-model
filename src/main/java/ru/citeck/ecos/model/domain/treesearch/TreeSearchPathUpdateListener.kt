package ru.citeck.ecos.model.domain.treesearch

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.listener.ListenerConfig
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration

@Component
class TreeSearchPathUpdateListener(
    private val eventsService: EventsService,
    private val recordsService: RecordsService,
    private val pathUpdateJob: TreeSearchPathUpdateJob
) {
    @PostConstruct
    fun init() {
        eventsService.addListener(
            ListenerConfig.create<ChildrenAssocsChangedEvent> {
                withEventType(RecordChangedEvent.TYPE)
                withTransactional(true)
                withDataClass(ChildrenAssocsChangedEvent::class.java)
                withFilter(
                    Predicates.and(
                        Predicates.eq("record._aspects._has.tree-search?bool", true),
                        Predicates.eq("diff._has.children?bool", true)
                    )
                )
                withAction { event -> processEvent(event) }
            }
        )
        eventsService.addListener(
            ListenerConfig.create<ParentChangedEvent> {
                withEventType("record-parent-changed")
                withTransactional(true)
                withDataClass(ParentChangedEvent::class.java)
                withFilter(
                    Predicates.and(
                        Predicates.eq("record._aspects._has.tree-search?bool", true),
                        Predicates.eq("diff._has._parent?bool", true)
                    )
                )
                withAction { event ->
                    // Process only events whose source (appName/sourceId) differs from the current ref.
                    // Other cases are handled in ChildrenAssocsChangedEvent above.
                    if (event.ref.withoutLocalId() != event.parentAfter.withoutLocalId()) {
                        processEvent(event)
                    }
                 }
            }
        )
    }

    private fun processEvent(event: ParentChangedEvent) {

        val newPath = listOf(event.parentAfter)

        val atts = RecordAtts(event.ref)
        atts[TreeSearchDesc.ATT_PATH] = newPath
        atts[TreeSearchDesc.ATT_PATH_HASH] = TreeSearchDesc.calculatePathHash(newPath)
        atts[TreeSearchDesc.ATT_PARENT_PATH_HASH] = TreeSearchDesc.calculatePathHash(emptyList())

        AuthContext.runAsSystem {
            recordsService.mutate(atts)
        }
        TxnContext.processSetAfterCommit(this::class, event.typeId) {
            pathUpdateJob.manualUpdateForExactTypes(it, Duration.ofSeconds(5))
        }
    }

    private fun processEvent(event: ChildrenAssocsChangedEvent) {
        val childrenAssocDiff = event.assocs.find { it.assocId == "children" } ?: return
        val mutAtts = mutableListOf<RecordAtts>()
        val newPath = listOf(*event.treePath.toTypedArray(), event.ref)
        val newPathHash = TreeSearchDesc.calculatePathHash(newPath)
        childrenAssocDiff.added.forEach {
            val atts = RecordAtts(it)
            atts[TreeSearchDesc.ATT_PATH] = newPath
            atts[TreeSearchDesc.ATT_PATH_HASH] = newPathHash
            atts[TreeSearchDesc.ATT_PARENT_PATH_HASH] = event.treePathHash
            mutAtts.add(atts)
        }
        AuthContext.runAsSystem {
            recordsService.mutate(mutAtts)
        }
        TxnContext.processSetAfterCommit(this::class, event.typeId) {
            pathUpdateJob.manualUpdateForExactTypes(it, Duration.ofSeconds(5))
        }
    }

    class ChildrenAssocsChangedEvent(
        @AttName("record?id")
        val ref: EntityRef,
        @AttName("typeDef.id!")
        val typeId: String,
        @AttName("assocs[]?json!")
        val assocs: List<AssocsDiff>,
        @AttName("record.${TreeSearchDesc.ATT_PATH}[]?id!")
        val treePath: List<EntityRef>,
        @AttName("record.${TreeSearchDesc.ATT_PATH_HASH}?str!")
        val treePathHash: String
    )

    class ParentChangedEvent(
        @AttName("record?id")
        val ref: EntityRef,
        @AttName("typeDef.id!")
        val typeId: String,
        @AttName("parentAfter?id!")
        val parentAfter: EntityRef
    )

    class AssocsDiff(
        val assocId: String,
        val added: List<EntityRef>
    )
}
