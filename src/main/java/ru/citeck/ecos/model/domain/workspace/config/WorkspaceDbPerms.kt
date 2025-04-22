package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.service.WorkspacePermissions
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records3.RecordsService

@Component
class WorkspaceDbPerms(
    private val workspacePermissions: WorkspacePermissions,
    private val recordsService: RecordsService,
    private val workspaceService: WorkspaceService
) : DbPermsComponent {

    override fun getRecordPerms(
        user: String,
        authorities: Set<String>,
        record: Any
    ): DbRecordPerms {

        val wsAtts = recordsService.getAtts(
            record,
            listOf(WorkspaceDesc.ATT_ID, WorkspaceDesc.ATT_SYSTEM_BOOL)
        )
        val workspaceId = wsAtts.getAtt(WorkspaceDesc.ATT_ID).asText()
        val isSystemWs = wsAtts[WorkspaceDesc.ATT_SYSTEM_BOOL].asBoolean()

        return object : DbRecordPerms {
            override fun getAdditionalPerms(): Set<String> {
                val result = mutableSetOf<String>()
                if (!isSystemWs && workspaceService.isUserManagerOf(user, workspaceId)) {
                    result.add("delete")
                }
                if (workspacePermissions.canJoin(user, authorities)) {
                    result.add("join")
                }
                return result
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
