package ru.citeck.ecos.model.domain.workspace.api.records

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.*
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.domain.workspace.service.WorkspacePermissions
import ru.citeck.ecos.model.domain.workspace.utils.WorkspaceSystemIdUtils
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.attributes.dto.AttOptionValue
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.permissions.dto.PermissionType
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValue
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
        const val WORKSPACE_ATT_MEMBER_AUTHORITY = "workspaceMembers.${WorkspaceMemberDesc.ATT_AUTHORITIES}"

        const val USER_WORKSPACES = "user-workspaces"

        val DEFAULT_PERSONAL_WS_REF = EntityRef.create(WORKSPACE_REPO_SOURCE_ID, "personal-workspace")

        private val log = KotlinLogging.logger {}
    }

    /**
     * We need to duplicate logic from [ru.citeck.ecos.model.domain.workspace.config.WorkspaceDbPerms] read permissions
     * in proxy-query predicate because [DbPermsComponent] is not work properly for many records size.
     * We assume that there may be more than 1000 workspaces in the system.
     */
    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {
        val currentAuthorities = AuthContext.getCurrentAuthorities()
        if (AuthContext.isNotRunAsSystemOrAdmin() &&
            (
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

                val predicate = recsQuery.getPredicate()

                var shouldIncludePersonalWs = false
                PredicateUtils.mapValuePredicates(
                    predicate,
                    {
                        if (it.getAttribute() == WorkspaceDesc.ATT_IS_CURRENT_USER_MEMBER) {
                            shouldIncludePersonalWs = true
                        }
                        null
                    },
                    onlyAnd = true,
                    optimize = true,
                    filterEmptyComposite = true
                )

                val newQuery = recsQuery.copy()
                    .withQuery(processQueryPredicate(predicate))

                if (!shouldIncludePersonalWs) {
                    super.queryRecords(newQuery.build())
                } else {
                    queryWithPersonalWs(newQuery)
                }
            }

            else -> error("Unsupported query language: ${recsQuery.language}")
        }
    }

    private fun queryWithPersonalWs(query: RecordsQuery.Builder): RecsQueryRes<*>? {

        val personalWsRecord = getUserWorkspaceRecord(AuthContext.getCurrentUser())
        if (!predicateService.isMatch(
                personalWsRecord,
                query.query.getAs(Predicate::class.java) ?: Predicates.alwaysTrue()
            )
        ) {
            return super.queryRecords(query.build())
        }

        // first element will be personal ws
        if (query.page.skipCount == 0) {
            query.withMaxItems(query.page.maxItems - 1)
        } else {
            query.withSkipCount(query.page.skipCount - 1)
        }

        val queryRes = super.queryRecords(query.build()) ?: RecsQueryRes<Any>()

        val resRecords = if (query.page.skipCount == 0) {
            val recsWithPersonal = ArrayList<Any>()
            recsWithPersonal.add(personalWsRecord)
            recsWithPersonal.addAll(queryRes.getRecords())
            recsWithPersonal
        } else {
            queryRes.getRecords()
        }

        val resWithPersonalWs = RecsQueryRes<Any>()
        resWithPersonalWs.setTotalCount(queryRes.getTotalCount() + 1)
        resWithPersonalWs.setRecords(resRecords)

        return resWithPersonalWs
    }

    private fun processQueryPredicate(predicate: Predicate): Predicate {
        val result = PredicateUtils.mapAttributePredicates(
            predicate,
            { srcPred ->
                if (srcPred is ValuePredicate) {
                    when (srcPred.getAttribute()) {
                        WorkspaceDesc.ATT_IS_CURRENT_USER_MEMBER -> {
                            val authoritiesRefs = ecosAuthoritiesApi.getAuthorityRefs(
                                AuthContext.getCurrentUserWithAuthorities()
                                    .filter { !it.startsWith(AuthRole.PREFIX) }
                            )
                            var targetPred: Predicate = Predicates.inVals(
                                WORKSPACE_ATT_MEMBER_AUTHORITY,
                                authoritiesRefs
                            )
                            if (!srcPred.getValue().asBoolean()) {
                                targetPred = Predicates.not(targetPred)
                            }
                            targetPred
                        }

                        else -> srcPred
                    }
                } else {
                    srcPred
                }
            },
            onlyAnd = false,
            optimize = false,
            filterEmptyComposite = false
        ) ?: Predicates.alwaysFalse()
        return getPermissionsPrefilteredQuery(result)
    }

    private fun getPermissionsPrefilteredQuery(basePredicate: Predicate): Predicate {

        if (AuthContext.isRunAsSystemOrAdmin()) {
            return basePredicate
        }

        val userAuthoritiesRefs = AuthContext.getCurrentUserWithAuthorities()
            .filter { it.startsWith(AuthRole.PREFIX).not() }
            .map { ecosAuthoritiesApi.getAuthorityRef(it) }

        val predicateWithPermsPrefilter = Predicates.and(
            basePredicate,
            Predicates.or(
                Predicates.eq(WORKSPACE_ATT_VISIBILITY, WorkspaceVisibility.PUBLIC.name),
                Predicates.inVals(WORKSPACE_ATT_MEMBER_AUTHORITY, userAuthoritiesRefs)
            )
        )

        log.debug { "Query workspaces with predicate: ${Json.mapper.toPrettyString(predicateWithPermsPrefilter)}" }

        return predicateWithPermsPrefilter
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        // In one mutation list we can process actions or regular mutation
        val processedJoinActionRecordIds = processJoinAction(records)
        if (processedJoinActionRecordIds.isNotEmpty()) {
            return processedJoinActionRecordIds
        }

        // Check allowing to create new workspace on proxy,
        // because DbPermsComponent perms write not used for creation case
        check(workspacePermissions.currentAuthCanMutateWorkspace()) {
            "Current user has no permissions to mutate workspaces"
        }

        if (AuthContext.isNotRunAsSystem()) {
            val invalidIds = mutableListOf<String>()
            records.mapNotNull {
                val idAtt = it.attributes["id"].asText()
                if (workspaceService.isWorkspaceNotExists(idAtt)) {
                    check(workspacePermissions.currentAuthCanCreateWorkspace()) {
                        "Current user has no permissions to create workspace id: $idAtt"
                    }
                }

                if (it.id.isEmpty() && !isValidWorkspaceIdForNewRecord(idAtt)) {
                    invalidIds.add(idAtt)
                } else {
                    null
                }
            }
            if (invalidIds.isNotEmpty()) {
                error("Invalid workspace identifiers: ${invalidIds.joinToString { "'$it'" }}")
            }
        }

        return super.mutate(records)
    }

    private fun isValidWorkspaceIdForNewRecord(workspaceId: String): Boolean {
        if (workspaceId.isBlank()) {
            return false
        }
        if (!WorkspaceDesc.VALID_WS_ID_REGEX.matches(workspaceId)) {
            return false
        }
        val dollarsCount = workspaceId.count { it == '$' }
        if (dollarsCount == 0) {
            return true
        }
        if (dollarsCount == 1 && workspaceId.startsWith("admin$")) {
            return AuthContext.isRunAsSystemOrAdmin()
        }
        return false
    }

    private fun processJoinAction(records: List<LocalRecordAtts>): List<String> {
        val processActionsIds = mutableListOf<String>()

        records.forEach { record ->
            val action = record.getAtt(WORKSPACE_ACTION_ATT).asText()
            if (action == WorkspaceAction.JOIN.name && record.id.isNotBlank()) {
                workspaceService.joinCurrentUserToWorkspace(record.id)
                processActionsIds.add(record.id)
            }
        }

        return processActionsIds
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {

        val systemFlag = recordsService.getAtts(
            recordIds.map { EntityRef.create(WORKSPACE_REPO_SOURCE_ID, it) },
            listOf(WorkspaceDesc.ATT_SYSTEM_BOOL)
        ).map { it.getAtt(WorkspaceDesc.ATT_SYSTEM_BOOL).asBoolean() }

        val isRunAsSystem = AuthContext.isRunAsSystem()
        val currentUser = AuthContext.getCurrentUser()
        val undeletableIds = recordIds.filterIndexed { idx, workspaceId ->
            systemFlag[idx] || (!isRunAsSystem && !workspaceService.isUserManagerOf(currentUser, workspaceId))
        }
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

        val result = recordAtts.toMutableList()

        for ((idx, id) in virtualUserAttsIdx) {
            val user = id.removePrefix(USER_WORKSPACE_PREFIX)
            result[idx] = if (workspacePermissions.currentAuthCanReadPersonalWorkspaceOf(user)) {
                getUserWorkspaceRecord(user)
            } else {
                EmptyAttValue.INSTANCE
            }
        }

        return result.toList()
    }

    private fun getUserWorkspaceRecord(user: String): UserWorkspaceRecord {

        val defaultConfig = AuthContext.runAsSystem {
            recordsService.getAtts(DEFAULT_PERSONAL_WS_REF, Workspace::class.java)
        }

        return UserWorkspaceRecord(
            defaultConfig.copy()
                .withId(USER_WORKSPACE_PREFIX + user)
                .withWorkspaceMembers(
                    listOf(
                        WorkspaceMember(
                            memberId = user,
                            authorities = listOf(AuthorityType.PERSON.getRef(user)),
                            memberRole = WorkspaceMemberRole.MANAGER
                        )
                    )
                ).build(),
            user
        )
    }

    class UserWorkspaceRecord(
        @AttName("...")
        val workspace: Workspace,
        val user: String
    ) {

        fun getSystemId(): String {
            return WorkspaceSystemIdUtils.USER_WS_SYS_ID_PREFIX + WorkspaceSystemIdUtils.createId(user)
        }

        fun getIsCurrentUserMember(): Boolean {
            return user == AuthContext.getCurrentUser()
        }

        fun getIsCurrentUserManager(): Boolean {
            return user == AuthContext.getCurrentUser()
        }

        fun getPermissions(): UserWorkspacePerms {
            return UserWorkspacePerms
        }

        fun getVisibility(): AttOptionValue {
            return AttOptionValue(
                MLText(
                    I18nContext.ENGLISH to "Private",
                    I18nContext.RUSSIAN to "Приватное",
                ),
                WorkspaceVisibility.PRIVATE.name
            )
        }

        @JsonIgnore
        @AttName(RecordConstants.ATT_WORKSPACE)
        fun getWorkspaceRef(): EntityRef {
            return WorkspaceDesc.getRef(workspace.id)
        }

        fun getEcosType(): String {
            return WorkspaceDesc.TYPE_ID
        }
    }

    object UserWorkspacePerms : AttValue {

        override fun has(name: String): Boolean {
            return PermissionType.READ.name.equals(name, ignoreCase = true)
        }
    }
}
