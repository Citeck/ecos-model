package ru.citeck.ecos.model.domain.perms.dto

import ru.citeck.ecos.webapp.api.entity.EntityRef

data class PermissionSettingsDto(
    val id: String,
    val recordRef: EntityRef,
    val inherit: Boolean,
    val settings: List<PermissionSettingDto>
) {
    companion object {
        const val TYPE_ID = "permission-settings"
        const val SOURCE_ID = "permission-settings"

        const val ATT_ID = "id"
        const val ATT_RECORD_REF = "recordRef"
        const val ATT_INHERIT = "inherit"
        const val ATT_SETTINGS = "settings"
    }
}
