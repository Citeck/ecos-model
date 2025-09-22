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
    private val pathUpdateJob: TreeSearchPathUpdateJob,
    private val treeSearchComponent: TreeSearchComponent
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
                        Predicates.eq("record._aspects._has.${TreeSearchDesc.ASPECT_ID}?bool", true),
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
                        Predicates.eq("record._aspects._has.${TreeSearchDesc.ASPECT_ID}?bool", true),
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
        eventsService.addListener(
            ListenerConfig.create<LeafAssocsChangedEvent> {
                withEventType(RecordChangedEvent.TYPE)
                withTransactional(true)
                withDataClass(LeafAssocsChangedEvent::class.java)
                withFilter(
                    Predicates.and(
                        Predicates.or(
                            treeSearchComponent.treeLeafAspects.map {
                                Predicates.eq("record._aspects._has.$it?bool", true)
                            }
                        ),
                        Predicates.or(
                            treeSearchComponent.treeLeafAssocs.map {
                                Predicates.eq("diff._has.$it?bool", true)
                            }
                        )
                    )
                )
                withAction { event -> processEvent(event, treeSearchComponent.treeLeafAssocs) }
            }
        )
    }

    private fun processEvent(event: LeafAssocsChangedEvent, leafAssocs: Set<String>) {
        val assoc = event.assocs.find { leafAssocs.contains(it.assocId) } ?: return

        val atts = RecordAtts(event.ref)

        if (assoc.added.isNotEmpty()) {
            val newTreeNode = assoc.added[0]
            val treeNodeAtts = recordsService.getAtts(newTreeNode, TreeNodeAtts::class.java)
            val leafPath = listOf(*treeNodeAtts.path.toTypedArray(), newTreeNode)
            atts[TreeSearchDesc.ATT_PATH] = leafPath
            atts[TreeSearchDesc.ATT_PATH_HASH] = TreeSearchDesc.calculatePathHash(leafPath)
            atts[TreeSearchDesc.ATT_PARENT_PATH_HASH] = treeNodeAtts.pathHash
        } else if (assoc.removed.isNotEmpty()) {
            atts[TreeSearchDesc.ATT_PATH] = emptyList<EntityRef>()
            atts[TreeSearchDesc.ATT_PATH_HASH] = TreeSearchDesc.calculatePathHash(emptyList())
            atts[TreeSearchDesc.ATT_PARENT_PATH_HASH] = TreeSearchDesc.calculatePathHash(emptyList())
        }
        AuthContext.runAsSystem {
            recordsService.mutate(atts)
        }
    }

    private fun processEvent(event: ParentChangedEvent) {

        val newPath = listOf(event.parentAfter)

        val atts = RecordAtts(event.ref)
        atts[TreeSearchDesc.ATT_PATH] = newPath
        atts[TreeSearchDesc.ATT_PATH_HASH] = TreeSearchDesc.calculatePathHash(newPath)
        atts[TreeSearchDesc.ATT_PARENT_PATH_HASH] = TreeSearchDesc.calculatePathHash(emptyList())
        atts[TreeSearchDesc.ATT_LEAF_ASSOCS_TO_UPDATE] = treeSearchComponent.getNonEmptyLeafSourceAssocs(event.ref)

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
        val nonEmptyLeafSourceAssocs = treeSearchComponent.getNonEmptyLeafSourceAssocs(childrenAssocDiff.added)
        childrenAssocDiff.added.forEachIndexed { idx, ref ->
            val atts = RecordAtts(ref)
            atts[TreeSearchDesc.ATT_PATH] = newPath
            atts[TreeSearchDesc.ATT_PATH_HASH] = newPathHash
            atts[TreeSearchDesc.ATT_PARENT_PATH_HASH] = event.treePathHash
            atts[TreeSearchDesc.ATT_LEAF_ASSOCS_TO_UPDATE] = nonEmptyLeafSourceAssocs[idx]
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

    class LeafAssocsChangedEvent(
        @AttName("record?id")
        val ref: EntityRef,
        @AttName("typeDef.id!")
        val typeId: String,
        @AttName("assocs[]?json!")
        val assocs: List<AssocsDiff>
    )

    class AssocsDiff(
        val assocId: String,
        val added: List<EntityRef>,
        val removed: List<EntityRef>
    )

    class TreeNodeAtts(
        @AttName("${TreeSearchDesc.ATT_PATH_HASH}!")
        val pathHash: String,
        @AttName("${TreeSearchDesc.ATT_PATH}[]?id!")
        val path: List<EntityRef>
    )
}
