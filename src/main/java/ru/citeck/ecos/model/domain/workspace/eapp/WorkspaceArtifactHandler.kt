package ru.citeck.ecos.model.domain.workspace.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class WorkspaceArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService,
    private val workspaceService: EmodelWorkspaceService
) : EcosArtifactHandler<Workspace> {

    override fun deployArtifact(artifact: Workspace) {
        AuthContext.runAsSystem {
            workspaceService.deployWorkspace(artifact)
        }
    }

    override fun listenChanges(listener: Consumer<Workspace>) {
        listOf(RecordChangedEvent.TYPE, RecordCreatedEvent.TYPE).forEach { eventType ->
            eventsService.addListener<EventAtts> {
                withEventType(eventType)
                withDataClass(EventAtts::class.java)
                withFilter(Predicates.eq("typeDef.id", WorkspaceDesc.TYPE_ID))
                withAction {
                    val workspace = if (it.defaultMembers != null) {
                        it.record.copy().withWorkspaceMembers(it.defaultMembers).build()
                    } else {
                        it.record
                    }
                    listener.accept(workspace)
                }
            }
        }
    }

    override fun deleteArtifact(artifactId: String) {
        AuthContext.runAsSystem {
            recordsService.delete(EntityRef.create(AppName.EMODEL, WorkspaceDesc.SOURCE_ID, artifactId))
        }
    }

    override fun getArtifactType(): String {
        return "model/${WorkspaceDesc.TYPE_ID}"
    }

    class EventAtts(
        val record: Workspace,
        @AttName("record.defaultWorkspaceMembers[]?json")
        val defaultMembers: List<WorkspaceMember>? = null
    )
}
