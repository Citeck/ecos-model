package ru.citeck.ecos.model.domain.endpoint.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.model.domain.endpoint.dto.EndpointDto
import ru.citeck.ecos.model.domain.endpoint.service.EndpointsService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EndpointArtifactHandler(
    private val recordsService: RecordsService,
    private val endpointsService: EndpointsService
) : EcosArtifactHandler<EndpointDto> {

    companion object {
        private const val ENDPOINT_SRC_ID = "endpoint"
    }

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(EntityRef.create(ENDPOINT_SRC_ID, artifactId))
    }

    override fun deployArtifact(artifact: EndpointDto) {
        recordsService.mutate(EntityRef.create(ENDPOINT_SRC_ID, ""), artifact)
    }

    override fun getArtifactType(): String {
        return "model/endpoint"
    }

    override fun listenChanges(listener: Consumer<EndpointDto>) {
        endpointsService.addOnChangeListener {
            val ref = EntityRef.create(AppName.EMODEL, "endpoint", it)
            listener.accept(recordsService.getAtts(ref, EndpointDto::class.java))
        }
    }
}
