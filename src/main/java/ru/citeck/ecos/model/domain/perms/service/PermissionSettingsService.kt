package ru.citeck.ecos.model.domain.perms.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.model.domain.perms.dto.PermissionSettingDto
import ru.citeck.ecos.model.domain.perms.dto.PermissionSettingsDto
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Service
class PermissionSettingsService(
    private val recordsService: RecordsService
) {

    fun getSettingsById(id: String): PermissionSettingsDto? {
        if (id.isBlank()) {
            return null
        }
        val atts = recordsService.queryOne(
            RecordsQuery.create()
                .withEcosType(PermissionSettingsDto.TYPE_ID)
                .withQuery(Predicates.eq(PermissionSettingsDto.ATT_ID, id))
                .build(),
            CustomPermsAtts::class.java
        ) ?: return null
        return convertToDto(atts)
    }

    fun getSettingsForRecord(recordRef: EntityRef): PermissionSettingsDto? {
        if (recordRef.isEmpty()) {
            return null
        }
        val atts = recordsService.queryOne(
            RecordsQuery.create()
                .withEcosType(PermissionSettingsDto.TYPE_ID)
                .withQuery(Predicates.eq(PermissionSettingsDto.ATT_RECORD_REF, recordRef))
                .build(),
            CustomPermsAtts::class.java
        )
        return atts?.let { perms ->
            PermissionSettingsDto(
                perms.id,
                recordRef,
                perms.inherit ?: true,
                perms.version ?: 0,
                perms.settings.map { setting ->
                    PermissionSettingDto(setting.permissions, setting.roles, setting.authorities)
                }
            )
        }
    }

    private fun convertToDto(atts: CustomPermsAtts): PermissionSettingsDto {
        return PermissionSettingsDto(
            atts.id,
            atts.recordRef,
            atts.inherit ?: true,
            atts.version ?: 0,
            atts.settings.map { setting ->
                PermissionSettingDto(
                    setting.permissions,
                    setting.roles,
                    setting.authorities
                )
            }
        )
    }

    private class CustomPermsAtts(
        val id: String,
        val recordRef: EntityRef,
        val inherit: Boolean?,
        val version: Int?,
        val settings: List<CustomPermsSettingsAtts>
    )

    private class CustomPermsSettingsAtts(
        val permissions: Set<EntityRef>,
        val authorities: Set<String>,
        val roles: Set<String>
    )
}
