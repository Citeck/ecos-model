package ru.citeck.ecos.model.domain.perms.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.model.domain.perms.service.PermissionSettingsConfig
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class PermissionDefArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService
) : EcosArtifactHandler<PermissionDefArtifactHandler.PermissionDef> {

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(toRef(artifactId))
    }

    override fun deployArtifact(artifact: PermissionDef) {
        recordsService.mutate(
            EntityRef.create(PermissionSettingsConfig.PERMISSION_DEF_ID, ""),
            artifact
        )
    }

    override fun getArtifactType(): String {
        return "model/permission-def"
    }

    override fun listenChanges(listener: Consumer<PermissionDef>) {
        eventsService.addListener<PermissionDefEventAtts> {
            withDataClass(PermissionDefEventAtts::class.java)
            withFilter(Predicates.eq("typeDef.id", PermissionSettingsConfig.PERMISSION_DEF_ID))
            withLocal(true)
            withAction { listener.accept(PermissionDef(it.id, it.name, it.appliesToTypes)) }
        }
    }

    private fun toRef(id: String): EntityRef {
        return EntityRef.create(PermissionSettingsConfig.PERMISSION_DEF_ID, id)
    }

    class PermissionDef(
        val id: String,
        val name: MLText = MLText.EMPTY,
        val appliesToTypes: List<EntityRef> = emptyList()
    )

    private class PermissionDefEventAtts(
        @AttName("record?localId")
        val id: String,
        @AttName("record.name?json!")
        val name: MLText,
        @AttName("record.appliesToTypes[]?id!")
        val appliesToTypes: List<EntityRef>
    )
}
