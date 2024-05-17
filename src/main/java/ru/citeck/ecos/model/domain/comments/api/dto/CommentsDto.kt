package ru.citeck.ecos.model.domain.comments.api.dto

import ecos.com.fasterxml.jackson210.annotation.JsonEnumDefaultValue
import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ecos.com.fasterxml.jackson210.databind.annotation.JsonPOJOBuilder
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.i18n.I18nContext

@JsonDeserialize(builder = CommentTag.Builder::class)
data class CommentTag(
    val type: CommentTagType,
    val name: MLText
) {
    @JsonPOJOBuilder
    class Builder {

        private var type: CommentTagType = CommentTagType.UNKNOWN
        private var name: MLText = MLText.EMPTY

        fun withType(type: CommentTagType?): Builder {
            this.type = type ?: CommentTagType.UNKNOWN
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun build(): CommentTag {
            var resName = name
            if (MLText.isEmpty(resName)) {
                resName = type.defaultName
            }
            return CommentTag(type, resName)
        }
    }
}

enum class CommentTagType(defaultName: MLText = MLText.EMPTY) {
    TASK,
    ACTION,
    INTEGRATION,
    INTERNAL(
        MLText(
            I18nContext.ENGLISH to "Internal",
            I18nContext.RUSSIAN to "Внутренний"
        )
    ),

    @JsonEnumDefaultValue
    UNKNOWN;

    val defaultName: MLText

    init {
        this.defaultName = if (MLText.isEmpty(defaultName)) {
            MLText(name.lowercase().replaceFirstChar { it.uppercaseChar() })
        } else {
            defaultName
        }
    }
}
