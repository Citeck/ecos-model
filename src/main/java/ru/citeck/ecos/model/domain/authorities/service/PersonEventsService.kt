package ru.citeck.ecos.model.domain.authorities.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EventsEmitter
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import java.time.Instant
import javax.annotation.PostConstruct

@Service
class PersonEventsService(
    private val eventsService: EventsService,
    private val recordsService: RecordsService
) {

    @Value("\${ecos.idp.default-realm}")
    lateinit var defaultRealm: String

    private lateinit var disabledStatusChangedEmitter: EventsEmitter<PersonDisabledStatusChangedEvent>
    private lateinit var createdEmitter: EventsEmitter<PersonCreatedEvent>

    @PostConstruct
    fun init() {
        disabledStatusChangedEmitter = eventsService.getEmitter {
            withEventType(PersonDisabledStatusChangedEvent.ID)
            withEventClass(PersonDisabledStatusChangedEvent::class.java)
            withSource("emodel")
        }
        createdEmitter = eventsService.getEmitter {
            withEventType(PersonCreatedEvent.ID)
            withEventClass(PersonCreatedEvent::class.java)
            withSource("emodel")
        }
    }

    fun onPersonChanged(event: DbRecordChangedEvent) {

        val beforeDisabled = event.before[PersonConstants.ATT_PERSON_DISABLED] == true
        val afterDisabled = event.after[PersonConstants.ATT_PERSON_DISABLED] == true

        if (beforeDisabled != afterDisabled) {
            disabledStatusChangedEmitter.emit(
                PersonDisabledStatusChangedEvent(event.record, afterDisabled, defaultRealm)
            )
            AuthContext.runAsSystem {
                val userId = recordsService.getAtt(event.record, "?localId").asText()
                recordsService.mutateAtt(
                    AuthorityType.PERSON.getRef(userId),
                    PersonConstants.ATT_LAST_ENABLED_TIME,
                    Instant.now()
                )
            }
        }
    }

    fun onPersonCreated(event: DbRecordCreatedEvent) {
        createdEmitter.emit(PersonCreatedEvent(event.record, defaultRealm))
        AuthContext.runAsSystem {
            val userId = recordsService.getAtt(event.record, "?localId").asText()
            recordsService.mutateAtt(
                AuthorityType.PERSON.getRef(userId),
                PersonConstants.ATT_LAST_ENABLED_TIME,
                Instant.now()
            )
        }
    }

    class PersonDisabledStatusChangedEvent(
        val person: Any,
        val disabled: Boolean,
        val realmId: String
    ) {
        companion object {
            const val ID = "person-disabled-status-changed"
        }
    }

    class PersonCreatedEvent(
        val person: Any,
        val realmId: String
    ) {
        companion object {
            const val ID = "person-created"
        }
    }
}
