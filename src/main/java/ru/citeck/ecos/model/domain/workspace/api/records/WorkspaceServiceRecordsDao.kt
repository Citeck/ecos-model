package ru.citeck.ecos.model.domain.workspace.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class WorkspaceServiceRecordsDao(
    private val workspaceService: EmodelWorkspaceService
) : ValueMutateDao<WorkspaceServiceRecordsDao.ActionData> {

    companion object {
        const val ID = "workspace-service"
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(value: ActionData): Any? {
        val workspaceId = value.workspace.getLocalId()
        when (value.type) {
            ActionType.JOIN -> {
                workspaceService.joinCurrentUserToWorkspace(workspaceId)
            }
            ActionType.LEAVE -> {
                workspaceService.leaveWorkspaceForCurrentUser(workspaceId)
            }
        }
        return DataValue.createObj()
    }

    class ActionData(
        val type: ActionType,
        val workspace: EntityRef
    )

    enum class ActionType {
        JOIN, LEAVE
    }
}
