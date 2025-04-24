package ru.citeck.ecos.model.domain.comments

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.comments.api.extractor.CommentExtractor
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentExtractorTest {

    companion object {
        private val extractor = CommentExtractor()

        private val JSON_STRING_IN_COMMENT = "{\"type\":\"lexical-file-node\",\"size\": 19932,\"name\":\"test.png\"," +
            "\"fileRecordId\":\"emodel/attachment@test\"}{\"type\":\"lexical-file-node\",\"size\":19932," +
            "\"name\":\"test-2.png\",\"fileRecordId\":\"emodel/attachment@test-2\"}"

        private val COMMENT_EVENT_TEXT = "test-attachment$JSON_STRING_IN_COMMENT"

        private const val LEXICAL_TEXT_WITH_IMAGES = """
            <p class="PlaygroundEditorTheme__paragraph" dir="ltr">
            <span class="LEd__embedBlock" style="white-space: pre-wrap;">text</span><img
        class="PlaygroundEditorTheme__image editor-image"
        src="/gateway/emodel/api/ecos/webapp/content?ref=temp-file%4031ca7535-9b3d-4fa9-9556-ea101925abb0&amp;att=content"
        alt="image.png" width="inherit" height="inherit"></p><p class="PlaygroundEditorTheme__paragraph" dir="ltr"><span
        class="LEd__embedBlock" style="white-space: pre-wrap;">фвцфц</span></p><p
        class="PlaygroundEditorTheme__paragraph"><br></p><p class="PlaygroundEditorTheme__paragraph"><br></p><p
        class="PlaygroundEditorTheme__paragraph" dir="ltr"><span class="LEd__embedBlock" style="white-space: pre-wrap;">text</span>
</p><p class="PlaygroundEditorTheme__paragraph" dir="ltr"><br><span class="LEd__embedBlock"
                                                                    style="white-space: pre-wrap;">text</span></p><p
        class="PlaygroundEditorTheme__paragraph" dir="ltr"><span class="LEd__embedBlock" style="white-space: pre-wrap;">text</span>
</p><p class="PlaygroundEditorTheme__paragraph" dir="ltr"><span class="LEd__embedBlock" style="white-space: pre-wrap;">text</span>
</p><p class="PlaygroundEditorTheme__paragraph" dir="ltr"><span class="LEd__embedBlock" style="white-space: pre-wrap;">text</span>
</p><p class="PlaygroundEditorTheme__paragraph"><img class="PlaygroundEditorTheme__image editor-image"
                                                     src="/gateway/emodel/api/ecos/webapp/content?ref=temp-file%401c00dce5-b34d-4cb5-8706-f6393a9ff822&amp;att=content"
                                                     alt="image.png" width="inherit" height="inherit"></p><p
        class="PlaygroundEditorTheme__paragraph" dir="ltr"><br></p>
        """
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

        val attachmentsRefs2 = extractor.extractAttachRefsFromText(LEXICAL_TEXT_WITH_IMAGES)

        assertThat(attachmentsRefs2).containsAllEntriesOf(mapOf(
            "temp-file%4031ca7535-9b3d-4fa9-9556-ea101925abb0" to EntityRef.valueOf("temp-file@31ca7535-9b3d-4fa9-9556-ea101925abb0"),
            "temp-file%401c00dce5-b34d-4cb5-8706-f6393a9ff822" to EntityRef.valueOf("temp-file@1c00dce5-b34d-4cb5-8706-f6393a9ff822"),
        ))
    }

    @Test
    fun extractCommentTextWithoutAttachments() {
        val text = extractor.extractCommentText(jsonStrings, COMMENT_EVENT_TEXT)

        assertEquals(text, "test-attachment")
    }
}
