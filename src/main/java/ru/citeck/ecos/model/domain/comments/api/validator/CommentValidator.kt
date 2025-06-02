package ru.citeck.ecos.model.domain.comments.api.validator

import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

object CommentValidator {

    private const val BASIC_URI_TO_REMOVE = "http://base-url-to-remove"

    private val ALLOWED_TAGS = setOf(
        "hr",
        "details",
        "summary"
    )

    private val ALLOWED_INLINE_STYLES = setOf(
        "font-size",
        "background-color",
        "color",
        "white-space",
        "grid-template-columns",
        "width",
        "height",
        "border",
        "vertical-align",
        "text-align",
        "padding-inline-start",
    ).map { "$it:" }

    private val ALLOWED_ATTS_FOR_ALL = setOf(
        "style",
        "class",
        "role",
        "tabindex",
        "aria-checked",
        "value",
        "data-lexical-layout-container",
        "data-lexical-layout-item",
        "spellcheck",
        "data-language",
        "data-highlight-language"
    )
    private val ALLOWED_ATTS_FOR_UL = setOf(
        "__lexicallisttype"
    )

    private val cleaner = Cleaner(
        Safelist.relaxed()
            .addTags(*ALLOWED_TAGS.toTypedArray())
            .addAttributes("p", "dir")
            .addAttributes("ul", *ALLOWED_ATTS_FOR_UL.toTypedArray())
            .addAttributes("span", "data-mention")
            .addAttributes("details", "open")
            .addAttributes(":all", *ALLOWED_ATTS_FOR_ALL.toTypedArray())
    )

    @JvmStatic
    fun removeVulnerabilities(data: String?): String {
        if (data.isNullOrBlank()) {
            return ""
        }
        val dirty = Jsoup.parseBodyFragment(
            removeNonPrintable(StringEscapeUtils.unescapeHtml4(data)),
            BASIC_URI_TO_REMOVE
        )
        val clean = cleanStyles(cleaner.clean(dirty))
        clean.outputSettings().prettyPrint(false)
        return clean.body().html().replace(BASIC_URI_TO_REMOVE, "")
    }

    private fun cleanStyles(doc: Document): Document {
        doc.select("[style]").forEach { element: Element ->
            val styleAtt = element.attr("style")
            if (styleAtt.contains("url")) {
                element.removeAttr("style")
            }
            val safeStyle = styleAtt.split(";")
                .map { it.trim() }
                .filter { style ->
                    val normalizedStyle = style.trim()
                    ALLOWED_INLINE_STYLES.any { normalizedStyle.startsWith(it) }
                }.joinToString("; ")
            element.attr("style", safeStyle)
        }
        return doc
    }

    private fun removeNonPrintable(data: String?): String {
        if (data == null) {
            return ""
        }
        return data
            .replace("\\x0C", "")
            .replace("\\x00", "")
            .replace("\\x2F", "")
            .replace("\\x20", "")
            .replace("\\x2F", "")
            .replace("\\x00", "")
    }
}
