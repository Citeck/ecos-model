package ru.citeck.ecos.model.domain.authorities.config

import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.entity.EntityRef

class AuthorityGroupsManagementCheckListener(
    private val recordsService: RecordsService,
    private val authorityService: AuthorityService,
    private val groupDbPermsComponent: GroupDbPermsComponent,
    private val authorityType: AuthorityType
) : DbRecordsListenerAdapter() {

    override fun onCreated(event: DbRecordCreatedEvent) {
        val runAs = AuthContext.getCurrentRunAsAuth()
        if (AuthContext.isSystemAuth(runAs) || AuthContext.isAdminAuth(runAs)) {
            return
        }
        val authorities = runAs.getAuthorities().toSet()

        if (authorityType == AuthorityType.PERSON) {
            permissionDenied(event.record)
        } else if (authorityType == AuthorityType.GROUP) {
            if (!runAs.getAuthorities().contains(AuthGroup.PREFIX + AuthorityGroupConstants.GROUPS_MANAGERS_GROUP)) {
                permissionDenied(event.record)
            }
            val groups = recordsService.getAtt(
                event.record,
                AuthorityConstants.ATT_AUTHORITY_GROUPS_LOCAL_ID
            ).asStrList()
            for (groupId in groups) {
                val groupRef = AuthorityType.GROUP.getRef(groupId)
                if (!groupDbPermsComponent.isWriteAllowed(runAs.getUser(), authorities, groupRef)) {
                    permissionDenied(groupRef)
                }
            }
        }
    }

    override fun onChanged(event: DbRecordChangedEvent) {

        val runAs = AuthContext.getCurrentRunAsAuth()
        if (AuthContext.isAdminAuth(runAs) || AuthContext.isSystemAuth(runAs)) {
            return
        }
        val authorities = runAs.getAuthorities().toSet()
        val isGroupsManager = authorities.contains(AuthGroup.PREFIX + AuthorityGroupConstants.GROUPS_MANAGERS_GROUP)

        event.assocs.filter {
            it.assocId == AuthorityConstants.ATT_AUTHORITY_GROUPS
        }.forEach {
            if (!isGroupsManager) {
                permissionDenied(event.record)
            }
            if (authorityType == AuthorityType.PERSON) {
                val userName = recordsService.getAtt(event.record, ScalarType.LOCAL_ID_SCHEMA).asText()
                if (userName == runAs.getUser()) {
                    // own authority groups can change only admin
                    permissionDenied(event.record)
                }
            }
            val changedGroups = HashSet<EntityRef>(it.added)
            changedGroups.addAll(it.removed)
            changedGroups.forEach { groupRef ->
                if (!groupDbPermsComponent.isWriteAllowed(runAs.getUser(), authorities, groupRef)) {
                    permissionDenied(groupRef)
                }
            }
        }
        if (authorityType == AuthorityType.PERSON) {
            checkUserChangedPerms(event, runAs.getUser(), authorities)
        }
    }

    private fun checkUserChangedPerms(
        event: DbRecordChangedEvent,
        currentUser: String,
        authorities: Set<String>
    ) {
        val userName = recordsService.getAtt(event.record, ScalarType.LOCAL_ID_SCHEMA).asText()
        if (userName == currentUser) {
            return
        }
        val changedAtts = HashSet<String>()
        event.assocs.forEach { changedAtts.add(it.assocId) }
        val attIds = HashSet(event.before.keys)
        attIds.addAll(event.after.keys)
        for (attId in attIds) {
            if (event.before[attId] != event.after[attId]) {
                changedAtts.add(attId)
            }
        }
        changedAtts.remove(AuthorityConstants.ATT_AUTHORITY_GROUPS)
        if (changedAtts.isEmpty()) {
            return
        }
        val isProfileManager = authorities.contains(
            AuthGroup.PREFIX + AuthorityGroupConstants.USERS_PROFILE_ADMIN_GROUP
        )
        if (!isProfileManager || authorityService.isAdmin(userName)) {
            permissionDenied(event.record)
        }
    }

    private fun permissionDenied(record: Any) {
        val recordRef = recordsService.getAtt(record, ScalarType.ID_SCHEMA).asText()
        error("Permission denied. Record: $recordRef")
    }
}
