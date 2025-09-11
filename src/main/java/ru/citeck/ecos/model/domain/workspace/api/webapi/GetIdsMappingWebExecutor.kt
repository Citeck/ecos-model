package ru.citeck.ecos.model.domain.workspace.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceApi
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceWebApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp

@Component
class GetIdsMappingWebExecutor(
    private val workspaceService: WorkspaceService
) : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {
        val req = request.getBodyReader().readDto(WorkspaceWebApi.GetIdsMappingReq::class.java)
        val result = when (WorkspaceApi.IdMappingType.fromId(req.mappingType)) {
            WorkspaceApi.IdMappingType.WS_ID_TO_SYS_ID -> workspaceService.getWorkspaceSystemId(req.ids)
            WorkspaceApi.IdMappingType.WS_SYS_ID_TO_ID -> workspaceService.getWorkspaceIdBySystemId(req.ids)
            WorkspaceApi.IdMappingType.NO_MAPPING -> req.ids
        }
        response.getBodyWriter().writeDto(WorkspaceWebApi.GetIdsMappingResp(result))
    }

    override fun getPath(): String {
        return WorkspaceWebApi.GET_IDS_MAPPING_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
