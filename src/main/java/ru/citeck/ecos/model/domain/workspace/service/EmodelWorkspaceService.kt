package ru.citeck.ecos.model.domain.workspace.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
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
import ru.citeck.ecos.model.lib.workspace.api.WsMembershipType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Service
class EmodelWorkspaceService(
    private val recordsService: RecordsService,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi,
    private val authorityService: AuthorityService,
    private val workspacePermissions: WorkspacePermissions
) {

    companion object {
        const val USER_JOIN_PREFIX = "user-join-"

        private val log = KotlinLogging.logger {}
    }

    private fun getUserAuthoritiesRefs(userRef: EntityRef, withUserRef: Boolean = true): Set<EntityRef> {
        val authoritiesRefs = authorityService.getAuthoritiesForPerson(userRef.getLocalId()).mapNotNullTo(HashSet()) {
            if (it.startsWith(AuthGroup.PREFIX)) {
                AuthorityType.GROUP.getRef(it.substring(AuthGroup.PREFIX.length))
            } else {
                null
            }
        }
        if (withUserRef) {
            authoritiesRefs.add(userRef)
        } else {
            authoritiesRefs.remove(userRef)
        }
        return authoritiesRefs
    }

    fun isUserManagerOf(user: String, workspace: String): Boolean {

        if (workspace.startsWith(USER_WORKSPACE_PREFIX)) {
            return user == workspace.substring(USER_WORKSPACE_PREFIX.length) ||
                authorityService.isAdmin(user)
        }

        val workspaceRef = EntityRef.create(WorkspaceDesc.SOURCE_ID, workspace)
        val workspaceData = recordsService.getAtts(workspaceRef, WorkspaceMembersAtts::class.java)

        val managers = workspaceData.workspaceMembers
            ?.filter { it.memberRole == WorkspaceMemberRole.MANAGER }
            ?.flatMapTo(HashSet()) { it.authorities ?: emptyList() }

        if (managers.isNullOrEmpty()) {
            return false
        }

        val authorities = getUserAuthoritiesRefs(AuthorityType.PERSON.getRef(user))

        return managers.any { authorities.contains(it) }
    }

    /**
     * Retrieves references to managers associated with the specified workspace.
     *
     * @param workspaceId ID of the workspace.
     * @return a set of manager references, or `null` if the workspace does not exist.
     */
    fun getWorkspaceManagersRefs(workspaceId: String): Set<EntityRef>? {

        if (workspaceId.startsWith(USER_WORKSPACE_PREFIX)) {
            return setOf(
                AuthorityType.PERSON.getRef(workspaceId.substring(USER_WORKSPACE_PREFIX.length))
            )
        }

        val workspaceRef = EntityRef.create(WorkspaceDesc.SOURCE_ID, workspaceId)
        val workspaceData = recordsService.getAtts(workspaceRef, WorkspaceMembersAtts::class.java)
        if (workspaceData.notExists) {
            return null
        }

        val managers = LinkedHashSet<EntityRef>()

        workspaceData.workspaceMembers ?: return emptySet()
        for (member in workspaceData.workspaceMembers) {
            if (member.memberRole != WorkspaceMemberRole.MANAGER) {
                continue
            }
            member.authorities?.let { managers.addAll(it) }
        }

        return managers
    }

    fun getUserWorkspaces(
        user: String,
        membershipType: WsMembershipType = WsMembershipType.ALL,
        filter: Predicate = Predicates.alwaysTrue(),
        includePersonal: Boolean? = null,
        maxItems: Int = 1000
    ): UserWorkspaces {

        val userRef = AuthorityType.PERSON.getRef(user)
        val membersPredicate = when (membershipType) {
            WsMembershipType.ALL -> {
                Predicates.inVals(WORKSPACE_ATT_MEMBER_AUTHORITY, getUserAuthoritiesRefs(userRef, true))
            }
            WsMembershipType.INDIRECT -> {
                Predicates.inVals(WORKSPACE_ATT_MEMBER_AUTHORITY, getUserAuthoritiesRefs(userRef, false))
            }
            WsMembershipType.DIRECT -> {
                Predicates.and(
                    Predicates.eq(WORKSPACE_ATT_MEMBER_AUTHORITY, userRef),
                    Predicates.not(
                        Predicates.inVals(
                            WORKSPACE_ATT_MEMBER_AUTHORITY,
                            getUserAuthoritiesRefs(userRef, false)
                        )
                    )
                )
            }
        }

        var queryPredicate: Predicate = membersPredicate
        if (!PredicateUtils.isAlwaysTrue(filter)) {
            queryPredicate = Predicates.and(queryPredicate, filter)
        }

        var totalCount = 0L
        val workspaces = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceDesc.SOURCE_ID)
                .withQuery(queryPredicate)
                .withMaxItems(maxItems)
                .build(),
        ).run {
            totalCount += getTotalCount()
            getRecords().mapTo(LinkedHashSet()) { it.getLocalId() }
        }

        val nnIncludePersonal = includePersonal ?: (membershipType == WsMembershipType.ALL)
        if (nnIncludePersonal && workspaces.size < maxItems) {
            workspaces.add("$USER_WORKSPACE_PREFIX$user")
            totalCount += 1
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
        return UserWorkspaces(result, totalCount)
    }

    fun getAllUsersFromWorkspaces(workspaces: List<String>): Set<EntityRef> {
        val allUsersFromWorkspaces = mutableSetOf<EntityRef>()
        val groups = mutableSetOf<EntityRef>()
        workspaces.forEach { workspace ->
            val workspaceRef = EntityRef.create(WorkspaceDesc.SOURCE_ID, workspace)
            val workspaceData = recordsService.getAtts(workspaceRef, WorkspaceMembersAtts::class.java)
            if (!workspaceData.notExists) {
                workspaceData.workspaceMembers?.forEach {
                    it.authorities?.forEach { authority ->
                        if (authority.getSourceId() == AuthorityGroupConstants.TYPE_ID) {
                            groups.add(authority)
                        } else {
                            allUsersFromWorkspaces.add(authority)
                        }
                    }
                }
            }
        }

        val usersFromGroup = recordsService.getAtts(groups, GroupInfo::class.java)
            .map { it.containedUsers }
            .flatten()
        allUsersFromWorkspaces.addAll(usersFromGroup)
        return allUsersFromWorkspaces
    }

    fun getWorkspace(workspaceId: String): Workspace {
        return getWorkspace(WorkspaceDesc.getRef(workspaceId))
    }

    fun getWorkspace(workspace: EntityRef): Workspace {
        return recordsService.getAtts(workspace, Workspace::class.java)
    }

    fun deployWorkspace(workspace: Workspace): String {
        return RequestContext.doWithCtx({
            it.withCtxAtts(mapOf(WorkspaceDesc.CTX_ATT_DEPLOY_WORKSPACE to true))
        }) {
            deployWorkspaceImpl(workspace).getLocalId()
        }
    }

    private fun deployWorkspaceImpl(workspace: Workspace): EntityRef {

        if (workspace.id.isBlank()) {
            error("Workspace ID must be non-blank")
        }

        val workspaceAttsToMutate = ObjectData.create(workspace)
        if (!workspace.system) {
            workspaceAttsToMutate.remove(WorkspaceDesc.ATT_SYSTEM)
        }

        val isWsExists = recordsService.getAtt(
            WorkspaceDesc.getRef(workspace.id),
            RecordConstants.ATT_NOT_EXISTS + "?bool"
        ).asBoolean().not()

        workspaceAttsToMutate[WorkspaceDesc.ATT_DEFAULT_WORKSPACE_MEMBERS] = Json.mapper.toNonDefaultJson(
            workspace.workspaceMembers,
            WorkspaceMember::class.java
        )

        return if (isWsExists) {
            workspaceAttsToMutate.remove(WorkspaceDesc.ATT_WORKSPACE_MEMBERS)
            recordsService.mutate(WorkspaceDesc.getRef(workspace.id), workspaceAttsToMutate)
        } else {
            workspaceAttsToMutate[WorkspaceDesc.ATT_WORKSPACE_MEMBERS] = DataValue.createArr()
            val createdWsRef = recordsService.mutate(WorkspaceDesc.getRef(""), workspaceAttsToMutate)
            workspace.workspaceMembers.forEach { member ->
                addMember(createdWsRef.getLocalId(), member)
            }
            createdWsRef
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        recordsService.delete(WorkspaceDesc.getRef(workspaceId))
    }

    fun leaveWorkspaceForCurrentUser(workspaceId: String) {
        AuthContext.runAsSystem { leaveWorkspaceForCurrentUserImpl(workspaceId) }
    }

    private fun leaveWorkspaceForCurrentUserImpl(workspaceId: String) {

        val currentUserRef = AuthorityType.PERSON.getRef(AuthContext.getCurrentUser())

        val wsManagers = getWorkspaceManagersRefs(workspaceId) ?: emptySet()
        if (wsManagers.size == 1 && wsManagers.contains(currentUserRef)) {
            error("You can't leave workspace when you are last manager")
        }

        class MembersAtts(
            val id: EntityRef,
            val authorities: List<EntityRef>
        )

        val membersToUpdate = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceMemberDesc.SOURCE_ID)
                .withMaxItems(10000)
                .withQuery(
                    Predicates.and(
                        Predicates.eq(RecordConstants.ATT_PARENT, WorkspaceDesc.getRef(workspaceId)),
                        Predicates.contains(WorkspaceMemberDesc.ATT_AUTHORITIES, currentUserRef.toString())
                    )
                ).build(),
            MembersAtts::class.java
        ).getRecords()

        if (membersToUpdate.isEmpty()) {
            log.debug { "User ${currentUserRef.getLocalId()} is not member of workspace '$workspaceId'" }
            return
        }

        for (member in membersToUpdate) {
            if (member.authorities.contains(currentUserRef)) {
                if (member.authorities.size == 1) {
                    recordsService.delete(member.id)
                } else {
                    recordsService.mutateAtt(
                        member.id,
                        "att_rem_${WorkspaceMemberDesc.ATT_AUTHORITIES}",
                        currentUserRef
                    )
                }
            }
        }
    }

    fun joinCurrentUserToWorkspace(workspaceId: String) {
        val workspaceInfo = AuthContext.runAsSystem { getWorkspace(workspaceId) }
        require(workspaceInfo.visibility == WorkspaceVisibility.PUBLIC) {
            "Join non public workspace: '$workspaceId' is not allowed"
        }

        val currentUser = AuthContext.getCurrentUser()
        val userAuthorities = AuthContext.getCurrentAuthorities()
        require(workspacePermissions.canJoin(currentUser, userAuthorities)) {
            "User: $currentUser has not allowed to join workspace: '$workspaceId'"
        }

        AuthContext.runAsSystem {
            val workspaceMember = WorkspaceMember(
                memberId = "$USER_JOIN_PREFIX$currentUser",
                authorities = listOf(ecosAuthoritiesApi.getAuthorityRef(currentUser)),
                memberRole = WorkspaceMemberRole.USER
            )
            addMember(workspaceId, workspaceMember)
        }
    }

    fun resetMembersToDefault(workspaceId: String) {

        if (AuthContext.isNotRunAsSystemOrAdmin()) {
            error("Permission denied")
        }

        AuthContext.runAsSystem {
            val workspaceAtts = recordsService.getAtts(
                WorkspaceDesc.getRef(workspaceId),
                ResetToDefaultMembersWsAtts::class.java
            )
            workspaceAtts.currentMembers?.let {
                recordsService.delete(it)
            }
            workspaceAtts.defaultWorkspaceMembers?.forEach {
                addMember(workspaceId, it, checkExistingMembers = false)
            }
        }
    }

    fun addMember(workspaceId: String, memberToAdd: WorkspaceMember) {
        addMember(workspaceId, memberToAdd, checkExistingMembers = true)
    }

    private fun addMember(workspaceId: String, memberToAdd: WorkspaceMember, checkExistingMembers: Boolean) {

        if (checkExistingMembers) {
            val workspaceInfo = getWorkspace(workspaceId)

            val existentAuthorities = hashSetOf<EntityRef>()
            for (wsMember in workspaceInfo.workspaceMembers) {
                for (wsMemberAuthority in wsMember.authorities) {
                    if (memberToAdd.authorities.contains(wsMemberAuthority)) {
                        existentAuthorities.add(wsMemberAuthority)
                    }
                }
            }
            require(existentAuthorities.isEmpty()) {
                "Members: ${existentAuthorities.joinToString()} already exists in workspace: '$workspaceId'"
            }
        }

        recordsService.mutate(
            WorkspaceMemberDesc.getRef(""),
            mapOf(
                WorkspaceMemberDesc.ATT_MEMBER_ID to memberToAdd.memberId,
                WorkspaceMemberDesc.ATT_AUTHORITIES to memberToAdd.authorities,
                WorkspaceMemberDesc.ATT_MEMBER_ROLE to memberToAdd.memberRole,
                RecordConstants.ATT_PARENT to WorkspaceDesc.getRef(workspaceId),
                RecordConstants.ATT_PARENT_ATT to "workspaceMembers",
            )
        )
    }

    private class WorkspaceVisitAtts(
        val workspace: String
    )

    private class WorkspaceMembersAtts(
        @AttName(RecordConstants.ATT_NOT_EXISTS + "?bool!")
        val notExists: Boolean,
        val workspaceMembers: List<MemberInfo>?
    ) {
        class MemberInfo(
            val authorities: List<EntityRef>?,
            val memberRole: WorkspaceMemberRole?
        )
    }

    private class ResetToDefaultMembersWsAtts(
        @AttName(WorkspaceDesc.ATT_WORKSPACE_MEMBERS + "[]?id")
        val currentMembers: List<EntityRef>?,
        val defaultWorkspaceMembers: List<WorkspaceMember>?
    )

    private data class GroupInfo(
        @AttName("containedUsers")
        val containedUsers: List<EntityRef> = emptyList()
    )
}
