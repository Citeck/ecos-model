package ru.citeck.ecos.model.num.apps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.num.dto.NumTemplateDto
import ru.citeck.ecos.model.num.service.NumTemplateService
import java.util.function.Consumer

@Component
class NumTemplateArtifactHandler(val numTemplateService: NumTemplateService) : EcosArtifactHandler<NumTemplateDto> {

    override fun deployArtifact(artifact: NumTemplateDto) {
        numTemplateService.save(artifact)
    }

    override fun deleteArtifact(artifactId: String) {
        numTemplateService.delete(IdInWs.create(artifactId))
    }

    override fun getArtifactType(): String {
        return "model/num-template"
    }

    override fun listenChanges(listener: Consumer<NumTemplateDto>) {
        numTemplateService.addListener { _, after ->
            if (after.entity.workspace.isBlank()) {
                listener.accept(NumTemplateDto(after.entity))
            }
        }
    }
}
