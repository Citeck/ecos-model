package ru.citeck.ecos.model.domain.workspace.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi

@Component
class WorkspacePermissions(
    private val recordsService: RecordsService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi
) {

    companion object {
        private val blockJoinForRoles = setOf(AuthRole.ANONYMOUS, AuthRole.GUEST, AuthRole.SYSTEM)
        private val blockJoinForUsers = setOf(AuthUser.GUEST, AuthUser.ANONYMOUS, AuthUser.SYSTEM)

        private val TXN_PERMS_KEY = Any()
    }

    fun allowRead(user: String, userAuthorities: Set<String>, record: Any): Boolean {
        val workspace = recordsService.getAtts(record, WorkspaceInfo::class.java)
        return allowReadForWorkspace(user, userAuthorities, workspace)
    }

    private fun allowReadForWorkspace(user: String, userAuthorities: Set<String>, workspace: WorkspaceInfo): Boolean {
        if (userAuthorities.contains(AuthRole.ADMIN) ||
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
        if (workspace.visibility == WorkspaceVisibility.PUBLIC) {
            return true
        }

        return hasPermissionWithTxnCaching(
            PermsTxnKey(user, userAuthorities, workspace.id, "READ")
        ) {
            val allowedWorkspaceAuthorityRefs = workspace.members.flatMapTo(HashSet()) { it.authorities }.toList()
            val allowedWorkspaceAuthorityNames = ecosAuthoritiesApi.getAuthorityNames(allowedWorkspaceAuthorityRefs)

            allowedWorkspaceAuthorityNames.contains(user) ||
                userAuthorities.any { allowedWorkspaceAuthorityNames.contains(it) }
        }
    }

    fun currentAuthCanReadPersonalWorkspaceOf(user: String): Boolean {
        val currentUser = AuthContext.getCurrentUser()
        return AuthContext.isRunAsSystemOrAdmin() || currentUser == user
    }

    fun allowWrite(user: String, userAuthorities: Set<String>, record: Any): Boolean {

        val workspace = recordsService.getAtts(record, WorkspaceInfo::class.java)
        val readAllowed = allowReadForWorkspace(user, userAuthorities, workspace)
        if (!readAllowed) {
            return false
        }

        return hasPermissionWithTxnCaching(
            PermsTxnKey(user, userAuthorities, workspace.id, "WRITE")
        ) {
            val authorityNameByRoles = ArrayList<Pair<String, WorkspaceMemberRole>>()
            for (member in workspace.members) {
                val authNames = ecosAuthoritiesApi.getAuthorityNames(member.authorities)
                for (authName in authNames) {
                    authorityNameByRoles.add(authName to member.memberRole)
                }
            }

            userAuthorities.contains(AuthRole.ADMIN) ||
                userAuthorities.contains(AuthRole.SYSTEM) ||
                authorityNameByRoles.any {
                    val (authorityName, role) = it
                    role == WorkspaceMemberRole.MANAGER && userAuthorities.contains(authorityName)
                }
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

    /**
     * Caching is used to preserve permissions temporarily during a transaction.
     * Some operations may revoke permissions, but the user should retain them
     * until the transaction is completed.
     */
    private inline fun hasPermissionWithTxnCaching(key: PermsTxnKey, calculate: () -> Boolean): Boolean {
        val txn = TxnContext.getTxnOrNull() ?: return calculate.invoke()
        val cache = txn.getData(TXN_PERMS_KEY) { HashSet<PermsTxnKey>() }
        if (cache.contains(key)) {
            return true
        }
        val hasPerms = calculate.invoke()
        if (hasPerms) {
            cache.add(key)
        }
        return hasPerms
    }

    private data class PermsTxnKey(
        val user: String,
        val authorities: Set<String>,
        val workspaceId: String,
        val permission: String
    )
}

private data class WorkspaceInfo(
    val id: String,
    val visibility: WorkspaceVisibility,

    @AttName("workspaceMembers")
    val members: List<WorkspaceMember> = emptyList()
)
