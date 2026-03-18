package ru.citeck.ecos.model.domain.contentcheckout

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.contentcheckout.service.ContentCheckoutService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContentCheckoutServiceTest {

    companion object {
        private const val USER_A = "userA"
        private const val USER_B = "userB"

        private const val TEST_SOURCE_ID = "content-checkout-test"
        private const val TEST_TYPE_ID = "content-checkout-test"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var contentCheckoutService: ContentCheckoutService

    @Autowired
    private lateinit var dbDomainFactory: DbDomainFactory

    private val refsToDelete = mutableListOf<EntityRef>()

    @BeforeAll
    fun setUp() {
        localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))

        val dao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(TEST_SOURCE_ID)
                        withTypeRef(ModelUtils.getTypeRef(TEST_TYPE_ID))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("test_content_checkout")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data").build()

        recordsService.register(dao)
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            for (ref in refsToDelete.reversed()) {
                try {
                    recordsService.delete(ref)
                } catch (_: Exception) {
                }
            }
        }
        recordsService.unregister(TEST_SOURCE_ID)
    }

    private fun createFileRecord(): EntityRef {
        val ref = AuthContext.runAsSystem {
            recordsService.create(
                TEST_SOURCE_ID,
                mapOf(
                    RecordConstants.ATT_TYPE to "emodel/type@$TEST_TYPE_ID",
                    "name" to "test-file-${System.nanoTime()}"
                )
            )
        }
        refsToDelete.add(ref)
        return ref
    }

    // --- MANUAL mode: checkout ---

    @Test
    fun manualCheckoutSetsAttributesAndAspect() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
        assertThat(contentCheckoutService.getCheckedOutBy(fileRef)).isEqualTo(USER_A)
        assertThat(contentCheckoutService.getCheckedOutMode(fileRef)).isEqualTo(ContentCheckoutService.Mode.MANUAL)

        val hasAspect = AuthContext.runAsSystem {
            recordsService.getAtt(fileRef, "_aspects._has.content-checkout?bool").asBoolean()
        }
        assertThat(hasAspect).isTrue()
    }

    @Test
    fun manualCheckoutIsIdempotentForSameUser() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
            contentCheckoutService.checkout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
        assertThat(contentCheckoutService.getCheckedOutBy(fileRef)).isEqualTo(USER_A)
    }

    @Test
    fun manualCheckoutFailsIfLockedByAnotherUser() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        val exception = assertThrows<RuntimeException> {
            runAsUser(USER_B) {
                contentCheckoutService.checkout(fileRef)
            }
        }
        assertThat(exception.message).contains("already checked out")
    }

    // --- MANUAL mode: cancelCheckout ---

    @Test
    fun manualCancelCheckoutByAuthorClearsLock() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
            contentCheckoutService.cancelCheckout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
        assertThat(contentCheckoutService.getCheckedOutBy(fileRef)).isEmpty()
        assertThat(contentCheckoutService.getCheckedOutMode(fileRef)).isNull()
    }

    @Test
    fun manualCancelCheckoutByAdminSucceeds() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        AuthContext.runAs(USER_B, listOf(AuthRole.ADMIN)) {
            contentCheckoutService.cancelCheckout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
    }

    @Test
    fun manualCancelCheckoutByOtherUserFails() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        val exception = assertThrows<RuntimeException> {
            runAsUser(USER_B) {
                contentCheckoutService.cancelCheckout(fileRef)
            }
        }
        assertThat(exception.message).contains("Only the user who checked out")
    }

    @Test
    fun cancelCheckoutOnNonLockedDocumentIsNoOp() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.cancelCheckout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
    }

    // --- MANUAL mode: checkin ---

    @Test
    fun manualCheckinByAuthorClearsLock() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        val dummyContent = EntityRef.valueOf("emodel/temp-content@dummy")
        runAsUser(USER_A) {
            contentCheckoutService.checkin(fileRef, dummyContent)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
        assertThat(contentCheckoutService.getCheckedOutBy(fileRef)).isEmpty()
    }

    @Test
    fun manualCheckinByOtherUserFails() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        val dummyContent = EntityRef.valueOf("emodel/temp-content@dummy")
        val exception = assertThrows<RuntimeException> {
            runAsUser(USER_B) {
                contentCheckoutService.checkin(fileRef, dummyContent)
            }
        }
        assertThat(exception.message).contains("Only the user who checked out")
    }

    @Test
    fun manualCheckinByAdminFails() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }

        val dummyContent = EntityRef.valueOf("emodel/temp-content@dummy")
        val exception = assertThrows<RuntimeException> {
            AuthContext.runAs(USER_B, listOf(AuthRole.ADMIN)) {
                contentCheckoutService.checkin(fileRef, dummyContent, "admin checkin", true)
            }
        }
        assertThat(exception.message).contains("Only the user who checked out")
        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
    }

    @Test
    fun checkinOnNonCheckedOutDocumentFails() {
        val fileRef = createFileRecord()

        val dummyContent = EntityRef.valueOf("emodel/temp-content@dummy")
        val exception = assertThrows<RuntimeException> {
            runAsUser(USER_A) {
                contentCheckoutService.checkin(fileRef, dummyContent)
            }
        }
        assertThat(exception.message).contains("not checked out")
    }

    // --- EDITOR mode ---

    @Test
    fun editorCheckoutSetsEditorMode() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
        assertThat(contentCheckoutService.getCheckedOutMode(fileRef)).isEqualTo(ContentCheckoutService.Mode.EDITOR)
    }

    @Test
    fun editorCheckoutAllowsMultipleUsers() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        runAsUser(USER_B) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
    }

    @Test
    fun editorCheckoutBlocksManualCheckout() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        val exception = assertThrows<RuntimeException> {
            runAsUser(USER_B) {
                contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.MANUAL)
            }
        }
        assertThat(exception.message).contains("already checked out")
        assertThat(exception.message).contains("document editor")
    }

    @Test
    fun manualCheckoutBlocksEditorCheckout() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.MANUAL)
        }

        val exception = assertThrows<RuntimeException> {
            runAsUser(USER_B) {
                contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
            }
        }
        assertThat(exception.message).contains("already checked out")
    }

    @Test
    fun editorCancelCheckoutByAnotherUserSucceeds() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        runAsUser(USER_B) {
            contentCheckoutService.cancelCheckout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
    }

    @Test
    fun editorCheckinByAnotherUserSucceeds() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        val dummyContent = EntityRef.valueOf("emodel/temp-content@dummy")
        runAsUser(USER_B) {
            contentCheckoutService.checkin(fileRef, dummyContent)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
    }

    @Test
    fun editorCancelCheckoutBySystemSucceeds() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef, ContentCheckoutService.Mode.EDITOR)
        }

        AuthContext.runAsSystem {
            contentCheckoutService.cancelCheckout(fileRef)
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
    }

    // --- full cycle ---

    @Test
    fun fullManualCycleAllowsRelock() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            contentCheckoutService.checkout(fileRef)
        }
        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()

        runAsUser(USER_A) {
            contentCheckoutService.cancelCheckout(fileRef)
        }
        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()

        runAsUser(USER_B) {
            contentCheckoutService.checkout(fileRef)
        }
        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
        assertThat(contentCheckoutService.getCheckedOutBy(fileRef)).isEqualTo(USER_B)
    }

    // --- ContentCheckoutRecordsDao ---

    @Test
    fun checkoutViaRecordsDao() {
        val fileRef = createFileRecord()

        runAsUser(USER_A) {
            recordsService.mutate(
                EntityRef.valueOf("emodel/content-checkout@"),
                ObjectData.create()
                    .set("action", "CHECKOUT")
                    .set("recordRef", fileRef)
            )
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isTrue()
        assertThat(contentCheckoutService.getCheckedOutBy(fileRef)).isEqualTo(USER_A)

        runAsUser(USER_A) {
            recordsService.mutate(
                EntityRef.valueOf("emodel/content-checkout@"),
                ObjectData.create()
                    .set("action", "CANCEL_CHECKOUT")
                    .set("recordRef", fileRef)
            )
        }

        assertThat(contentCheckoutService.isCheckedOut(fileRef)).isFalse()
    }

    private inline fun <T> runAsUser(user: String, crossinline action: () -> T): T {
        return AuthContext.runAsFull(user, listOf(AuthRole.USER)) {
            action.invoke()
        }
    }
}
