package ru.citeck.ecos.model.domain.endpoint.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EndpointArtifactHandler(
    private val webAppApi: EcosWebAppApi,
    private val recordsService: RecordsService
) : EcosArtifactHandler<EndpointArtifactHandler.EndpointDto> {

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
        webAppApi.doBeforeAppReady {
            val endpointDao = recordsService.getRecordsDao(ENDPOINT_SRC_ID, DbRecordsDao::class.java)
                ?: error("Records DAO doesn't found by ID '$ENDPOINT_SRC_ID'")
            endpointDao.addListener(object : DbRecordsListenerAdapter() {
                override fun onChanged(event: DbRecordChangedEvent) {
                    listener.accept(recordsService.getAtts(event.record, EndpointDto::class.java))
                }
            })
        }
    }

    class EndpointDto(
        val id: String,
        val name: MLText?,
        val url: String,
        val credentials: EntityRef?
    )
}
