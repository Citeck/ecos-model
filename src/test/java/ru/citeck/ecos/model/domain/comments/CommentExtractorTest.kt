package ru.citeck.ecos.model.domain.comments

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.comments.api.extractor.CommentExtractor
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class CommentExtractorTest {

    companion object {
        private val extractor = CommentExtractor()

        private val JSON_STRING_IN_COMMENT = "{\"type\":\"lexical-file-node\",\"size\": 19932,\"name\":\"test.png\"," +
            "\"fileRecordId\":\"emodel/attachment@test\"}{\"type\":\"lexical-file-node\",\"size\":19932," +
            "\"name\":\"test-2.png\",\"fileRecordId\":\"emodel/attachment@test-2\"}"

        private val COMMENT_EVENT_TEXT = "test-attachment$JSON_STRING_IN_COMMENT"
    }

    private val jsonStrings = extractor.extractJsonStrings(COMMENT_EVENT_TEXT)

    @Test
    fun extractJsonStrings() {
        assertTrue(
            jsonStrings.containsAll(
                JSON_STRING_IN_COMMENT.replace("}{", "}, {").split(", ")
            )
        )
    }

    @Test
    fun extractAttachmentsEntityRefs() {
        val attachmentsRefs = extractor.extractAttachmentsRefs(jsonStrings)

        assertTrue(
            attachmentsRefs.containsAll(
                listOf("emodel/attachment@test", "emodel/attachment@test-2").map { it.toEntityRef() }
            )
        )
    }

    @Test
    fun extractCommentTextWithoutAttachments() {
        val text = extractor.extractCommentText(jsonStrings, COMMENT_EVENT_TEXT)

        assertEquals(text, "test-attachment")
    }
}
