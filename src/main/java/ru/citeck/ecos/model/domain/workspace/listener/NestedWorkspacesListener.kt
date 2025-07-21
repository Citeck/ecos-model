package ru.citeck.ecos.model.domain.workspace.listener

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName

@Component
class NestedWorkspacesListener(
    val workspaceService: WorkspaceService,
    val eventsService: EventsService
) {

    @PostConstruct
    fun init() {

        eventsService.addListener<NestedWorkspacesChanged> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(NestedWorkspacesChanged::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", "workspace"),
                    Predicates.eq("diff._has.${WorkspaceDesc.ATT_NESTED_WORKSPACES}?bool", true)
                )
            )
            withExclusive(false)
            withAction { workspaceService.resetNestedWorkspacesCache(listOf(it.workspaceId)) }
        }
    }

    private data class NestedWorkspacesChanged(
        @AttName("record?localId")
        val workspaceId: String
    )
}
