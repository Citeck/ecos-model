package ru.citeck.ecos.model.domain.workspace.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_ATT_MEMBER_AUTHORITY
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceVisitDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
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

        if (workspace.startsWith(USER_WORKSPACE_PREFIX)) {
            return user == workspace.substring(USER_WORKSPACE_PREFIX.length) ||
                authorityService.isAdmin(user)
        }

        val workspaceRef = EntityRef.create(WorkspaceDesc.SOURCE_ID, workspace)
        val workspaceData = recordsService.getAtts(workspaceRef, WorkspaceMembersAtts::class.java)
        if (workspaceData.creator.getLocalId() == user) {
            return true
        }
        val managers = workspaceData.workspaceMembers
            ?.filter { it.memberRole == WorkspaceMemberRole.MANAGER }
            ?.flatMapTo(HashSet()) { it.authorities ?: emptyList() }

        if (managers.isNullOrEmpty()) {
            return false
        }

        val authorities = getUserAuthoritiesRefs(AuthorityType.PERSON.getRef(user))

        return managers.any { authorities.contains(it) }
    }

    fun getUserWorkspaces(
        user: String,
        filter: Predicate = Predicates.alwaysTrue(),
        includePersonal: Boolean = true,
        maxItems: Int = 1000
    ): Set<String> {

        val userRef = AuthorityType.PERSON.getRef(user)
        val authoritiesToQuery = getUserAuthoritiesRefs(userRef)

        var queryPredicate: Predicate = Predicates.or(
            Predicates.eq(RecordConstants.ATT_CREATOR, userRef),
            Predicates.inVals(WORKSPACE_ATT_MEMBER_AUTHORITY, authoritiesToQuery)
        )
        if (!PredicateUtils.isAlwaysTrue(filter)) {
            queryPredicate = Predicates.and(queryPredicate, filter)
        }

        val workspaces = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceDesc.SOURCE_ID)
                .withQuery(queryPredicate)
                .withMaxItems(maxItems)
                .build(),
        ).getRecords().mapTo(LinkedHashSet()) { it.getLocalId() }

        if (includePersonal && workspaces.size < maxItems) {
            workspaces.add("$USER_WORKSPACE_PREFIX$user")
        }
        val orders = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceVisitDesc.SOURCE_ID)
                .withQuery(
                    Predicates.and(
                        Predicates.eq(WorkspaceVisitDesc.ATT_USER, userRef),
                        Predicates.inVals(WorkspaceVisitDesc.ATT_WORKSPACE, workspaces),
                    )
                )
                .withSortBy(
                    listOf(
                        SortBy(WorkspaceVisitDesc.ATT_VISITS_COUNT, false),
                        SortBy(WorkspaceVisitDesc.ATT_LAST_VISIT_TIME, false),
                    )
                ).build(),
            WorkspaceVisitAtts::class.java
        ).getRecords()

        val result = LinkedHashSet<String>()
        orders.forEach {
            result.add(it.workspace)
            workspaces.remove(it.workspace)
        }
        workspaces.forEach {
            result.add(it)
        }
        return result
    }

    fun getWorkspace(workspace: EntityRef): Workspace {
        return recordsService.getAtts(workspace, Workspace::class.java)
    }

    fun mutateWorkspace(workspace: Workspace): EntityRef {
        val workspaceWithoutMembers = workspace.copy(workspaceMembers = emptyList())

        val createdWorkspace = recordsService.mutate(
            EntityRef.create(AppName.EMODEL, WorkspaceDesc.SOURCE_ID, ""),
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
                memberId = "user-join-$currentUser",
                authorities = listOf(ecosAuthoritiesApi.getAuthorityRef(currentUser)),
                memberRole = WorkspaceMemberRole.USER
            )

            addMember(workspace, workspaceMember)
        }
    }

    fun addMember(workspace: EntityRef, memberToAdd: WorkspaceMember) {
        val workspaceInfo = getWorkspace(workspace)

        val existentAuthorities = hashSetOf<EntityRef>()
        for (wsMember in workspaceInfo.workspaceMembers) {
            for (wsMemberAuthority in wsMember.authorities) {
                if (memberToAdd.authorities.contains(wsMemberAuthority)) {
                    existentAuthorities.add(wsMemberAuthority)
                }
            }
        }
        require(existentAuthorities.isEmpty()) {
            "Members: ${existentAuthorities.joinToString()} already exists in workspace: $workspace"
        }

        recordsService.mutate(
            WorkspaceMemberDesc.getRef(""),
            mapOf(
                WorkspaceMemberDesc.ATT_MEMBER_ID to memberToAdd.memberId,
                WorkspaceMemberDesc.ATT_AUTHORITIES to memberToAdd.authorities,
                WorkspaceMemberDesc.ATT_MEMBER_ROLE to memberToAdd.memberRole,
                RecordConstants.ATT_PARENT to workspace,
                RecordConstants.ATT_PARENT_ATT to "workspaceMembers",
            )
        )
    }

    private class WorkspaceVisitAtts(
        val workspace: String
    )

    private class WorkspaceMembersAtts(
        @AttName(RecordConstants.ATT_CREATOR + ScalarType.ID_SCHEMA)
        val creator: EntityRef,
        val workspaceMembers: List<MemberInfo>?
    ) {
        class MemberInfo(
            val authorities: List<EntityRef>?,
            val memberRole: WorkspaceMemberRole?
        )
    }
}
