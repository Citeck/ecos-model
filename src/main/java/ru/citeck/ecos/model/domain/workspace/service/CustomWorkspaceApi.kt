package ru.citeck.ecos.model.domain.workspace.service

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceApi
import ru.citeck.ecos.model.lib.workspace.api.WsMembershipType

@Component
class CustomWorkspaceApi(
    private val workspaceService: EmodelWorkspaceService,
    private val modelServiceFactory: ModelServiceFactory
) : WorkspaceApi {

    @PostConstruct
    fun init() {
        modelServiceFactory.setWorkspaceApi(this)
    }

    override fun getNestedWorkspaces(workspaces: Collection<String>): List<Set<String>> {
        return workspaceService.getNestedWorkspaces(workspaces)
    }

    override fun getUserWorkspaces(user: String, membershipType: WsMembershipType): Set<String> {
        return workspaceService.getUserWorkspaces(user, membershipType).workspaces
    }

    override fun isUserManagerOf(user: String, workspace: String): Boolean {
        return workspaceService.isUserManagerOf(user, workspace)
    }

    override fun mapIdentifiers(identifiers: List<String>, mappingType: WorkspaceApi.IdMappingType): List<String> {
        return when (mappingType) {
            WorkspaceApi.IdMappingType.WS_SYS_ID_TO_ID -> identifiers.map {
                workspaceService.getWorkspaceIdBySystemId(it)
            }
            WorkspaceApi.IdMappingType.WS_ID_TO_SYS_ID -> identifiers.map {
                workspaceService.getSystemId(it)
            }
            WorkspaceApi.IdMappingType.NO_MAPPING -> identifiers
        }
    }
}
