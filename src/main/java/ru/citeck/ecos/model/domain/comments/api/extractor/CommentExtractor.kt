package ru.citeck.ecos.model.domain.comments.api.extractor

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class CommentExtractor {

    fun extractJsonStrings(text: String?): List<String> {
        if (text.isNullOrBlank()) {
            return emptyList()
        }
        val jsonStrings = mutableListOf<String>()

        text?.let {
            val pattern = Regex("\\{.*?\\}")
            val matcher = pattern.findAll(text)
            jsonStrings.addAll(matcher.map { it.value })
        }

        return jsonStrings
    }

    fun extractAttachmentsRefs(jsonStrings: List<String>): List<EntityRef> {
        return jsonStrings.map {
            Json.mapper.read(it)?.get("fileRecordId")?.asText().toEntityRef()
        }.filter {
            it.isNotEmpty()
        }
    }

    fun extractCommentText(jsonStrings: List<String>, text: String): String {
        return jsonStrings.fold(text) { acc, jsonString ->
            acc.replace(jsonString, "")
        }
    }
}
