package ru.citeck.ecos.model.num.apps

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.WsAwareArtifactHandler
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.num.dto.NumTemplateDto
import ru.citeck.ecos.model.num.service.NumTemplateService
import java.util.function.BiConsumer

@Component
class NumTemplateArtifactHandler(val numTemplateService: NumTemplateService) : WsAwareArtifactHandler<NumTemplateDto> {

    override fun deployArtifact(artifact: NumTemplateDto, workspace: String) {
        val toSave = NumTemplateDto(artifact).also { it.workspace = workspace }
        numTemplateService.save(toSave)
    }

    override fun deleteArtifact(artifactId: String, workspace: String) {
        numTemplateService.delete(IdInWs.create(workspace, artifactId))
    }

    override fun getArtifactType(): String {
        return "model/num-template"
    }

    override fun listenChanges(listener: BiConsumer<NumTemplateDto, String>) {
        numTemplateService.addListener { _, after ->
            val workspace = after.entity.workspace
            val stripped = NumTemplateDto(after.entity).also { it.workspace = "" }
            listener.accept(stripped, workspace)
        }
    }
}
