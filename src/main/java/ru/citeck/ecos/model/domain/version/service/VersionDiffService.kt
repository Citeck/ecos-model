package ru.citeck.ecos.model.domain.version.service

import org.springframework.stereotype.Service

private const val BPMN_FORMAT = "bpmn"

@Service
class VersionDiffService {

    companion object {
        private const val TEXT_TEMPLATE = "<span class=\"%s\">%s</span>"
        private const val CODE_TEMPLATE = "<code class=\"%s\">%s</code>"
    }

    fun getDiff(first: String, second: String, format: String): String {
        val diffMatchPatch = DiffMatchPatch()

        val diffMain = diffMatchPatch.diff_main(
            first,
            second
        )
        diffMatchPatch.diff_cleanupSemantic(diffMain)

        val diff = StringBuilder()

        while (!diffMain.isEmpty()) {
            val element: DiffMatchPatch.Diff = diffMain.pop()
            diff.append(formatElement(element, format))
        }

        return diff.toString()
    }

    private fun formatElement(element: DiffMatchPatch.Diff, format: String): String {
        return when (format) {
            BPMN_FORMAT -> {
                val textEscaped = element.text
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\\n", "&#10;")

                String.format(CODE_TEMPLATE, element.operation.toString(), textEscaped)
            }

            else -> {
                String.format(TEXT_TEMPLATE, element.operation.toString(), element.text)
            }
        }
    }
}
