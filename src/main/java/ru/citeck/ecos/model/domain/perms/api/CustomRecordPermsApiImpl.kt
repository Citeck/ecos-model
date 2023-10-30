package ru.citeck.ecos.model.domain.perms.api

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.perms.dto.PermissionSettingsDto
import ru.citeck.ecos.model.domain.perms.service.PermissionSettingsService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.perms.component.custom.CustomRecordPerms
import ru.citeck.ecos.webapp.lib.perms.component.custom.CustomRecordPermsApi
import ru.citeck.ecos.webapp.lib.perms.component.custom.CustomRecordPermsData

@Primary
@Component
class CustomRecordPermsApiImpl(
    private val permissionSettings: PermissionSettingsService,
    private val recordsService: RecordsService
) : CustomRecordPermsApi {

    companion object {
        private const val PARENT_REF_ATT = RecordConstants.ATT_PARENT + ScalarType.ID_SCHEMA
    }

    override fun getPerms(recordRef: EntityRef): CustomRecordPerms {

        val result = LinkedHashSet<CustomRecordPermsData>()
        val dependencies = HashSet<EntityRef>()
        fun addCustomPerms(perms: PermissionSettingsDto?) {
            perms ?: return
            result.addAll(
                perms.settings.map { setting ->
                    CustomRecordPermsData(
                        setting.permissions.mapTo(LinkedHashSet()) { it.getLocalId() },
                        setting.authorities,
                        setting.roles
                    )
                }
            )
        }
        dependencies.add(recordRef)
        var atts = permissionSettings.getSettingsForRecord(recordRef)
        addCustomPerms(atts)

        var currentRef = recordRef

        while (atts?.inherit != false && currentRef.isNotEmpty()) {
            val parentRef = recordsService.getAtt(currentRef, PARENT_REF_ATT).asText().toEntityRef()
            if (parentRef.isEmpty()) {
                break
            }
            dependencies.add(parentRef)
            atts = permissionSettings.getSettingsForRecord(parentRef)
            addCustomPerms(atts)
            currentRef = parentRef
        }
        return CustomRecordPerms(result.toList(), dependencies)
    }
}
