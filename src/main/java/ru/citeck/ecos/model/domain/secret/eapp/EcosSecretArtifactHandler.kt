package ru.citeck.ecos.model.domain.secret.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.model.domain.secret.service.EcosSecretDto
import ru.citeck.ecos.model.domain.secret.service.EcosSecretService
import java.util.function.Consumer

@Component
class EcosSecretArtifactHandler(
    private val service: EcosSecretService
) : EcosArtifactHandler<EcosSecretDto> {

    override fun deleteArtifact(artifactId: String) {
        service.delete(artifactId)
    }

    override fun deployArtifact(artifact: EcosSecretDto) {
        service.save(artifact)
    }

    override fun getArtifactType(): String {
        return "model/secret"
    }

    override fun listenChanges(listener: Consumer<EcosSecretDto>) {
        service.listenLocalChanges {
            listener.accept(it)
        }
    }
}
