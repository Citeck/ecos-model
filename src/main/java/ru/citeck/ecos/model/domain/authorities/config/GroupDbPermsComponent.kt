package ru.citeck.ecos.model.domain.authorities.config

import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType

class GroupDbPermsComponent(
    private val recordsService: RecordsService,
    private val authorityService: AuthorityService
) : DbPermsComponent {

    companion object {
        const val GROUPS_MANAGERS_GROUP_WITH_PREFIX = AuthGroup.PREFIX + AuthorityGroupConstants.GROUPS_MANAGERS_GROUP

        val PROTECTED_GROUPS = listOf(
            *AuthorityService.ADMIN_GROUPS.toTypedArray(),
            AuthorityGroupConstants.GROUPS_MANAGERS_GROUP
        )
    }

    fun isWriteAllowed(user: String, authorities: Set<String>, record: Any): Boolean {
        return authorities.contains(AuthRole.SYSTEM) ||
            authorities.contains(AuthRole.ADMIN) ||
            isGroupManaged(record, authorities)
    }

    override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
        return GroupsPerms(isWriteAllowed(user, authorities, record))
    }

    private fun isGroupManaged(record: Any, authorities: Set<String>): Boolean {
        if (!authorities.contains(GROUPS_MANAGERS_GROUP_WITH_PREFIX)) {
            return false
        }
        val groupId = recordsService.getAtt(record, ScalarType.LOCAL_ID_SCHEMA).asText()
        if (PROTECTED_GROUPS.contains(groupId) || groupId == AuthorityGroupConstants.MANAGED_GROUPS_GROUP) {
            return false
        }
        val expandedGroups = authorityService.getExpandedGroups(groupId, true)
        if (!expandedGroups.contains(AuthorityGroupConstants.MANAGED_GROUPS_GROUP)) {
            return false
        }
        var isGroupProtected = PROTECTED_GROUPS.any { expandedGroups.contains(it) }
        if (!isGroupProtected) {
            isGroupProtected = PROTECTED_GROUPS.any {
                authorityService.getExpandedGroups(it, true).contains(groupId)
            }
        }
        return !isGroupProtected
    }

    private class GroupsPerms(val writeAllowed: Boolean) : DbRecordPerms {
        override fun getAllowedPermissions(): Set<String> {
            return emptySet()
        }

        override fun getAuthoritiesWithReadPermission(): Set<String> {
            return setOf(AuthGroup.EVERYONE)
        }

        override fun hasAttReadPerms(name: String): Boolean {
            return true
        }

        override fun hasAttWritePerms(name: String): Boolean {
            return writeAllowed
        }

        override fun hasReadPerms(): Boolean {
            return true
        }

        override fun hasWritePerms(): Boolean {
            return writeAllowed
        }

        override fun isAllowed(permission: String): Boolean {
            return false
        }
    }
}
