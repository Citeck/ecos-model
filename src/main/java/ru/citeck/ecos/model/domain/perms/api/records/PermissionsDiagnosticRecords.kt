package ru.citeck.ecos.model.domain.perms.api.records

import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.permissions.service.RecordPermsService
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.role.service.RoleService
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class PermissionsDiagnosticRecords(
    private val dbPermsComponent: DbPermsComponent,
    private val recordPermsService: RecordPermsService,
    private val roleService: RoleService
) : AbstractRecordsDao(),
    RecordsQueryDao {

    companion object {
        const val ID = "permissions-diagnostic"
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val query = recsQuery.query.getAsNotNull(Query::class.java)

        val userAuthorities: List<String>
        val user = if (query.user.isBlank()) {
            userAuthorities = AuthContext.getCurrentRunAsAuthorities()
            AuthContext.getCurrentUser()
        } else {
            userAuthorities = recordsService.getAtt(
                AuthorityType.PERSON.getRef(query.user),
                "authorities.list[]?str"
            ).asStrList()
            query.user
        }

        return AuthContext.runAsSystem { getReport(query, user, userAuthorities) }
    }

    private fun getReport(query: Query, user: String, userAuthorities: List<String>): Report {

        val meta = recordsService.getAtts(query.recordRef, RecordMetaAtts::class.java)
        if (meta.notExists) {
            return Report(
                exists = false,
                user = user,
                authorities = userAuthorities
            )
        }

        val fullAuthorities = setOf(user, *userAuthorities.toTypedArray())

        val permissions = dbPermsComponent.getRecordPerms(user, fullAuthorities, query.recordRef)
        val modelPerms = recordPermsService.getRecordPerms(query.recordRef)

        val roles = roleService.getRoles(meta.type)
        val userRoles = roleService.getRolesForAuthorities(query.recordRef, meta.type, fullAuthorities)

        return Report(
            exists = true,
            user = user,
            status = meta.status ?: "",
            typeRef = meta.type ?: EntityRef.EMPTY,
            typeRoles = roles,
            userRoles = userRoles,
            authorities = userAuthorities,
            authoritiesWithReadPerms = permissions.getAuthoritiesWithReadPermission(),
            hasReadPerms = permissions.hasReadPerms(),
            hasWritePerms = permissions.hasWritePerms(),
            hasReadPermsByModel = modelPerms?.isReadAllowed(userRoles) ?: false,
            hasWritePermsByModel = modelPerms?.isWriteAllowed(userRoles) ?: false,
            additionalPerms = permissions.getAdditionalPerms()
        )
    }

    override fun getId(): String {
        return ID
    }

    class Report(
        val exists: Boolean,
        val user: String,
        val typeRef: EntityRef = EntityRef.EMPTY,
        val status: String = "",
        val authorities: List<String> = emptyList(),
        val userRoles: List<String> = emptyList(),
        val authoritiesWithReadPerms: Set<String> = emptySet(),
        val hasReadPerms: Boolean = false,
        val hasWritePerms: Boolean = false,
        val hasReadPermsByModel: Boolean = false,
        val hasWritePermsByModel: Boolean = false,
        val additionalPerms: Set<String> = emptySet(),
        val typeRoles: List<RoleDef> = emptyList(),
    )

    class Query(
        val recordRef: EntityRef,
        val user: String = ""
    )

    class RecordMetaAtts(
        @AttName(RecordConstants.ATT_TYPE)
        val type: EntityRef?,
        @AttName(StatusConstants.ATT_STATUS_STR)
        val status: String?,
        @AttName(RecordConstants.ATT_NOT_EXISTS + "?bool!false")
        val notExists: Boolean
    )
}
