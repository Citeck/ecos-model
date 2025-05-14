package ru.citeck.ecos.model.domain.comments.event

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordDeletedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.model.domain.comments.api.extractor.CommentExtractor
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef

private const val TEXT_ATT = "text"

@Component
class CommentsEmitEventsDbRecordsListener(
    private val commentEventEmitter: CommentEventEmitter,
    private val recordsService: RecordsService,
    private val extractor: CommentExtractor
) : DbRecordsListenerAdapter() {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun onChanged(event: DbRecordChangedEvent) {
        log.debug { "Comment changed: ${event.record}" }

        val recordAtt = recordsService.getAtt(event.record, "record")
        if (recordAtt.isNull()) {
            // record was deleted
            return
        }
        val comment = event.toCommentEvent()
        commentEventEmitter.emitCommentUpdate(comment)
    }

    override fun onCreated(event: DbRecordCreatedEvent) {
        log.debug { "Comment created: ${event.record}" }

        val comment = event.toCommentEvent()
        commentEventEmitter.emitCommentCreate(comment)
    }

    override fun onDeleted(event: DbRecordDeletedEvent) {
        log.debug { "Comment deleted: ${event.record}" }

        val recordAtt = recordsService.getAtt(event.record, "record")
        if (recordAtt.isNull()) {
            // record was deleted
            return
        }
        val comment = event.toCommentEvent()
        commentEventEmitter.emitCommentDelete(comment)
    }

    private fun DbRecordCreatedEvent.toCommentEvent(): CommentCreateEvent {

        val commentAtts = recordsService.getAtts(this.record, CommentAtts::class.java)
        val attachments = extractor.extractAttachRefsFromText(commentAtts.text ?: "").values.toList()

        return CommentCreateEvent(
            record = commentAtts.record,
            commentRecord = commentAtts.commentRecord,
            text = extractor.extractCommentTextForEvent(commentAtts.text ?: ""),
            attachments = attachments
        )
    }

    private fun DbRecordDeletedEvent.toCommentEvent(): CommentDeleteEvent {
        val commentAtts = recordsService.getAtts(this.record, CommentAtts::class.java)

        return CommentDeleteEvent(
            record = commentAtts.record,
            commentRecord = commentAtts.commentRecord,
            text = commentAtts.text
        )
    }

    private fun DbRecordChangedEvent.toCommentEvent(): CommentUpdateEvent {
        val commentAtts = recordsService.getAtts(this.record, CommentAtts::class.java)

        val textBefore = this.before[TEXT_ATT]
        val textAfter = this.after[TEXT_ATT]

        return CommentUpdateEvent(
            record = commentAtts.record,
            commentRecord = commentAtts.commentRecord,
            textBefore = textBefore?.toString(),
            textAfter = textAfter?.toString()
        )
    }

    private data class CommentAtts(
        val record: EntityRef,
        @AttName(".id")
        val commentRecord: EntityRef,
        val text: String? = null,
        val attachments: List<EntityRef>?
    )
}
