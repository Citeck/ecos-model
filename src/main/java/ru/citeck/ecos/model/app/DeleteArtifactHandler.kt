package ru.citeck.ecos.model.app

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.model.type.service.TypeService
import java.util.function.Consumer

@Slf4j
@Component
@RequiredArgsConstructor
class DeleteArtifactHandler(
    private val typeService: TypeService
) : EcosArtifactHandler<DeleteArtifactHandler.DeleteDto> {

    override fun deployArtifact(artifact: DeleteDto) {
        artifact.types.forEach {
            typeService.deleteWithChildren(it)
        }
    }

    override fun getArtifactType(): String {
        return "model/delete"
    }

    override fun deleteArtifact(artifactId: String) {
        error("Not supported")
    }

    override fun listenChanges(listener: Consumer<DeleteDto>) {
    }

    class DeleteDto(
        val types: List<String> = emptyList()
    )
}
