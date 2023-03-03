package ru.citeck.ecos.model.domain.aspects.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.aspect.constants.AspectConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import java.util.function.Consumer

@Component
class AspectArtifactHandler(
    private val recordsService: RecordsService
) : EcosArtifactHandler<AspectDef> {

    private lateinit var listener: Consumer<AspectDef>

    override fun deleteArtifact(artifactId: String) {
        AuthContext.runAsSystem {
            recordsService.delete(EntityRef.create(AppName.EMODEL, AspectConstants.ASPECT_SOURCE, artifactId))
        }
    }

    override fun deployArtifact(artifact: AspectDef) {
        val recordRef = EntityRef.create(AspectConstants.ASPECT_SOURCE, "")
        AuthContext.runAsSystem {
            recordsService.mutate(recordRef, artifact)
        }
    }

    override fun getArtifactType(): String {
        return "model/aspect"
    }

    override fun listenChanges(listener: Consumer<AspectDef>) {
        this.listener = listener
    }

    fun aspectWasChanged(aspect: AspectDef) {
        this.listener.accept(aspect)
    }
}
