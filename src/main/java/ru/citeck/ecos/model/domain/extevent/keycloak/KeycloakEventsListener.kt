package ru.citeck.ecos.model.domain.extevent.keycloak

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.time.Instant
import javax.annotation.PostConstruct

@Component
class KeycloakEventsListener(
    val eventsService: EventsService,
    val recordsService: RecordsService
) {
    companion object {
        private const val EVENT_TYPE_LOGIN = "LOGIN"
        private val log = KotlinLogging.logger {}
    }

    @Value("\${ecos.idp.default-realm}")
    lateinit var defaultRealm: String

    @PostConstruct
    fun init() {
        eventsService.addListener<LoginEvent> {
            withEventType("keycloak-user")
            withDataClass(LoginEvent::class.java)
            withAction {
                processLoginEvent(it)
            }
            withFilter(Predicates.and(
                Predicates.eq("type", EVENT_TYPE_LOGIN),
                Predicates.eq("realmId", defaultRealm),
            ))
        }
    }

    private fun processLoginEvent(event: LoginEvent) {
        log.info { "LOGIN: $event" }
        val mutAtts = mapOf(
            PersonConstants.ATT_LAST_LOGIN_TIME to event.time
        )
        recordsService.mutate(AuthorityType.PERSON.getRef(event.userName), mutAtts)
    }

    data class LoginEvent(
        @AttName("time?num")
        val time: Instant,
        @AttName("details.username")
        val userName: String
    )
}
