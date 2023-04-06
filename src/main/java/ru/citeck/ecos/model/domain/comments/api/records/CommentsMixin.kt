package ru.citeck.ecos.model.domain.comments.api.records

import ru.citeck.ecos.model.domain.comments.api.dto.CommentTag
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin
import java.time.Instant

class CommentsMixin : AttMixin {

    companion object {
        private val providedAtts = listOf(
            "edited",
            "tags"
        )

        private const val EDITED_DIFF_MS = 100
    }

    override fun getProvidedAtts(): Collection<String> {
        return providedAtts
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            "edited" -> {
                val timeData = value.getAtts(CommentTimeData::class.java)
                return timeData.modified.epochSecond - timeData.created.epochSecond > EDITED_DIFF_MS
            }

            "tags" -> {
                val tagsData = value.getAtts(CommentTagsData::class.java)
                return tagsData.tags
            }

            else -> null
        }
    }

    private data class CommentTimeData(
        @AttName(RecordConstants.ATT_CREATED)
        val created: Instant,
        @AttName(RecordConstants.ATT_MODIFIED)
        val modified: Instant
    )

    private data class CommentTagsData(
        @AttName("tags[]")
        val tags: List<CommentTag>
    )

}
