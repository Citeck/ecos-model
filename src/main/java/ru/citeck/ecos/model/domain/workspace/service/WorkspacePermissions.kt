package ru.citeck.ecos.model.domain.workspace.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi

@Component
class WorkspacePermissions(
    private val recordsService: RecordsService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi
) {

    companion object {
        private val blockJoinForRoles = setOf(AuthRole.ANONYMOUS, AuthRole.GUEST, AuthRole.SYSTEM)
        private val blockJoinForUsers = setOf(AuthUser.GUEST, AuthUser.ANONYMOUS, AuthUser.SYSTEM)
    }

    fun allowRead(user: String, userAuthorities: Set<String>, record: Any): Boolean {
        val workspace = recordsService.getAtts(record, WorkspaceInfo::class.java)
        return allowReadForWorkspace(user, userAuthorities, workspace)
    }

    private fun allowReadForWorkspace(user: String, userAuthorities: Set<String>, workspace: WorkspaceInfo): Boolean {
        if (workspace.creator == user ||
            userAuthorities.contains(AuthRole.ADMIN) ||
            userAuthorities.contains(AuthRole.SYSTEM)
        ) {
            return true
        }

        if (userAuthorities.contains(AuthRole.GUEST) ||
            userAuthorities.contains(AuthRole.ANONYMOUS) ||
            userAuthorities.none { it == AuthRole.USER }
        ) {
            return false
        }

        val allowedWorkspaceAuthorityNames = workspace.members.map {
            ecosAuthoritiesApi.getAuthorityName(it.authority)
        }

        return workspace.visibility == WorkspaceVisibility.PUBLIC ||
            allowedWorkspaceAuthorityNames.any {
                userAuthorities.contains(it)
            }
    }

    fun allowWrite(user: String, userAuthorities: Set<String>, record: Any): Boolean {
        val workspace = recordsService.getAtts(record, WorkspaceInfo::class.java)
        val readAllowed = allowReadForWorkspace(user, userAuthorities, workspace)
        if (!readAllowed) {
            return false
        }

        val authorityNameByRoles = workspace.members.map {
            ecosAuthoritiesApi.getAuthorityName(it.authority) to it.memberRole
        }

        return userAuthorities.contains(AuthRole.ADMIN) ||
            userAuthorities.contains(AuthRole.SYSTEM) ||
            workspace.creator == user ||
            authorityNameByRoles.any {
                val (authorityName, role) = it
                role == WorkspaceMemberRole.MANAGER && userAuthorities.contains(authorityName)
            }
    }

    fun currentAuthCanCreateWorkspace(): Boolean {
        val currentAuthorities = AuthContext.getCurrentAuthorities()
        return currentAuthorities.none { it == AuthRole.ANONYMOUS || it == AuthRole.GUEST }
    }

    fun canJoin(user: String, authorities: Collection<String>): Boolean {
        return user.isNotBlank() &&
            authorities.contains(AuthRole.USER) &&
            authorities.none { blockJoinForRoles.contains(it) } &&
            user !in blockJoinForUsers
    }
}

private data class WorkspaceInfo(
    val visibility: WorkspaceVisibility,

    @AttName("workspaceMembers")
    val members: List<WorkspaceMember> = emptyList(),

    @AttName(RecordConstants.ATT_CREATOR + ScalarType.LOCAL_ID_SCHEMA)
    val creator: String
)