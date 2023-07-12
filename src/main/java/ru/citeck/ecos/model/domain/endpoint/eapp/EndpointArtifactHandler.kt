package ru.citeck.ecos.model.domain.endpoint.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.lib.type.constants.TypeConstants
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.function.Consumer

@Component
class EndpointArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService
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
        eventsService.addListener<EndpointChangedEventAtts> {
            withDataClass(EndpointChangedEventAtts::class.java)
            withEventType(RecordChangedEvent.TYPE)
            withLocal(true)
            withFilter(Predicates.eq(
                "record._type.${TypeConstants.ATT_IS_SUBTYPE_OF}.endpoint?bool", true)
            )
            withAction {
                listener.accept(EndpointDto(it.id, it.name, it.url, it.credentials))
            }
        }
    }

    class EndpointDto(
        val id: String,
        val name: MLText?,
        val url: String,
        val credentials: EntityRef?
    )

    private class EndpointChangedEventAtts(
        @AttName("record?localId")
        val id: String,
        @AttName("record.name?json")
        val name: MLText?,
        @AttName("record.url?str")
        val url: String,
        @AttName("record.credentials?id")
        val credentials: EntityRef?
    )
}
