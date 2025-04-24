package ru.citeck.ecos.model.domain.comments.api.extractor

import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Component
class CommentExtractor {

    companion object {
        private const val REF_URL_PARAM_PREFIX = "ref="
    }

    fun extractAttachRefsFromText(text: String): Map<String, EntityRef> {

        val result = LinkedHashMap<String, EntityRef>()

        extractAttachmentsRefs(extractJsonStrings(text)).forEach {
            result[it.toString()] = it
        }

        val fragment = Jsoup.parseBodyFragment(text)

        val images = fragment.getElementsByTag("img")

        for (image in images) {
            val src = image.attribute("src").value
            val urlArgs = src.substringAfter("?", "").split("&")
            for (argument in urlArgs) {
                if (argument.startsWith(REF_URL_PARAM_PREFIX)) {
                    val refValue = argument.substring(REF_URL_PARAM_PREFIX.length)
                    result[refValue] = EntityRef.valueOf(URLDecoder.decode(refValue, StandardCharsets.UTF_8))
                }
            }
        }

        return result
    }

    fun extractJsonStrings(text: String?): List<String> {
        if (text.isNullOrBlank()) {
            return emptyList()
        }
        val jsonStrings = mutableListOf<String>()

        text.let {
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
