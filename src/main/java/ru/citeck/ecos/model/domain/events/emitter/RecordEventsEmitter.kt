package ru.citeck.ecos.model.domain.events.emitter

import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.events2.emitter.EventsEmitter
import ru.citeck.ecos.events2.type.*
import javax.annotation.PostConstruct

@Component
class RecordEventsEmitter(val eventsService: EventsService) {

    private lateinit var recChangedEmitter: EventsEmitter<RecordChangedEvent>
    private lateinit var recCreatedEmitter: EventsEmitter<RecordCreatedEvent>
    private lateinit var recStatusChangedEmitter: EventsEmitter<RecordStatusChangedEvent>
    private lateinit var recDraftStatusChangedEmitter: EventsEmitter<RecordDraftStatusChangedEvent>
    private lateinit var recDeletedEmitter: EventsEmitter<RecordDeletedEvent>

    @PostConstruct
    fun init() {
        recChangedEmitter = eventsService.getEmitter(EmitterConfig.create {
            withEventType(RecordChangedEvent.TYPE)
            withSource(RecordChangedEvent::class.java.simpleName)
            withEventClass(RecordChangedEvent::class.java)
        })
        recCreatedEmitter = eventsService.getEmitter(EmitterConfig.create {
            withEventType(RecordCreatedEvent.TYPE)
            withSource(RecordCreatedEvent::class.java.simpleName)
            withEventClass(RecordCreatedEvent::class.java)
        })
        recStatusChangedEmitter = eventsService.getEmitter(EmitterConfig.create {
            withEventType(RecordStatusChangedEvent.TYPE)
            withSource(RecordStatusChangedEvent::class.java.simpleName)
            withEventClass(RecordStatusChangedEvent::class.java)
        })
        recDeletedEmitter = eventsService.getEmitter(EmitterConfig.create {
            withEventType(RecordDeletedEvent.TYPE)
            withSource(RecordDeletedEvent::class.java.simpleName)
            withEventClass(RecordDeletedEvent::class.java)
        })
        recDraftStatusChangedEmitter = eventsService.getEmitter(EmitterConfig.create {
            withEventType(RecordDraftStatusChangedEvent.TYPE)
            withSource(RecordDraftStatusChangedEvent::class.java.simpleName)
            withEventClass(RecordDraftStatusChangedEvent::class.java)
        })
    }

    fun emitRecChanged(event: RecordChangedEvent) {
        recChangedEmitter.emit(event)
    }

    fun emitRecCreatedEvent(event: RecordCreatedEvent) {
        recCreatedEmitter.emit(event)
    }

    fun emitRecStatusChanged(event: RecordStatusChangedEvent) {
        recStatusChangedEmitter.emit(event)
    }

    fun emitRecDeleted(event: RecordDeletedEvent) {
        recDeletedEmitter.emit(event)
    }

    fun emitRecDraftStatusChanged(event: RecordDraftStatusChangedEvent) {
        recDraftStatusChangedEmitter.emit(event)
    }
}
