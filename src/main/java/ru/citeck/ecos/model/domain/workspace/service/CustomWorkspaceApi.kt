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

    override fun getUserWorkspaces(user: String, membershipType: WsMembershipType): Set<String> {
        return workspaceService.getUserWorkspaces(user, membershipType).workspaces
    }

    override fun isUserManagerOf(user: String, workspace: String): Boolean {
        return workspaceService.isUserManagerOf(user, workspace)
    }
}
