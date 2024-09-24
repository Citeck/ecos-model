package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.model.domain.workspace.service.WorkspacePermissions

@Component
class WorkspaceDbPerms(
    private val workspacePermissions: WorkspacePermissions
) : DbPermsComponent {

    override fun getRecordPerms(
        user: String,
        authorities: Set<String>,
        record: Any
    ): DbRecordPerms {

        return object : DbRecordPerms {
            override fun getAdditionalPerms(): Set<String> {
                if (workspacePermissions.canJoin(user, authorities)) {
                    return setOf("Join")
                }

                return emptySet()
            }

            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf(AuthRole.ADMIN, AuthRole.SYSTEM)
            }

            override fun hasReadPerms(): Boolean {
                return workspacePermissions.allowRead(user, authorities, record)
            }

            override fun hasWritePerms(): Boolean {
                return workspacePermissions.allowWrite(user, authorities, record)
            }

            override fun hasAttWritePerms(name: String): Boolean {
                return hasWritePerms()
            }

            override fun hasAttReadPerms(name: String): Boolean {
                return hasReadPerms()
            }
        }
    }
}
