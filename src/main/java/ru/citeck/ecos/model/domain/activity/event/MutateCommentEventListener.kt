package ru.citeck.ecos.model.domain.activity.event

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.domain.activity.config.ActivityConfiguration
import ru.citeck.ecos.model.domain.comments.event.CommentDeleteEvent
import ru.citeck.ecos.model.domain.comments.event.CommentUpdateEvent
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class MutateCommentEventListener(
    eventsService: EventsService,
    private val recordsService: RecordsService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    init {
        eventsService.addListener<UpdateCommentEvent> {
            withTransactional(true)
            withEventType(CommentUpdateEvent.TYPE)
            withDataClass(UpdateCommentEvent::class.java)
            withFilter(
                Predicates.eq("commentRecord._parent._type.isSubTypeOf.comment-activity?bool", true)
            )
            withAction { event ->
                if (event.commentText != event.activityText) {
                    updateCommentActivity(event)
                }
            }
        }

        eventsService.addListener<CommentDeleteEvent> {
            withTransactional(true)
            withEventType(CommentDeleteEvent.TYPE)
            withDataClass(CommentDeleteEvent::class.java)
            withAction { deleteCommentActivity(it) }
        }

        eventsService.addListener<UpdateActivityCommentEvent> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(UpdateActivityCommentEvent::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("record._type.isSubTypeOf.comment-activity?bool", true),
                    Predicates.eq("diff._has.text?bool", true)
                )
            )
            withAction { event ->
                if (event.commentText != event.activityText) {
                    updateComment(event)
                }
            }
        }
    }

    private fun updateCommentActivity(event: UpdateCommentEvent) {
        recordsService.mutate(
            event.activityRecord,
            mapOf(
                "text" to event.commentText
            )
        )
        log.info { "Comment activity ${event.activityRecord} successfully updated" }
    }

    private fun deleteCommentActivity(event: CommentDeleteEvent) {
        val activityRecord = recordsService.queryOne(
            RecordsQuery.create {
                withSourceId("${AppName.EMODEL}/${ActivityConfiguration.ACTIVITY_DAO_ID}")
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(
                    Predicates.and(
                        Predicates.eq("_parent", event.record),
                        Predicates.eq("_type.isSubTypeOf.comment-activity?bool", true),
                        Predicates.eq("comment", null)
                    )
                )
            }
        )

        activityRecord?.let {
            recordsService.delete(activityRecord)
            log.info { "Comment activity $activityRecord successfully deleted" }
        }
    }

    private fun updateComment(event: UpdateActivityCommentEvent) {
        recordsService.mutate(
            event.commentRecord,
            mapOf(
                "text" to event.activityText
            )
        )
        log.info { "Comment ${event.commentRecord} successfully updated" }
    }

    private data class UpdateCommentEvent(
        val commentRecord: EntityRef,
        @AttName("commentRecord.text?str")
        val commentText: String? = null,
        @AttName("commentRecord._parent")
        val activityRecord: EntityRef,
        @AttName("commentRecord._parent.text?str")
        val activityText: String? = null
    )

    private data class UpdateActivityCommentEvent(
        @AttName("after.text?str")
        val activityText: String,
        @AttName("record.comment")
        val commentRecord: EntityRef,
        @AttName("record.comment.text?str")
        val commentText: String? = null,
    )
}
