package ru.citeck.ecos.model.domain.workspace.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.service.CustomWorkspaceApi
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceWebApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp

@Component
class GetWorkspaceSystemIdWebExecutor(
    private val workspaceService: WorkspaceService
) : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {
        val req = request.getBodyReader().readDto(WorkspaceWebApi.GetWorkspaceSystemIdReq::class.java)
        val result = workspaceService.getWorkspaceSystemId(req.workspaces)
        response.getBodyWriter().writeDto(WorkspaceWebApi.GetWorkspaceSystemIdResp(result))
    }

    override fun getPath(): String {
        return WorkspaceWebApi.IS_USER_MANAGER_OF_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
