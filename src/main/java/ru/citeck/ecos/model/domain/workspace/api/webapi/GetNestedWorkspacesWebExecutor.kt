package ru.citeck.ecos.model.domain.workspace.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceWebApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp

@Component
class GetNestedWorkspacesWebExecutor(
    private val workspaceService: WorkspaceService
) : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {
        val req = request.getBodyReader().readDto(WorkspaceWebApi.GetNestedWorkspacesReq::class.java)
        val result = workspaceService.getNestedWorkspaces(req.workspaces.toList())
        response.getBodyWriter().writeDto(WorkspaceWebApi.GetNestedWorkspacesResp(result))
    }

    override fun getPath(): String {
        return WorkspaceWebApi.GET_NESTED_WORKSPACES_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
