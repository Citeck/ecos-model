package ru.citeck.ecos.model.domain.comments.api.extractor

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeVisitor
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
        private val LOCAL_REF_ELEMENTS = listOf(
            "img" to "src",
            "a" to "href"
        )
    }

    fun extractAttachRefsFromText(text: String): Map<String, EntityRef> {

        val result = LinkedHashMap<String, EntityRef>()

        extractAttachmentsRefs(extractJsonStrings(text)).forEach {
            result[it.toString()] = it
        }

        val fragment = Jsoup.parseBodyFragment(text)

        LOCAL_REF_ELEMENTS.forEach { (tag, attribute) ->

            val elements = fragment.getElementsByTag(tag)

            for (element in elements) {
                val src = element.attribute(attribute)?.value ?: ""
                if (!src.startsWith("/gateway/")) {
                    continue
                }
                val urlArgs = src.substringAfter("?", "").split("&")
                for (argument in urlArgs) {
                    if (argument.startsWith(REF_URL_PARAM_PREFIX)) {
                        val refValue = argument.substring(REF_URL_PARAM_PREFIX.length)
                        result[refValue] = EntityRef.valueOf(URLDecoder.decode(refValue, StandardCharsets.UTF_8))
                    }
                }
            }
        }

        return result
    }

    /**
     * Remove tags with local references
     */
    private fun simplifyHtmlContentWithoutLocalRefs(text: String): String {

        val document = Jsoup.parse(text)

        fun isNodeWithLocalRef(node: Element): Boolean {
            for ((tag, att) in LOCAL_REF_ELEMENTS) {
                if (tag != node.tagName()) {
                    continue
                }
                val attValue = node.attr(att) ?: ""
                if (attValue.isEmpty() || attValue.startsWith("/gateway/")) {
                    return true
                }
            }
            return false
        }

        document.body().traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is Element && node.tagName() == "body") {
                    return
                }
                if (node is Element) {
                    if (isNodeWithLocalRef(node)) {
                        node.remove()
                    }
                }
            }
        })
        document.outputSettings().prettyPrint(false)
        return document.body().html().replace("&nbsp;", " ")
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

    fun extractCommentTextForEvent(text: String): String {

        val jsonStrings = extractJsonStrings(text)

        val textWithoutJson = jsonStrings.fold(text) { acc, jsonString ->
            acc.replace(jsonString, "")
        }
        return simplifyHtmlContentWithoutLocalRefs(textWithoutJson)
    }
}
