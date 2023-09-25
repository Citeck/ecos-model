package ru.citeck.ecos.model.domain.endpoint.service

import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.endpoints.lib.EcosEndpointImpl
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EventsEmitter
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.lib.type.constants.TypeConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.endpoint.event.EndpointChangedEvent
import ru.citeck.ecos.webapp.lib.endpoint.provider.ModelEcosEndpointsProvider
import java.util.concurrent.CopyOnWriteArrayList
import javax.annotation.PostConstruct

@Configuration
class EndpointsService(
    private val modelEcosEndpointsProvider: ModelEcosEndpointsProvider,
    private val recordsService: RecordsService,
    private val eventsService: EventsService
) {

    private val endpointChangedListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private lateinit var endpointChangedEventEmitter: EventsEmitter<EndpointChangedEvent>

    @PostConstruct
    fun init() {
        endpointChangedEventEmitter = eventsService.getEmitter {
            withEventClass(EndpointChangedEvent::class.java)
            withEventType(EndpointChangedEvent.EVENT_ID)
            withSource("ecos-model.endpoints-service")
        }
        modelEcosEndpointsProvider.setCustomEndpointResolver {
            val atts = recordsService.getAtts("endpoint@$it", EndpointAtts::class.java)
            if (atts.url.isNullOrBlank()) {
                null
            } else {
                EcosEndpointImpl(atts.url, atts.credentials?.getLocalId() ?: "")
            }
        }
        eventsService.addListener<EndpointRepoChangedEventAtts> {
            withDataClass(EndpointRepoChangedEventAtts::class.java)
            withEventType(RecordChangedEvent.TYPE)
            withLocal(true)
            withFilter(
                Predicates.eq("record._type.${TypeConstants.ATT_IS_SUBTYPE_OF}.endpoint?bool", true)
            )
            withAction { event -> endpointChangedListeners.forEach { it.invoke(event.id) } }
        }
        addOnChangeListener {
            endpointChangedEventEmitter.emit(EndpointChangedEvent(it))
        }
    }

    fun addOnChangeListener(listener: (String) -> Unit) {
        endpointChangedListeners.add(listener)
    }

    class EndpointAtts(
        val url: String? = null,
        val credentials: EntityRef? = null
    )

    class EndpointRepoChangedEventAtts(
        @AttName("record.id")
        val id: String
    )
}
