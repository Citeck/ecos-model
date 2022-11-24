package ru.citeck.ecos.model.domain.comments.event

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig

@Component
class CommentEventEmitter(
    eventsService: EventsService,

    @Value("\${spring.application.name}")
    private val appName: String
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val commentCreateEmitter = eventsService.getEmitter(
        EmitterConfig.create<CommentCreateEvent> {
            source = appName
            eventType = CommentCreateEvent.TYPE
            eventClass = CommentCreateEvent::class.java
        }
    )

    private val commentUpdateEmitter = eventsService.getEmitter(
        EmitterConfig.create<CommentUpdateEvent> {
            source = appName
            eventType = CommentUpdateEvent.TYPE
            eventClass = CommentUpdateEvent::class.java
        }
    )

    private val commentDeleteEmitter = eventsService.getEmitter(
        EmitterConfig.create<CommentDeleteEvent> {
            source = appName
            eventType = CommentDeleteEvent.TYPE
            eventClass = CommentDeleteEvent::class.java
        }
    )

    fun emitCommentCreate(event: CommentCreateEvent) {
        log.trace { "Emitting comment create event: $event" }
        commentCreateEmitter.emit(event)
    }

    fun emitCommentUpdate(event: CommentUpdateEvent) {
        log.trace { "Emitting comment update event: $event" }
        commentUpdateEmitter.emit(event)
    }

    fun emitCommentDelete(event: CommentDeleteEvent) {
        log.trace { "Emitting comment delete event: $event" }
        commentDeleteEmitter.emit(event)
    }
}
