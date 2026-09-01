package ru.citeck.ecos.model.domain.content

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.config.lib.dto.ConfigKey
import ru.citeck.ecos.config.lib.provider.InMemConfigProvider
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.contentcheckout.service.ContentCheckoutService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.webapp.api.content.EcosContentApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import ru.citeck.ecos.webapp.lib.spring.context.content.upload.ContentUploadConfig
import ru.citeck.ecos.webapp.lib.spring.context.webmvc.api.EcosContentController
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * Integration tests for the chunked-upload REST surface in emodel: `EcosContentController`, the
 * limits from the live `content-upload` ecos-config record, and the permission/checkout rules around
 * the record an upload feeds into. The multi-chunk happy path needs a remote chunk-capable storage
 * and lives in ecos-data / ecos-content.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChunkedUploadIntegrationTest {

    companion object {
        private const val BASE = EcosContentController.URL_PATH
        private const val TEMP_FILE_TYPE = "temp-file"

        /** A type other than temp-file that emodel serves with its own auto-generated DbRecordsDao. */
        private const val ATTACHMENT_TYPE = "attachment"

        private const val TYPE_WITHOUT_CONTENT = "category"

        private const val UNKNOWN_TYPE = "definitely-not-a-type"

        private const val MUT_SOURCE_ID = "chunked-upload-mut-test"
        private const val MUT_TYPE_ID = "chunked-upload-mut-test"

        private const val USER_WITH_WRITE_ACCESS = "chunkUploadOwner"
        private const val USER_LOCKER = "chunkUploadLocker"
        private const val USER_NO_WRITE_ACCESS = "chunkUploadNoPerm"

        private val CONFIG_KEY = ConfigKey.create("app/emodel", "content-upload")
    }

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var dbDomainFactory: DbDomainFactory

    @Autowired
    private lateinit var inMemConfigProvider: InMemConfigProvider

    @Autowired
    private lateinit var contentCheckoutService: ContentCheckoutService

    @Autowired
    private lateinit var ecosContentApi: EcosContentApi

    @Autowired
    private lateinit var typesRegistry: EcosTypesRegistry

    private lateinit var mockMvc: MockMvc

    @BeforeAll
    fun setUp() {
        localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()

        // Step 5's DAO: its perms component denies write to a single user, so the permission and
        // checkout assertions can be isolated from each other
        val dao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(MUT_SOURCE_ID)
                        withTypeRef(ModelUtils.getTypeRef(MUT_TYPE_ID))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("test_chunked_upload_mut")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data")
            .withPermsComponent(DenyWritePermsComponent(setOf(USER_NO_WRITE_ACCESS)))
            .build()

        recordsService.register(dao)
    }

    @AfterAll
    fun tearDown() {
        recordsService.unregister(MUT_SOURCE_ID)
    }

    // ---- Step 1: init on a LOCAL-backed type answers 200 with a body, never an HTTP error ----

    @Test
    fun initOnLocalBackedStorageReturns200WithSupportedFalseAndFallbackLimits() {
        mockMvc.perform(
            post("$BASE/upload-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"step1-file.txt","size":1024,"mimeType":"text/plain","ecosType":"$TEMP_FILE_TYPE"}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.supported").value(false))
            .andExpect(jsonPath("$.reason").value("storage-not-supported"))
            .andExpect(jsonPath("$.maxSingleUploadSize").isNumber)
            .andExpect(jsonPath("$.maxFileSize").isNumber)
            .andExpect(jsonPath("$.uploadId").doesNotExist())
    }

    /** `supported:false` alone is ambiguous, so the assertions below rule out the routing branch. */
    @Test
    fun initOnAttachmentTypeResolvesItsOwnDaoAndAnswersAsACapability() {
        val tempFileDao = recordsService.getRecordsDao(TEMP_FILE_TYPE, DbRecordsDao::class.java)
        assertThat(tempFileDao).isNotNull

        val typeDef = typesRegistry.getValue(ATTACHMENT_TYPE)
        assertThat(typeDef).isNotNull
        assertThat(typeDef!!.sourceId).isEqualTo("emodel/$ATTACHMENT_TYPE")

        val attachmentDao = recordsService.getRecordsDao(ATTACHMENT_TYPE, DbRecordsDao::class.java)
        assertThat(attachmentDao).isNotNull
        assertThat(attachmentDao!!.getTableRef().schema).isEqualTo(tempFileDao!!.getTableRef().schema)

        mockMvc.perform(
            post("$BASE/upload-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"attachment-file.txt","size":1024,"mimeType":"text/plain",""" +
                        """"ecosType":"$ATTACHMENT_TYPE"}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.supported").value(false))
            .andExpect(jsonPath("$.reason").value("storage-not-supported"))
            .andExpect(jsonPath("$.maxSingleUploadSize").isNumber)
            .andExpect(jsonPath("$.maxFileSize").isNumber)
            .andExpect(jsonPath("$.uploadId").doesNotExist())
    }

    @Test
    fun initOnAnUnknownTypeReturns400() {
        mockMvc.perform(
            post("$BASE/upload-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"unknown-type-file.txt","size":1024,"mimeType":"text/plain",""" +
                        """"ecosType":"$UNKNOWN_TYPE"}"""
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(containsString(UNKNOWN_TYPE)))
    }

    /** The controller-wide `IllegalArgumentException` handler answers 400, not a 500 problem detail. */
    @Test
    fun singleShotUploadForATypeWithoutContentReturns400() {
        mockMvc.perform(
            post(BASE)
                .header(
                    EcosContentController.UPLOAD_META_HEADER,
                    """{"name":"no-content-type.txt","ecosType":"$TYPE_WITHOUT_CONTENT"}"""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(ByteArray(8))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(containsString("Content attribute is not found")))
    }

    // ---- Step 2: max-size-exceeded is produced from live ecos-config ----

    @Test
    fun maxSizeExceededIsProducedFromLiveEcosConfig() {
        val declaredSize = 2000L

        // sanity check: at the default maxFileSize (-1) the same size is not rejected for size
        mockMvc.perform(
            post("$BASE/upload-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"step2-file.txt","size":$declaredSize,"mimeType":"text/plain",""" +
                        """"ecosType":"$TEMP_FILE_TYPE"}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reason").value("storage-not-supported"))

        val loweredMaxFileSize = declaredSize - 1
        try {
            // ContentUploadProps re-reads the config on every init, it is not frozen at startup
            inMemConfigProvider.setConfig(CONFIG_KEY, ContentUploadConfig(maxFileSize = loweredMaxFileSize))

            mockMvc.perform(
                post("$BASE/upload-session")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"name":"step2-file.txt","size":$declaredSize,"mimeType":"text/plain",""" +
                            """"ecosType":"$TEMP_FILE_TYPE"}"""
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.reason").value("max-size-exceeded"))
                .andExpect(jsonPath("$.maxFileSize").value(loweredMaxFileSize))
        } finally {
            inMemConfigProvider.remove(CONFIG_KEY)
        }
    }

    // ---- Step 3: unknown uploadId is indistinguishable across every session operation ----

    @Test
    fun unknownUploadIdReturns404WithNoInformationLeakOnEveryOperation() {
        val unknownId = UUID.randomUUID().toString()

        mockMvc.perform(get("$BASE/upload-session/$unknownId"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))

        mockMvc.perform(
            post("$BASE/upload-session/$unknownId/chunk")
                .param("offset", "0")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(ByteArray(5))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))

        mockMvc.perform(post("$BASE/upload-session/$unknownId/complete"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))

        mockMvc.perform(delete("$BASE/upload-session/$unknownId"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))
    }

    /** The other road to the same answer: a live dao, but no live session. Must be indistinguishable. */
    @Test
    fun aWellFormedUploadIdWithNoLiveSessionAlsoReturns404() {
        val unknownId = "$TEMP_FILE_TYPE\$" + UUID.randomUUID()

        mockMvc.perform(get("$BASE/upload-session/$unknownId"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))

        mockMvc.perform(post("$BASE/upload-session/$unknownId/complete"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))

        mockMvc.perform(delete("$BASE/upload-session/$unknownId"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))
    }

    @Test
    fun chunkWithoutUsableContentLengthReturns400() {
        val unknownId = UUID.randomUUID().toString()

        mockMvc.perform(
            post("$BASE/upload-session/$unknownId/chunk")
                .param("offset", "0")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(ByteArray(5))
                .with(overrideContentLength(-1L))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("content-length required"))
    }

    // ---- Step 4: the single-shot POST enforces the server-side limit ----

    @Test
    fun singleShotUploadExceedingServerLimitIsRejectedAndCreatesNoRecord() {
        val overriddenLimit = 100L
        val uniqueName = "step4-oversized-${UUID.randomUUID()}"
        try {
            inMemConfigProvider.setConfig(CONFIG_KEY, ContentUploadConfig(maxSingleUploadSize = overriddenLimit))

            mockMvc.perform(
                post(BASE)
                    .header(EcosContentController.UPLOAD_META_HEADER, """{"name":"$uniqueName"}""")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(ByteArray((overriddenLimit + 1).toInt()))
            )
                .andExpect(status().isPayloadTooLarge)
                .andExpect(jsonPath("$.error").value("max-size-exceeded"))
                .andExpect(jsonPath("$.maxSingleUploadSize").value(overriddenLimit))

            val created = AuthContext.runAsSystem {
                recordsService.query(
                    RecordsQuery.create()
                        .withSourceId(TEMP_FILE_TYPE)
                        .withQuery(Predicates.eq("name", uniqueName))
                        .build()
                )
            }
            assertThat(created.getRecords()).isEmpty()
        } finally {
            inMemConfigProvider.remove(CONFIG_KEY)
        }
    }

    // ---- Step 5: versioned mutation keeps its permission and checkout rules ----

    @Test
    fun mutateContentWithoutWritePermissionIsRefused() {
        val fileRef = createMutTestRecord()
        val tempFileRef = uploadTempFile("step5-no-perm-${System.nanoTime()}.txt")

        val exception = assertThrows<RuntimeException> {
            AuthContext.runAsFull(USER_NO_WRITE_ACCESS, listOf(AuthRole.USER)) {
                recordsService.mutate(
                    fileRef,
                    mapOf(
                        "_content" to tempFileRef,
                        "version:version" to "+0.1",
                        "version:comment" to "should be refused: no write permission"
                    )
                )
            }
        }
        // pinned to the ACL failure specifically, so a broken guard cannot slip past a loose assertion
        assertThat(exception).isInstanceOf(I18nRuntimeException::class.java)
        assertThat((exception as I18nRuntimeException).messageKey).contains("permission-denied")

        // trivially true here; load-bearing in the checkout test, where the listener fires at commit
        assertThat(hasNoContent(fileRef)).isTrue()
    }

    @Test
    fun mutateWhileCheckedOutByAnotherUserIsRefusedEvenForAWriteCapableUser() {
        val fileRef = createMutTestRecord()
        val tempFileRef = uploadTempFile("step5-checkout-${System.nanoTime()}.txt")

        AuthContext.runAsFull(USER_LOCKER, listOf(AuthRole.USER)) {
            contentCheckoutService.checkout(fileRef)
        }

        try {
            val exception = assertThrows<RuntimeException> {
                // USER_WITH_WRITE_ACCESS may write, so the refusal can only be the checkout rule
                AuthContext.runAsFull(USER_WITH_WRITE_ACCESS, listOf(AuthRole.USER)) {
                    recordsService.mutate(
                        fileRef,
                        mapOf(
                            "_content" to tempFileRef,
                            "version:version" to "+0.1",
                            "version:comment" to "should be refused: checked out by another user"
                        )
                    )
                }
            }
            // pinned to the listener's phrase: a bare "checked out" matches the opposite message too
            assertThat(exception.message).contains("is checked out by")

            assertThat(hasNoContent(fileRef)).isTrue()
        } finally {
            AuthContext.runAsSystem {
                contentCheckoutService.cancelCheckout(fileRef)
            }
        }
    }

    // ---- helpers ----

    private fun createMutTestRecord(): EntityRef {
        return AuthContext.runAsSystem {
            recordsService.create(
                MUT_SOURCE_ID,
                mapOf(
                    RecordConstants.ATT_TYPE to "emodel/type@$MUT_TYPE_ID",
                    "name" to "chunked-upload-mut-test-${System.nanoTime()}"
                )
            )
        }
    }

    private fun uploadTempFile(name: String): EntityRef {
        return RequestContext.doWithCtx {
            ecosContentApi.uploadTempFile()
                .withName(name)
                .withMimeType("text/plain")
                .writeContent { writer ->
                    writer.writeStream(ByteArrayInputStream("chunked-upload-test-content".toByteArray()))
                }
        }
    }

    private fun hasNoContent(ref: EntityRef): Boolean {
        return AuthContext.runAsSystem {
            recordsService.getAtt(ref, RecordConstants.ATT_CONTENT).isNull()
        }
    }

    private fun overrideContentLength(length: Long): RequestPostProcessor {
        return RequestPostProcessor { request ->
            val spyRequest = spy(request)
            whenever(spyRequest.contentLengthLong).thenReturn(length)
            spyRequest
        }
    }

    private class DenyWritePermsComponent(private val deniedUsers: Set<String>) : DbPermsComponent {
        override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
            val canWrite = user !in deniedUsers
            return object : DbRecordPerms {
                override fun getAdditionalPerms(): Set<String> = emptySet()
                override fun getAuthoritiesWithReadPermission(): Set<String> = setOf(AuthRole.ADMIN, AuthRole.SYSTEM)
                override fun hasReadPerms(): Boolean = true
                override fun hasWritePerms(): Boolean = canWrite
                override fun hasAttWritePerms(name: String): Boolean = canWrite
                override fun hasAttReadPerms(name: String): Boolean = true
            }
        }
    }
}
