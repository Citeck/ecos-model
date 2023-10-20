package ru.citeck.ecos.model.domain.perms.dto

import ru.citeck.ecos.webapp.api.entity.EntityRef

data class PermissionSettingDto(
    val permissions: Set<EntityRef> = emptySet(),
    val roles: Set<String> = emptySet(),
    val authorities: Set<String> = emptySet()
)
