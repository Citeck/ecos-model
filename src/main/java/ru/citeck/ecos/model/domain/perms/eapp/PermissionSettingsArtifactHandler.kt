package ru.citeck.ecos.model.domain.perms.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.model.domain.perms.dto.PermissionSettingDto
import ru.citeck.ecos.model.domain.perms.dto.PermissionSettingsDto
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class PermissionSettingsArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService
) : EcosArtifactHandler<PermissionSettingsDto> {

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(toRef(artifactId))
    }

    override fun deployArtifact(artifact: PermissionSettingsDto) {
        recordsService.mutate(
            EntityRef.create(PermissionSettingsDto.SOURCE_ID, ""),
            artifact
        )
    }

    override fun getArtifactType(): String {
        return "model/permission-settings"
    }

    override fun listenChanges(listener: Consumer<PermissionSettingsDto>) {
        eventsService.addListener<PermissionSettingsEventAtts> {
            withDataClass(PermissionSettingsEventAtts::class.java)
            withFilter(Predicates.eq("typeDef.id", PermissionSettingsDto.TYPE_ID))
            withLocal(true)
            withAction {
                listener.accept(
                    PermissionSettingsDto(
                        it.id,
                        it.recordRef,
                        it.inherit,
                        it.settings
                    )
                )
            }
        }
    }

    private fun toRef(id: String): EntityRef {
        return EntityRef.create(PermissionSettingsDto.SOURCE_ID, id)
    }

    private class PermissionSettingsEventAtts(
        @AttName("record?localId")
        val id: String,
        @AttName("record.recordRef?id")
        val recordRef: EntityRef,
        @AttName("record.inherit!true")
        val inherit: Boolean,
        @AttName("record.settings[]?json!")
        val settings: List<PermissionSettingDto>
    )
}
