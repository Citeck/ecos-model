package ru.citeck.ecos.model.domain.doclib.listener

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.listener.ListenerConfig
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.domain.doclib.desc.DocLibDirDesc
import ru.citeck.ecos.model.domain.doclib.job.DocLibDirPathUpdateJob
import ru.citeck.ecos.model.domain.doclib.service.DocLibDirUtils
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration

@Component
class DocLibDirPathUpdateListener(
    private val eventsService: EventsService,
    private val recordsService: RecordsService,
    private val pathUpdateJob: DocLibDirPathUpdateJob
) {
    @PostConstruct
    fun init() {
        eventsService.addListener(ListenerConfig.create<ChildrenAssocsChangedEvent> {
            withEventType(RecordChangedEvent.TYPE)
            withTransactional(true)
            withDataClass(ChildrenAssocsChangedEvent::class.java)
            withLocal(true)
            withFilter(Predicates.and(
                Predicates.eq("record._type.isSubTypeOf.doclib-directory?bool", true),
                Predicates.eq("diff._has.children?bool", true)
            ))
            withAction { event -> processEvent(event) }
        })
    }

    private fun processEvent(event: ChildrenAssocsChangedEvent) {
        val childrenAssocDiff = event.assocs.find { it.assocId == "children" } ?: return
        val mutAtts = mutableListOf<RecordAtts>()
        val newPath = listOf(*event.dirPath.toTypedArray(), event.ref)
        val newPathHash = DocLibDirUtils.calculatePathHash(newPath)
        childrenAssocDiff.added.forEach {
            val atts = RecordAtts(it)
            atts[DocLibDirDesc.ATT_DIR_PATH] = newPath
            atts[DocLibDirDesc.ATT_DIR_PATH_HASH] = newPathHash
            atts[DocLibDirDesc.ATT_PARENT_DIR_PATH_HASH] = event.dirPathHash
            mutAtts.add(atts)
        }
        AuthContext.runAsSystem {
            recordsService.mutate(mutAtts)
        }
        TxnContext.processSetAfterCommit(this::class, event.typeId) {
            pathUpdateJob.manualUpdateForExactTypes(it, Duration.ofSeconds(10))
        }
    }

    class ChildrenAssocsChangedEvent(
        @AttName("record?id")
        val ref: EntityRef,
        @AttName("typeDef.id!")
        val typeId: String,
        @AttName("assocs[]?json!")
        val assocs: List<AssocsDiff>,
        @AttName("record.dirPath[]?id!")
        val dirPath: List<EntityRef>,
        @AttName("record.dirPathHash?str!")
        val dirPathHash: String
    )

    class AssocsDiff(
        val assocId: String,
        val added: List<EntityRef>
    )
}
