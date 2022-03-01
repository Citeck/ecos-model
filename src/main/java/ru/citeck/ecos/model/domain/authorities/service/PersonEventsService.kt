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
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
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
            if (!afterDisabled) {
                updatePersonEnabledTime(event.record)
            }
        }
    }

    fun onPersonCreated(event: DbRecordCreatedEvent) {
        createdEmitter.emit(PersonCreatedEvent(event.record, defaultRealm))
        updatePersonEnabledTime(event.record)
    }

    private fun updatePersonEnabledTime(record: Any) {
        AuthContext.runAsSystem {
            val userId = recordsService.getAtt(record, ScalarType.LOCAL_ID.schema).asText()
            val now = Instant.now()
            recordsService.mutate(
                AuthorityType.PERSON.getRef(userId),
                mapOf(
                    PersonConstants.ATT_LAST_ACTIVITY_TIME to now,
                    PersonConstants.ATT_LAST_ENABLED_TIME to now,
                    PersonConstants.ATT_PERSON_DISABLE_REASON to ""
                )
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
