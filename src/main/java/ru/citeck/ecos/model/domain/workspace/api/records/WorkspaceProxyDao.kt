package ru.citeck.ecos.model.domain.workspace.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceAction
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.domain.workspace.service.WorkspacePermissions
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class WorkspaceProxyDao(
    private val workspacePermissions: WorkspacePermissions,
    private val workspaceService: EmodelWorkspaceService,
    private val modelServices: ModelServiceFactory,
    private val ecosAuthoritiesApi: EcosAuthoritiesApi
) : RecordsDaoProxy(
    WorkspaceDesc.SOURCE_ID,
    WORKSPACE_REPO_SOURCE_ID
) {

    companion object {
        const val WORKSPACE_REPO_SOURCE_ID = "${WorkspaceDesc.SOURCE_ID}-repo"

        const val WORKSPACE_ACTION_ATT = "action"
        const val WORKSPACE_QUERY_USER_ATT = "user"

        const val WORKSPACE_ATT_VISIBILITY = "visibility"
        const val WORKSPACE_ATT_MEMBER_AUTHORITY = "workspaceMembers.authority"

        const val USER_WORKSPACES = "user-workspaces"

        private val UNDELETABLE_WORKSPACES = setOf(
            "admin\$workspace",
            "default",
            "personal-workspace"
        )

        private val log = KotlinLogging.logger {}
    }

    /**
     * We need to duplicate logic from [ru.citeck.ecos.model.domain.workspace.config.WorkspaceDbPerms] read permissions
     * in proxy-query predicate because [DbPermsComponent] is not work properly for many records size.
     * We assume that there may be more than 1000 workspaces in the system.
     */
    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {
        val currentAuthorities = AuthContext.getCurrentAuthorities()
        if (AuthContext.isNotRunAsSystemOrAdmin() && (
                currentAuthorities.contains(AuthRole.GUEST) ||
                    currentAuthorities.contains(AuthRole.ANONYMOUS) ||
                    currentAuthorities.none { it == AuthRole.USER }
                )
        ) {
            return RecsQueryRes<EntityRef>()
        }

        return when (recsQuery.language) {
            USER_WORKSPACES -> {
                val user = recsQuery.query[WORKSPACE_QUERY_USER_ATT].asText()
                    .ifBlank { AuthContext.getCurrentUser() }
                val result = RecsQueryRes<EntityRef>()
                result.setRecords(
                    modelServices.workspaceService.getUserWorkspaces(user)
                        .map { EntityRef.create(AppName.EMODEL, WorkspaceDesc.SOURCE_ID, it) }
                )
                result
            }

            PredicateService.LANGUAGE_PREDICATE -> {
                super.queryRecords(getPermissionsPrefilteredQuery(recsQuery))
            }

            else -> error("Unsupported query language: ${recsQuery.language}")
        }
    }

    private fun getPermissionsPrefilteredQuery(recsQuery: RecordsQuery): RecordsQuery {
        if (AuthContext.isRunAsSystemOrAdmin()) {
            return recsQuery
        }

        val userRef = AuthorityType.PERSON.getRef(AuthContext.getCurrentUser())

        val userAuthoritiesRefs = AuthContext.getCurrentUserWithAuthorities()
            .filter { it.startsWith(AuthRole.PREFIX).not() }
            .map {
                ecosAuthoritiesApi.getAuthorityRef(it)
            }

        val basePredicate = recsQuery.getQuery(Predicate::class.java)
        val predicateWithPermsPrefilter = Predicates.and(
            basePredicate,
            Predicates.or(
                Predicates.eq(WORKSPACE_ATT_VISIBILITY, WorkspaceVisibility.PUBLIC.name),
                Predicates.eq(RecordConstants.ATT_CREATOR, userRef),
                Predicates.inVals(WORKSPACE_ATT_MEMBER_AUTHORITY, userAuthoritiesRefs)
            )
        )

        log.debug { "Query workspaces with predicate: ${Json.mapper.toPrettyString(predicateWithPermsPrefilter)}" }

        return recsQuery.copy(query = DataValue.create(predicateWithPermsPrefilter))
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        // In one mutation list we can process actions or regular mutation
        val processedJoinActionRecordIds = processJoinAction(records)
        if (processedJoinActionRecordIds.isNotEmpty()) {
            return processedJoinActionRecordIds
        }

        // Check allowing to create new workspace on proxy,
        // because DbPermsComponent perms write not used for creation case
        check(workspacePermissions.currentAuthCanCreateWorkspace()) {
            "Current user has no permissions to mutate workspaces"
        }

        return super.mutate(records)
    }

    private fun processJoinAction(records: List<LocalRecordAtts>): List<String> {
        val processActionsIds = mutableListOf<String>()

        records.forEach { record ->
            val action = record.getAtt(WORKSPACE_ACTION_ATT).asText()
            if (action == WorkspaceAction.JOIN.name && record.id.isNotBlank()) {
                workspaceService.joinCurrentUserToWorkspace(
                    EntityRef.create(
                        AppName.EMODEL,
                        WorkspaceDesc.SOURCE_ID,
                        record.id
                    )
                )
                processActionsIds.add(record.id)
            }
        }

        return processActionsIds
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        val undeletableIds = recordIds.filter { UNDELETABLE_WORKSPACES.contains(it) }
        if (undeletableIds.isNotEmpty()) {
            error("You can't delete this records: $undeletableIds")
        }
        return super.delete(recordIds)
    }

    override fun getRecordsAtts(recordIds: List<String>): List<*>? {
        val recordAtts = super.getRecordsAtts(recordIds)
        if (recordAtts.isNullOrEmpty()) {
            return recordAtts
        }

        val virtualUserAttsIdx = recordIds.mapIndexedNotNull { idx, id ->
            if (id.startsWith(USER_WORKSPACE_PREFIX)) {
                idx to id
            } else {
                null
            }
        }.toMap()

        if (virtualUserAttsIdx.isEmpty()) {
            return recordAtts
        }

        val defaultConfig = AuthContext.runAsSystem {
            recordsService.getAtts(
                EntityRef.create(WORKSPACE_REPO_SOURCE_ID, "personal-workspace"),
                Workspace::class.java
            )
        }
        val result = recordAtts.toMutableList()

        for ((idx, id) in virtualUserAttsIdx) {
            val user = id.removePrefix(USER_WORKSPACE_PREFIX)
            result[idx] = if (workspacePermissions.currentAuthCanReadPersonalWorkspaceOf(user)) {
                UserWorkspaceRecord(
                    defaultConfig.copy()
                        .withId(id)
                        .withWorkspaceMembers(
                            listOf(
                                WorkspaceMember(
                                    id = user,
                                    authority = AuthorityType.PERSON.getRef(user),
                                    memberRole = WorkspaceMemberRole.MANAGER
                                )
                            )
                        ).build(),
                    user
                )
            } else {
                EmptyAttValue.INSTANCE
            }
        }

        return result.toList()
    }

    class UserWorkspaceRecord(
        @AttName("...")
        val workspace: Workspace,
        val user: String
    ) {

        fun getIsCurrentUserMember(): Boolean {
            return user == AuthContext.getCurrentUser()
        }

        fun getIsCurrentUserManager(): Boolean {
            return user == AuthContext.getCurrentUser()
        }
    }
}
