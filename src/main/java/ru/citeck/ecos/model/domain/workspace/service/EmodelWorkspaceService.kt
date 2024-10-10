package ru.citeck.ecos.model.domain.workspace.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceMemberProxyDao.Companion.WORKSPACE_MEMBER_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_ATT_MEMBER_AUTHORITY
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Service
class EmodelWorkspaceService(
    private val recordsService: RecordsService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi,
    private val authorityService: AuthorityService,
    private val workspacePermissions: WorkspacePermissions
) {

    private fun getUserAuthoritiesRefs(userRef: EntityRef): Set<EntityRef> {
        val authoritiesRefs = authorityService.getAuthoritiesForPerson(userRef.getLocalId()).mapNotNullTo(HashSet()) {
            if (it.startsWith(AuthGroup.PREFIX)) {
                AuthorityType.GROUP.getRef(it.substring(AuthGroup.PREFIX.length))
            } else {
                null
            }
        }
        authoritiesRefs.add(userRef)
        return authoritiesRefs
    }

    fun isUserManagerOf(user: String, workspace: String): Boolean {

        val workspaceRef = EntityRef.create(WORKSPACE_SOURCE_ID, workspace)
        val workspaceData = recordsService.getAtts(workspaceRef, WorkspaceMembersAtts::class.java)
        if (workspaceData.creator.getLocalId() == user) {
            return true
        }
        val managers = workspaceData.workspaceMembers
            ?.filter { it.memberRole == WorkspaceMemberRole.MANAGER }
            ?.mapNotNull { it.authority }

        if (managers.isNullOrEmpty()) {
            return false
        }

        val authorities = getUserAuthoritiesRefs(AuthorityType.PERSON.getRef(user))

        return managers.any { authorities.contains(it) }
    }

    fun getUserWorkspaces(user: String): Set<String> {

        val userRef = AuthorityType.PERSON.getRef(user)
        val authoritiesToQuery = getUserAuthoritiesRefs(userRef)

        return recordsService.query(
            RecordsQuery.create()
                .withSourceId(WORKSPACE_SOURCE_ID)
                .withQuery(
                    Predicates.or(
                        Predicates.eq(RecordConstants.ATT_CREATOR, userRef),
                        Predicates.inVals(WORKSPACE_ATT_MEMBER_AUTHORITY, authoritiesToQuery)
                    )
                )
                .withMaxItems(1000)
                .build(),
        ).getRecords().mapTo(HashSet()) { it.getLocalId() }
    }

    fun getWorkspace(workspace: EntityRef): Workspace {
        return recordsService.getAtts(workspace, Workspace::class.java)
    }

    fun mutateWorkspace(workspace: Workspace): EntityRef {
        val workspaceWithoutMembers = workspace.copy(workspaceMembers = emptyList())

        val createdWorkspace = recordsService.mutate(
            EntityRef.create(AppName.EMODEL, WORKSPACE_SOURCE_ID, ""),
            workspaceWithoutMembers
        )

        workspace.workspaceMembers.forEach { member ->
            addMember(createdWorkspace, member)
        }

        return createdWorkspace
    }

    fun deleteWorkspace(workspace: EntityRef) {
        recordsService.delete(workspace)
    }

    fun joinCurrentUserToWorkspace(workspace: EntityRef) {
        val workspaceInfo = AuthContext.runAsSystem { getWorkspace(workspace) }
        require(workspaceInfo.visibility == WorkspaceVisibility.PUBLIC) {
            "Join non public workspace: $workspace is not allowed"
        }

        val currentUser = AuthContext.getCurrentUser()
        val userAuthorities = AuthContext.getCurrentAuthorities()
        require(workspacePermissions.canJoin(currentUser, userAuthorities)) {
            "User: $currentUser has not allowed to join workspace: $workspace"
        }

        AuthContext.runAsSystem {
            val workspaceMember = WorkspaceMember(
                id = "",
                authority = ecosAuthoritiesApi.getAuthorityRef(currentUser),
                memberRole = WorkspaceMemberRole.USER
            )

            addMember(workspace, workspaceMember)
        }
    }

    fun addMember(workspace: EntityRef, member: WorkspaceMember) {
        val workspaceInfo = getWorkspace(workspace)

        val isNewMember = workspaceInfo.workspaceMembers.none { it.authority == member.authority }
        require(isNewMember) { "Member: ${member.authority} already exists in workspace: $workspace" }

        recordsService.mutate(
            EntityRef.create(AppName.EMODEL, WORKSPACE_MEMBER_SOURCE_ID, ""),
            mapOf(
                "id" to member.id,
                "authority" to member.authority,
                "memberRole" to member.memberRole,
                RecordConstants.ATT_PARENT to workspace,
                RecordConstants.ATT_PARENT_ATT to "workspaceMembers",
            )
        )
    }

    private class WorkspaceMembersAtts(
        @AttName(RecordConstants.ATT_CREATOR + ScalarType.ID_SCHEMA)
        val creator: EntityRef,
        val workspaceMembers: List<MemberInfo>?
    ) {
        class MemberInfo(
            val authority: EntityRef?,
            val memberRole: WorkspaceMemberRole?
        )
    }
}
