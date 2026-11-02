package ru.citeck.ecos.model.domain.comments

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.comments.api.records.COMMENT_DAO_ID
import ru.citeck.ecos.model.domain.comments.api.records.COMMENT_REPO_DAO_ID
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.dao.impl.mem.InMemDataRecordsDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.UUID

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class CommentCreationPermissionsTest {

    @Autowired
    private lateinit var records: RecordsService

    private val parentsSource = "comment-permissions-parent-" + UUID.randomUUID()
    private val parents = object : InMemDataRecordsDao(parentsSource) {
        override fun getRecordAtts(recordId: String): Any? {
            val base = super.getRecordAtts(recordId) as? AttValue ?: return null
            return object : AttValueDelegate(base) {
                override fun getAtt(name: String): Any? {
                    if (name == RecordConstants.ATT_NOT_EXISTS) {
                        return !getRecords().getValue(recordId)["readable"].asBoolean() && !AuthContext.isRunAsSystemOrAdmin()
                    }
                    if (name != "permissions") {
                        return super.getAtt(name)
                    }
                    return object : AttValue {
                        override fun has(name: String): Boolean = name == "Read" && getRecords().getValue(recordId)["readable"].asBoolean()
                    }
                }
            }
        }
    }
    private val customParentsSource = "comment-custom-parent-" + UUID.randomUUID()
    private val customParents = InMemDataRecordsDao(customParentsSource)
    private val customParentRefs = mutableListOf<EntityRef>()
    private lateinit var readable: EntityRef
    private lateinit var unreadable: EntityRef

    @BeforeEach
    fun setUp() {
        records.register(parents)
        records.register(customParents)
        readable = records.create(parentsSource, mapOf("readable" to true))
        unreadable = records.create(parentsSource, mapOf("readable" to false))
        asUser {
            // Verify visibility and existing comment-read permissions independently.
            assertThat(records.getAtt(readable, "_notExists?bool").asBoolean()).isFalse()
            assertThat(records.getAtt(unreadable, "_notExists?bool").asBoolean()).isTrue()
            assertThat(records.getAtt(readable, "permissions._has.Read?bool!").asBoolean()).isTrue()
            assertThat(records.getAtt(unreadable, "permissions._has.Read?bool!").asBoolean()).isFalse()
        }
    }

    @AfterEach
    fun cleanUp() {
        AuthContext.runAsSystem {
            records.delete((listOf(readable, unreadable) + customParentRefs).flatMap { commentsFor(it) })
        }
        records.unregister(parentsSource)
        records.unregister(customParentsSource)
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID, "emodel/comment", "emodel/comment-repo"])
    fun `creating a comment on an unreadable parent fails without persisting it`(source: String) {
        val exception = assertThrows<Exception> {
            asUser { createComment(source, unreadable) }
        }
        assertThat(exception).hasStackTraceContaining("cannot create a comment on a missing or inaccessible record")
        assertThat(commentsFor(unreadable)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID])
    fun `users can create and read comments on readable records`(source: String) {
        val comment = asUser { createComment(source, readable) }
        assertThat(commentsFor(readable).map { it.getLocalId() }).containsExactly(comment.getLocalId())
        asUser { assertThat(records.getAtt(comment, "text").asText()).isEqualTo("A comment") }
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID])
    fun `a missing parent does not grant permission to create`(source: String) {
        val parent = EntityRef.create(parentsSource, "missing")
        asUser {
            assertThat(records.getAtt(parent, "_notExists?bool").asBoolean()).isTrue()
            assertThrows<Exception> { createComment(source, parent) }
        }
        assertThat(commentsFor(parent)).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("comment,false", "comment,undefined", "comment-repo,false", "comment-repo,undefined")
    fun `custom DAO without permissions supports false or undefined notExists`(source: String, notExists: String) {
        val atts = ObjectData.create().set("name", "Custom parent")
        if (notExists == "false") {
            atts.set(RecordConstants.ATT_NOT_EXISTS, false)
        }
        val parent = records.create(customParentsSource, atts).also { customParentRefs.add(it) }
        asUser {
            assertThat(records.getAtt(parent, "permissions").isNull()).isTrue()
            if (notExists == "undefined") {
                assertThat(records.getAtt(parent, RecordConstants.ATT_NOT_EXISTS).isNull()).isTrue()
            }
            assertThat(records.getAtt(parent, "_notExists?bool").asBoolean()).isFalse()
            val comment = createComment(source, parent)
            assertThat(commentsFor(parent).map { it.getLocalId() }).containsExactly(comment.getLocalId())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID])
    fun `custom DAO explicit notExists true rejects creation without permissions`(source: String) {
        val parent = records.create(customParentsSource, mapOf("_notExists" to true)).also { customParentRefs.add(it) }
        asUser {
            assertThat(records.getAtt(parent, "permissions").isNull()).isTrue()
            assertThat(records.getAtt(parent, "_notExists?bool").asBoolean()).isTrue()
            assertThrows<Exception> { createComment(source, parent) }
        }
        assertThat(commentsFor(parent)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID])
    fun `SQL records hidden by read permissions reject comment creation`(source: String) {
        // This parent is itself SQL-backed, so DbRecordsAttsDao must turn denied read access into _notExists.
        val hiddenParent = AuthContext.runAsSystem { createComment(COMMENT_REPO_DAO_ID, unreadable) }
        customParentRefs.add(hiddenParent)
        asUser {
            assertThat(records.getAtt(hiddenParent, "_notExists?bool").asBoolean()).isTrue()
            assertThrows<Exception> { createComment(source, hiddenParent) }
        }
        assertThat(commentsFor(hiddenParent)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID])
    fun `system and admin cannot create comments on a missing record`(source: String) {
        val missing = EntityRef.create(parentsSource, "missing")
        AuthContext.runAsSystem {
            assertThat(records.getAtt(missing, "_notExists?bool").asBoolean()).isTrue()
            assertThrows<Exception> { createComment(source, missing) }
        }
        AuthContext.runAs("commentAdmin", listOf(AuthRole.ADMIN)) {
            assertThrows<Exception> { createComment(source, missing) }
        }
        assertThat(commentsFor(missing)).isEmpty()
    }

    @Test
    fun `the proxy denies attachments before mutating the parent`() {
        val before = parents.getRecords().getValue(unreadable.getLocalId()).deepCopy()
        assertThrows<Exception> {
            asUser {
                records.create(
                    COMMENT_DAO_ID,
                    mapOf(
                        "record" to unreadable,
                        "text" to "With attachment",
                        "att_add_docs:documents" to listOf("emodel/attachment@not-created")
                    )
                )
            }
        }
        assertThat(commentsFor(unreadable)).isEmpty()
        assertThat(parents.getRecords().getValue(unreadable.getLocalId())).isEqualTo(before)
    }

    @Test
    fun `a denied record in a batch is checked before attachment side effects`() {
        val before = parents.getRecords().getValue(readable.getLocalId()).deepCopy()
        assertThrows<Exception> {
            asUser {
                records.mutate(
                    listOf(
                        RecordAtts(
                            EntityRef.create(COMMENT_DAO_ID, ""),
                            ObjectData.create()
                                .set("record", readable)
                                .set("text", "First")
                                .set("att_add_docs:documents", listOf("emodel/attachment@not-created"))
                        ),
                        RecordAtts(
                            EntityRef.create(COMMENT_DAO_ID, ""),
                            ObjectData.create().set("record", unreadable).set("text", "Second")
                        )
                    )
                )
            }
        }
        assertThat(commentsFor(readable)).isEmpty()
        assertThat(commentsFor(unreadable)).isEmpty()
        assertThat(parents.getRecords().getValue(readable.getLocalId())).isEqualTo(before)
    }

    @ParameterizedTest
    @ValueSource(strings = [COMMENT_DAO_ID, COMMENT_REPO_DAO_ID])
    fun `system and admin comment creation remains supported`(source: String) {
        val systemComment = AuthContext.runAsSystem { createComment(source, unreadable) }
        val adminComment = AuthContext.runAs("commentAdmin", listOf(AuthRole.ADMIN)) { createComment(source, unreadable) }
        assertThat(commentsFor(unreadable).map { it.getLocalId() })
            .containsExactlyInAnyOrder(systemComment.getLocalId(), adminComment.getLocalId())
    }

    @Test
    fun `existing comment read edit and delete permissions remain enforced`() {
        val comment = asUser { createComment(COMMENT_DAO_ID, readable) }
        asUser {
            records.mutateAtt(comment, "text", "Edited")
            assertThat(records.getAtt(comment, "text").asText()).isEqualTo("Edited")
        }
        AuthContext.runAs("anotherCommentUser", listOf(AuthRole.USER)) {
            assertThat(records.getAtt(comment, "text").asText()).isEqualTo("Edited")
            assertThrows<Exception> { records.mutateAtt(comment, "text", "Not allowed") }
            assertThrows<Exception> { records.delete(comment) }
        }
        asUser { records.delete(comment) }
        assertThat(commentsFor(readable)).isEmpty()

        val hidden = AuthContext.runAsSystem { createComment(COMMENT_DAO_ID, unreadable) }
        asUser { assertThat(records.getAtt(hidden, "text").isNull()).isTrue() }
    }

    private fun createComment(source: String, parent: EntityRef): EntityRef = records.create(source, mapOf("record" to parent, "text" to "A comment"))

    private fun commentsFor(parent: EntityRef): List<EntityRef> = AuthContext.runAsSystem {
        records.query(
            RecordsQuery.create {
                withSourceId(COMMENT_REPO_DAO_ID)
                withLanguage(PredicateService.LANGUAGE_PREDICATE)
                withQuery(Predicates.eq("record", parent))
                withMaxItems(100)
            }
        ).getRecords()
    }

    private fun <T> asUser(action: () -> T): T = AuthContext.runAs("commentTestUser", listOf(AuthRole.USER), action)
}
