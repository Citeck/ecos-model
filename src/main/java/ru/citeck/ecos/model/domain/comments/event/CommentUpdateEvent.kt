package ru.citeck.ecos.model.domain.comments.event

import ru.citeck.ecos.webapp.api.entity.EntityRef

data class CommentUpdateEvent(
    val record: EntityRef,
    val commentRecord: EntityRef,
    val textBefore: String? = null,
    val textAfter: String? = null
) {

    companion object {
        const val TYPE = "comment-update"
    }

    init {
        validate(record, commentRecord)
    }
}

data class CommentCreateEvent(
    val record: EntityRef,
    val commentRecord: EntityRef,
    val text: String? = null
) {

    companion object {
        const val TYPE = "comment-create"
    }

    init {
        validate(record, commentRecord)
    }
}

data class CommentDeleteEvent(
    val record: EntityRef,
    val commentRecord: EntityRef,
    val text: String? = null
) {

    companion object {
        const val TYPE = "comment-delete"
    }

    init {
        validate(record, commentRecord)
    }
}

private fun validate(record: EntityRef, commentRecord: EntityRef) {
    require(record != EntityRef.EMPTY) { "record is empty" }
    require(commentRecord != EntityRef.EMPTY) { "commentRecord is empty" }
}
