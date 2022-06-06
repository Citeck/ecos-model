package ru.citeck.ecos.model.app

/*@Component
class ModelDbPermsComponent(
    val roleService: RoleService,
    val permsService: RecordPermsService,
    val recordsService: RecordsService
) : DbPermsComponent {

    private val defaultPerms = DefaultDbPermsComponent(recordsService)

    override fun getRecordPerms(recordRef: RecordRef): DbRecordPerms {
        val perms = permsService.getRecordPerms(recordRef)
            ?: return defaultPerms.getRecordPerms(recordRef)

        return RecordPerms(recordRef, perms)
    }

    private inner class RecordPerms(val recordRef: RecordRef, val perms: RolesPermissions) : DbRecordPerms {

        override fun getAuthoritiesWithReadPermission(): Set<String> {

            val typeRef = RecordRef.valueOf(recordsService.getAtt(recordRef, "_type?id").asText())
            if (RecordRef.isEmpty(typeRef)) {
                error("Type is empty for record $recordRef")
            }
            val authorities = mutableSetOf<String>()
            val roles = roleService.getRoles(typeRef)
            roles.forEach { role ->
                if (perms.isReadAllowed(listOf(role.id))) {
                    authorities.addAll(roleService.getAssignees(recordRef, role.id))
                }
            }

            return authorities
        }

        override fun isCurrentUserHasWritePerms(): Boolean {
            // todo: add write perms checking
            return true
        }
    }
}*/
