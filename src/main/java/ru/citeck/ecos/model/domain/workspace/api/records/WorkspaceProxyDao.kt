package ru.citeck.ecos.model.domain.workspace.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceAction
import ru.citeck.ecos.model.domain.workspace.service.WorkspacePermissions
import ru.citeck.ecos.model.domain.workspace.service.WorkspaceService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class WorkspaceProxyDao(
    private val workspacePermissions: WorkspacePermissions,
    private val workspaceService: WorkspaceService
) : RecordsDaoProxy(
    WORKSPACE_SOURCE_ID,
    WORKSPACE_REPO_SOURCE_ID
) {

    companion object {
        const val WORKSPACE_SOURCE_ID = "workspace"
        const val WORKSPACE_REPO_SOURCE_ID = "$WORKSPACE_SOURCE_ID-repo"

        const val WORKSPACE_ACTION_ATT = "action"
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        // In one mutation list we can process actions or regular mutation
        val processedJoinActionRecordIds = processJoinAction(records)
        if (processedJoinActionRecordIds.isNotEmpty()) {
            return processedJoinActionRecordIds
        }

        // Check allowing to create new workspace on proxy,
        // because DbPermsComponent perms write not used for creation case
        check(workspacePermissions.currentAuthCanCreateWorkspace()) {
            "Current user has no permissions to mutate workspaces"
        }

        return super.mutate(records)
    }


    private fun processJoinAction(records: List<LocalRecordAtts>): List<String> {
        val processActionsIds = mutableListOf<String>()

        records.forEach { record ->
            val action = record.getAtt(WORKSPACE_ACTION_ATT).asText()
            if (action == WorkspaceAction.JOIN.name && record.id.isNotBlank()) {
                workspaceService.joinCurrentUserToWorkspace(
                    EntityRef.create(
                        AppName.EMODEL,
                        WORKSPACE_SOURCE_ID,
                        record.id
                    )
                )
                processActionsIds.add(record.id)
            }
        }

        return processActionsIds
    }
}
