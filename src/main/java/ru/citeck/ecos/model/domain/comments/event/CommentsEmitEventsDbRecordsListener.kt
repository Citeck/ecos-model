package ru.citeck.ecos.model.domain.comments.event

import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.dao.atts.DbRecord
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.webapp.api.entity.EntityRef

private const val TEXT_ATT = "text"
private const val RECORD_ATT = "record"

@Component
class CommentsEmitEventsDbRecordsListener(
    private val commentEventEmitter: CommentEventEmitter
) : DbRecordsListenerAdapter() {

    override fun onChanged(event: DbRecordChangedEvent) {
        val comment = event.toCommentEvent()
        commentEventEmitter.emitCommentUpdate(comment)
    }

    override fun onCreated(event: DbRecordCreatedEvent) {
        val comment = event.toCommentEvent()
        commentEventEmitter.emitCommentCreate(comment)
    }

    override fun onDeleted(event: DbRecordDeletedEvent) {
        val comment = event.toCommentEvent()
        commentEventEmitter.emitCommentDelete(comment)
    }

    private fun DbRecordCreatedEvent.toCommentEvent(): CommentCreateEvent {
        val dbRecord = this.record as DbRecord
        val (record, commentRecord, text) = dbRecord.parseCommentData(typeDef)

        return CommentCreateEvent(
            record = record,
            commentRecord = commentRecord,
            text = text
        )
    }

    private fun DbRecordDeletedEvent.toCommentEvent(): CommentDeleteEvent {
        val dbRecord = this.record as DbRecord
        val (record, commentRecord, text) = dbRecord.parseCommentData(typeDef)

        return CommentDeleteEvent(
            record = record,
            commentRecord = commentRecord,
            text = text
        )
    }

    private fun DbRecordChangedEvent.toCommentEvent(): CommentUpdateEvent {
        val dbRecord = this.record as DbRecord
        val (record, commentRecord) = dbRecord.parseCommentData(typeDef)

        val textBefore = this.before[TEXT_ATT]
        val textAfter = this.after[TEXT_ATT]

        return CommentUpdateEvent(
            record = record,
            commentRecord = commentRecord,
            textBefore = textBefore?.toString(),
            textAfter = textAfter?.toString()
        )
    }

    private fun DbRecord.parseCommentData(typeInfo: TypeInfo): Triple<EntityRef, EntityRef, String?> {
        val recordAtt = getAtt(RECORD_ATT) ?: error("record attribute not found")
        val commentRecord = typeInfo.createSourceRefWithId(entity.extId)
        val text = getAtt(TEXT_ATT)?.toString()

        return Triple(EntityRef.valueOf(recordAtt), commentRecord, text)
    }

    private fun TypeInfo.createSourceRefWithId(id: String): EntityRef {
        return EntityRef.valueOf("${this.sourceId}@$id")
    }
}
