package ru.citeck.ecos.model.domain.workspace.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.service.CustomWorkspaceApi
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceApi
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceWebApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp

/**
 * Mappings are taken from [CustomWorkspaceApi] and not from the local WorkspaceService: the latter
 * puts its own cache in front of the same data, and a remote application caches the answer anyway.
 * That extra layer only prolongs the lifetime of a mapping which was resolved incorrectly - which is
 * what COREDEV-514 is about - without saving any real work.
 */
@Component
class GetIdsMappingWebExecutor(
    private val workspaceApi: CustomWorkspaceApi
) : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {
        val req = request.getBodyReader().readDto(WorkspaceWebApi.GetIdsMappingReq::class.java)
        val result = workspaceApi.mapIdentifiers(req.ids, WorkspaceApi.IdMappingType.fromId(req.mappingType))
        response.getBodyWriter().writeDto(WorkspaceWebApi.GetIdsMappingResp(result))
    }

    override fun getPath(): String {
        return WorkspaceWebApi.GET_IDS_MAPPING_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
