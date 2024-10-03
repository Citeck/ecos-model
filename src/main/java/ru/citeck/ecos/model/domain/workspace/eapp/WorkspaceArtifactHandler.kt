package ru.citeck.ecos.model.domain.workspace.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.config.WORKSPACE_TYPE
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
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
            workspaceService.mutateWorkspace(artifact)
        }
    }

    override fun listenChanges(listener: Consumer<Workspace>) {
        listOf(RecordChangedEvent.TYPE, RecordCreatedEvent.TYPE).forEach { eventType ->
            eventsService.addListener<Workspace> {
                withEventType(eventType)
                withDataClass(Workspace::class.java)
                withFilter(Predicates.eq("typeDef.id", WORKSPACE_TYPE))
                withAction {
                    listener.accept(it)
                }
            }
        }
    }

    override fun deleteArtifact(artifactId: String) {
        AuthContext.runAsSystem {
            recordsService.delete(EntityRef.create(AppName.EMODEL, WORKSPACE_SOURCE_ID, artifactId))
        }
    }

    override fun getArtifactType(): String {
        return "model/${WORKSPACE_TYPE}"
    }
}
