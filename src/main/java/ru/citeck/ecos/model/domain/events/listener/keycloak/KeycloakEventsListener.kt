package ru.citeck.ecos.model.domain.events.listener.keycloak

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
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

        private const val EVENT_TYPE_USER = "keycloak-user"
        private const val EVENT_TYPE_ADMIN = "keycloak-admin"

        private const val EVENT_TYPE_LOGIN = "LOGIN"
        private const val RESOURCE_TYPE_USER = "USER"
        private const val RESOURCE_OP_TYPE_CREATE = "CREATE"

        private val log = KotlinLogging.logger {}
    }

    @Value("\${ecos.idp.default-realm}")
    lateinit var defaultRealm: String

    @PostConstruct
    fun init() {

        addKkEventsLogging(EVENT_TYPE_USER)
        addKkEventsLogging(EVENT_TYPE_ADMIN)

        eventsService.addListener<LoginEventAtts> {
            withEventType(EVENT_TYPE_USER)
            withDataClass(LoginEventAtts::class.java)
            withAction {
                processLoginEvent(it)
            }
            withFilter(Predicates.and(
                Predicates.eq("type", EVENT_TYPE_LOGIN),
                Predicates.eq("realmId", defaultRealm),
            ))
        }
        eventsService.addListener<UserCreatedEventAtts> {
            withEventType(EVENT_TYPE_ADMIN)
            withDataClass(UserCreatedEventAtts::class.java)
            withAction {
                processUserCreatedEvent(UserCreatedEvent(it))
            }
            withFilter(Predicates.and(
                Predicates.eq("resourceType", RESOURCE_TYPE_USER),
                Predicates.eq("realmId", defaultRealm),
                Predicates.eq("operationType", RESOURCE_OP_TYPE_CREATE)
            ))
        }
    }

    private fun addKkEventsLogging(type: String) {
        eventsService.addListener<ObjectData> {
            withEventType(type)
            withAttributes(mapOf("data" to "?json"))
            withDataClass(ObjectData::class.java)
            withAction { data ->
                log.info { data.get("data").toString() }
            }
        }
    }

    private fun processLoginEvent(event: LoginEventAtts) {
        val mutAtts = mapOf(
            PersonConstants.ATT_LAST_LOGIN_TIME to event.time,
            PersonConstants.ATT_LAST_ACTIVITY_TIME to event.time
        )
        recordsService.mutate(AuthorityType.PERSON.getRef(event.userName.lowercase()), mutAtts)
    }

    private fun processUserCreatedEvent(event: UserCreatedEvent) {

        val user = event.user
        if (user.userName.isBlank()) {
            log.warn { "User was created, but username is blank. User: $user" }
            return
        }

        val notExists = recordsService.getAtt(
            AuthorityType.PERSON.getRef(user.userName),
            "_notExists?bool"
        ).asBoolean()

        if (!notExists) {
            log.info { "User was created in Keycloak, but it's already exists. Nothing to do" }
            return
        }

        val userAtts = ObjectData.create()
        userAtts.set("id", user.userName)
        userAtts.set(PersonConstants.ATT_FIRST_NAME, user.firstName)
        userAtts.set(PersonConstants.ATT_LAST_NAME, user.lastName)
        userAtts.set(PersonConstants.ATT_EMAIL, user.email)

        recordsService.create(AuthorityType.PERSON.sourceId, userAtts)
    }

    data class LoginEventAtts(
        @AttName("time?num")
        val time: Instant,
        @AttName("details.username")
        val userName: String
    )

    data class UserRepresentationAtts(
        val username: String,
        val enabled: Boolean,
        val firstName: String?,
        val lastName: String?,
        val email: String?
    )

    data class UserCreatedEventAtts(
        @AttName("representation?json")
        val representation: UserRepresentationAtts
    )

    data class UserCreatedEvent(
        val user: UserRepresentation
    ) {
        constructor(atts: UserCreatedEventAtts) : this(
            UserRepresentation(atts.representation)
        )
    }

    data class UserRepresentation(
        val userName: String,
        val enabled: Boolean,
        val firstName: String?,
        val lastName: String?,
        val email: String?
    ) {
        constructor(atts: UserRepresentationAtts): this(
            atts.username,
            atts.enabled,
            atts.firstName,
            atts.lastName,
            atts.email
        )
    }
}
