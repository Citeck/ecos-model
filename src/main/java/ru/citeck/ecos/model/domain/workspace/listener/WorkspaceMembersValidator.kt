package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.records2.predicate.model.AndPredicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.txn.lib.TxnContext

@Component
class WorkspaceMembersValidator(
    eventsService: EventsService,
    private val workspaceService: EmodelWorkspaceService
) {
    companion object {
        private val TXN_KEY_WORKSPACES_TO_CHECK_MANAGERS = Any()
    }

    init {
        val commonWsMemberPredicate = AndPredicate.of(
            Predicates.eq("typeDef.id", WorkspaceMemberDesc.TYPE_ID),
            Predicates.notEq(WorkspaceDesc.CTX_ATT_DEPLOY_WORKSPACE_BOOL, true)
        )

        listOf(RecordChangedEvent.TYPE, RecordDeletedEvent.TYPE).forEach { eventType ->
            eventsService.addListener<WorkspaceChangedAtts> {
                withTransactional(true)
                withEventType(eventType)
                withDataClass(WorkspaceChangedAtts::class.java)
                withFilter(commonWsMemberPredicate)
                withAction { validateWorkspaceManagers(it.workspaceId) }
            }
        }
    }

    private fun validateWorkspaceManagers(workspace: String) {
        if (workspace.isBlank()) {
            return
        }
        val txn = TxnContext.getTxnOrNull()
        if (txn == null) {
            validateWorkspacesManagersImpl(listOf(workspace))
            return
        }
        val workspaces = txn.getData(TXN_KEY_WORKSPACES_TO_CHECK_MANAGERS) { HashSet<String>() }
        workspaces.add(workspace)
        if (workspaces.size == 1) {
            TxnContext.doBeforeCommit(0f) {
                validateWorkspacesManagersImpl(workspaces)
            }
        }
    }

    private fun validateWorkspacesManagersImpl(workspaces: Collection<String>) {
        for (workspace in workspaces) {
            if (workspaceService.getWorkspaceManagersRefs(workspace).isEmpty()) {
                error(
                    "Workspace '$workspace' must have at least one manager. " +
                        "If you want to remove all managers, consider deleting the workspace instead."
                )
            }
        }
    }

    private class WorkspaceChangedAtts(
        @AttName("record._parent?localId!")
        val workspaceId: String
    )
}
