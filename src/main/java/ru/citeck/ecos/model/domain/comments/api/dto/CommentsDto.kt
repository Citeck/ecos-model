package ru.citeck.ecos.model.domain.comments.api.dto

import ru.citeck.ecos.commons.data.MLText

data class CommentTag(
    val type: CommentTagType,
    val name: MLText
)

enum class CommentTagType {
    TASK,
    ACTION,
    INTEGRATION
}
