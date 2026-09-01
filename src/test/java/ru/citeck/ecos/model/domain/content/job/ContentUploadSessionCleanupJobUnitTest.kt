package ru.citeck.ecos.model.domain.content.job

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.citeck.ecos.data.sql.content.DbContentService
import ru.citeck.ecos.data.sql.content.DbEcosContentData
import ru.citeck.ecos.data.sql.content.storage.ChunkedUploadGoneException
import ru.citeck.ecos.data.sql.content.storage.EcosContentStorageConstants
import ru.citeck.ecos.data.sql.content.storage.EcosContentStorageService
import ru.citeck.ecos.data.sql.content.upload.ContentUploadSessionStatus
import ru.citeck.ecos.data.sql.content.upload.DbContentUploadSessionEntity
import ru.citeck.ecos.data.sql.content.upload.DbContentUploadSessionService
import ru.citeck.ecos.data.sql.context.DbDataSourceContext
import ru.citeck.ecos.data.sql.context.DbSchemaContext
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.refs.DbRecordRefService
import ru.citeck.ecos.webapp.lib.spring.context.content.upload.ContentUploadProps
import ru.citeck.ecos.webapp.lib.web.webapi.exception.EcosWebException
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Plain-Mockito tests for branches [ContentUploadSessionCleanupJobTest] cannot reach: its storage
 * service is production-wired. `abortSession` / `resolveStorageRef` are `internal` for it.
 */
class ContentUploadSessionCleanupJobUnitTest {

    private fun newJob(dbDomainFactory: DbDomainFactory = mock()): ContentUploadSessionCleanupJob {
        // @PostConstruct never runs here, so ContentUploadProps serves its defaults
        return ContentUploadSessionCleanupJob(dbDomainFactory, mock(), mock(), ContentUploadProps(mock()))
    }

    private fun newSession(
        storageRef: Long,
        status: String = ContentUploadSessionStatus.ACTIVE.name,
        dataKey: String = ""
    ): DbContentUploadSessionEntity {
        val entity = DbContentUploadSessionEntity()
        entity.extId = "test-session"
        entity.status = status
        entity.storageRef = storageRef
        entity.storageState = "fake-state"
        entity.dataKey = dataKey
        return entity
    }

    private fun farFutureDeadline(): Instant = Instant.now().plus(Duration.ofHours(1))

    // --- resolveStorageRef ---

    @Test
    fun `resolveStorageRef returns null without touching the ref service when storageRef is -1`() {
        val schemaCtx = mock<DbSchemaContext>()
        val recordRefService = mock<DbRecordRefService>()
        whenever(schemaCtx.recordRefService).thenReturn(recordRefService)

        val result = newJob().resolveStorageRef(schemaCtx, newSession(-1L))

        assertThat(result).isNull()
        verify(recordRefService, never()).getExtIdById(any())
    }

    @Test
    fun `resolveStorageRef resolves a registered id to its EntityRef`() {
        val schemaCtx = mock<DbSchemaContext>()
        val recordRefService = mock<DbRecordRefService>()
        whenever(schemaCtx.recordRefService).thenReturn(recordRefService)
        whenever(recordRefService.getExtIdById(42L))
            .thenReturn(EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF.toString())

        val result = newJob().resolveStorageRef(schemaCtx, newSession(42L))

        assertThat(result).isEqualTo(EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF)
    }

    @Test
    fun `resolveStorageRef returns null for an id that no longer resolves`() {
        val schemaCtx = mock<DbSchemaContext>()
        val recordRefService = mock<DbRecordRefService>()
        whenever(schemaCtx.schema).thenReturn("ecos_data")
        whenever(schemaCtx.recordRefService).thenReturn(recordRefService)
        whenever(recordRefService.getExtIdById(999L)).thenReturn("")

        val result = newJob().resolveStorageRef(schemaCtx, newSession(999L))

        assertThat(result).isNull()
    }

    /** Folding it into "no storage ref" would delete the row without attempting the storage abort. */
    @Test
    fun `resolveStorageRef does not swallow a transient ref-service failure`() {
        val schemaCtx = mock<DbSchemaContext>()
        val recordRefService = mock<DbRecordRefService>()
        whenever(schemaCtx.recordRefService).thenReturn(recordRefService)
        whenever(recordRefService.getExtIdById(7L)).thenThrow(RuntimeException("DB blip"))

        assertThrows<RuntimeException> {
            newJob().resolveStorageRef(schemaCtx, newSession(7L))
        }
    }

    // --- abortSession: storage outcomes, run budget and quarantine ---

    private class SchemaCtxMocks(
        val schemaCtx: DbSchemaContext,
        val uploadSessionService: DbContentUploadSessionService,
        val contentService: DbContentService,
        val contentStorageService: EcosContentStorageService
    )

    private fun newSchemaCtxMocks(storageRefId: Long, quarantineApplied: Boolean = true): SchemaCtxMocks {
        val schemaCtx = mock<DbSchemaContext>()
        val recordRefService = mock<DbRecordRefService>()
        val uploadSessionService = mock<DbContentUploadSessionService>()
        val contentService = mock<DbContentService>()
        val contentStorageService = mock<EcosContentStorageService>()
        whenever(schemaCtx.schema).thenReturn("ecos_data")
        whenever(schemaCtx.recordRefService).thenReturn(recordRefService)
        whenever(schemaCtx.uploadSessionService).thenReturn(uploadSessionService)
        whenever(schemaCtx.contentService).thenReturn(contentService)
        whenever(schemaCtx.contentStorageService).thenReturn(contentStorageService)
        whenever(recordRefService.getExtIdById(storageRefId))
            .thenReturn(EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF.toString())
        // the quarantine retires the session: status *and* the handles it held into the storage
        whenever(uploadSessionService.retire(any(), any())).thenReturn(quarantineApplied)
        return SchemaCtxMocks(schemaCtx, uploadSessionService, contentService, contentStorageService)
    }

    @Test
    fun `abortSession treats a gone storage as success`() {
        val mocks = newSchemaCtxMocks(11L)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any()))
            .thenThrow(ChunkedUploadGoneException("CHUNKED_UPLOAD_GONE: already gone"))

        val result = newJob().abortSession(mocks.schemaCtx, newSession(11L), farFutureDeadline())

        assertThat(result).isTrue()
        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    @Test
    fun `abortSession leaves the row for retry on a transient storage failure`() {
        val mocks = newSchemaCtxMocks(12L)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any()))
            .thenThrow(RuntimeException("abort failed", IOException("connection reset")))

        val result = newJob().abortSession(mocks.schemaCtx, newSession(12L), farFutureDeadline())

        assertThat(result).isFalse()
        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    /** Nothing sets `retryable = true`, and a rolling restart of the storage app looks like this. */
    @Test
    fun `abortSession treats a remote error from the storage app as transient`() {
        val mocks = newSchemaCtxMocks(13L)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any())).thenThrow(
            EcosWebException.create()
                .withTargetApp("ecos-content")
                .withMessage("App is not available: 'ecos-content'")
                .build()
        )

        val result = newJob().abortSession(mocks.schemaCtx, newSession(13L), farFutureDeadline())

        assertThat(result).isFalse()
        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    /** A deterministic misconfiguration can never succeed on a retry, so it is quarantined at once. */
    @Test
    fun `abortSession quarantines a session whose abort fails permanently`() {
        val mocks = newSchemaCtxMocks(14L)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any()))
            .thenThrow(IllegalStateException("Chunked upload is not supported for local content storage"))

        val result = newJob().abortSession(mocks.schemaCtx, newSession(14L), farFutureDeadline())

        assertThat(result).isFalse()
        verify(mocks.uploadSessionService).retire(
            eq("test-session"),
            eq(ContentUploadSessionStatus.ACTIVE.name)
        )
    }

    @Test
    fun `abortSession quarantines a session after the consecutive attempt limit`() {
        val mocks = newSchemaCtxMocks(15L)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any()))
            .thenThrow(RuntimeException("abort failed", IOException("connection reset")))
        val job = newJob()
        val session = newSession(15L)

        repeat(ContentUploadSessionCleanupJob.MAX_ABORT_ATTEMPTS - 1) {
            assertThat(job.abortSession(mocks.schemaCtx, session, farFutureDeadline())).isFalse()
        }
        verify(mocks.uploadSessionService, never()).retire(any(), any())

        assertThat(job.abortSession(mocks.schemaCtx, session, farFutureDeadline())).isFalse()

        verify(mocks.uploadSessionService, times(1)).retire(
            eq("test-session"),
            eq(ContentUploadSessionStatus.ACTIVE.name)
        )
    }

    /** The limit counts *consecutive* failures: one successful abort clears the history. */
    @Test
    fun `abortSession resets the attempt counter after a successful abort`() {
        val mocks = newSchemaCtxMocks(16L)
        var failing = true
        whenever(mocks.contentStorageService.chunkedAbort(any(), any())).thenAnswer {
            if (failing) {
                throw RuntimeException("abort failed", IOException("connection reset"))
            }
            null
        }
        val job = newJob()
        val session = newSession(16L)

        repeat(ContentUploadSessionCleanupJob.MAX_ABORT_ATTEMPTS - 1) {
            assertThat(job.abortSession(mocks.schemaCtx, session, farFutureDeadline())).isFalse()
        }
        failing = false
        assertThat(job.abortSession(mocks.schemaCtx, session, farFutureDeadline())).isTrue()
        failing = true
        repeat(ContentUploadSessionCleanupJob.MAX_ABORT_ATTEMPTS - 1) {
            assertThat(job.abortSession(mocks.schemaCtx, session, farFutureDeadline())).isFalse()
        }

        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    @Test
    fun `abortSession keeps the row when the quarantine update does not apply`() {
        val mocks = newSchemaCtxMocks(17L, quarantineApplied = false)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any()))
            .thenThrow(IllegalArgumentException("bucket 'content' doesn't exist"))

        val result = newJob().abortSession(mocks.schemaCtx, newSession(17L), farFutureDeadline())

        assertThat(result).isFalse()
        verify(mocks.uploadSessionService).retire(any(), any())
    }

    /** Retrying a retired row would fail as before and keep it at the head of the queue for good. */
    @Test
    fun `abortSession deletes a retired session without touching the storage`() {
        val mocks = newSchemaCtxMocks(24L)
        val session = newSession(24L, status = ContentUploadSessionStatus.ABORTED.name)
        session.storageState = ""

        val result = newJob().abortSession(mocks.schemaCtx, session, farFutureDeadline())

        assertThat(result).isTrue()
        verify(mocks.contentStorageService, never()).chunkedAbort(any(), any())
        verify(mocks.contentStorageService, never()).deleteContent(any(), any())
        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    /** The other kind of ABORTED: a cancelled upload whose storage abort failed, so it must retry. */
    @Test
    fun `abortSession retries the storage abort of an ABORTED session that still holds one`() {
        val mocks = newSchemaCtxMocks(25L)
        val session = newSession(25L, status = ContentUploadSessionStatus.ABORTED.name)

        val result = newJob().abortSession(mocks.schemaCtx, session, farFutureDeadline())

        assertThat(result).isTrue()
        verify(mocks.contentStorageService).chunkedAbort(
            eq(EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF),
            eq("fake-state")
        )
    }

    @Test
    fun `abortSession makes no storage call once the run budget is exhausted`() {
        val mocks = newSchemaCtxMocks(18L)

        val result = newJob().abortSession(
            mocks.schemaCtx,
            newSession(18L),
            Instant.now().minus(Duration.ofSeconds(1))
        )

        assertThat(result).isFalse()
        verify(mocks.contentStorageService, never()).chunkedAbort(any(), any())
        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    @Test
    fun `abortSession quarantines a session whose status this version cannot parse`() {
        val mocks = newSchemaCtxMocks(19L)
        whenever(mocks.contentStorageService.chunkedAbort(any(), any()))
            .thenThrow(IllegalStateException("Chunked upload is not supported for local content storage"))

        val session = newSession(19L, status = "SOMETHING_A_LATER_VERSION_WRITES")
        val result = newJob().abortSession(mocks.schemaCtx, session, farFutureDeadline())

        assertThat(result).isFalse()
        // the stored value is the CAS expectation, so the transition applies without parsing it
        verify(mocks.uploadSessionService).retire(
            eq("test-session"),
            eq("SOMETHING_A_LATER_VERSION_WRITES")
        )
    }

    // --- abortSession: a session whose object is already assembled ---

    /** The dataKey is written before the record: an abort would report success and leak the object. */
    @Test
    fun `abortSession deletes the assembled object of a session that never finished`() {
        val mocks = newSchemaCtxMocks(21L)
        whenever(mocks.contentService.findContentByStorageAndDataKey(any(), any())).thenReturn(null)

        val session = newSession(21L, status = ContentUploadSessionStatus.COMPLETING.name, dataKey = "b/2026/01/01/x")
        val result = newJob().abortSession(mocks.schemaCtx, session, farFutureDeadline())

        assertThat(result).isTrue()
        verify(mocks.contentStorageService).deleteContent(
            eq(EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF),
            eq("b/2026/01/01/x")
        )
        verify(mocks.contentStorageService, never()).chunkedAbort(any(), any())
        verify(mocks.uploadSessionService, never()).retire(any(), any())
    }

    /** A live row may reference the same (storage, dataKey) pair: dedup, or a half-done completion. */
    @Test
    fun `abortSession leaves an assembled object that a content row still references`() {
        val mocks = newSchemaCtxMocks(22L)
        whenever(mocks.contentService.findContentByStorageAndDataKey(any(), any()))
            .thenReturn(mock<DbEcosContentData>())

        val session = newSession(22L, status = ContentUploadSessionStatus.COMPLETING.name, dataKey = "b/2026/01/01/x")
        val result = newJob().abortSession(mocks.schemaCtx, session, farFutureDeadline())

        assertThat(result).isTrue()
        verify(mocks.contentStorageService, never()).deleteContent(any(), any())
        verify(mocks.contentStorageService, never()).chunkedAbort(any(), any())
    }

    @Test
    fun `abortSession keeps the row when the assembled object cannot be deleted`() {
        val mocks = newSchemaCtxMocks(23L)
        whenever(mocks.contentService.findContentByStorageAndDataKey(any(), any())).thenReturn(null)
        whenever(mocks.contentStorageService.deleteContent(any(), any()))
            .thenThrow(IllegalArgumentException("bucket 'content' doesn't exist"))

        val session = newSession(23L, status = ContentUploadSessionStatus.COMPLETING.name, dataKey = "b/2026/01/01/x")
        val result = newJob().abortSession(mocks.schemaCtx, session, farFutureDeadline())

        assertThat(result).isFalse()
        verify(mocks.uploadSessionService).retire(
            eq("test-session"),
            eq(ContentUploadSessionStatus.COMPLETING.name)
        )
    }

    // --- cleanup: per-schema isolation ---

    @Test
    fun `cleanup isolates a failing schema and still processes the rest`() {
        val dataSourceCtx = mock<DbDataSourceContext>()

        val failingSchema = mock<DbSchemaContext>()
        whenever(failingSchema.schema).thenReturn("schema-a")
        whenever(failingSchema.doInNewTxn<Any?>(any())).thenThrow(RuntimeException("connection lost"))

        val healthySchema = mock<DbSchemaContext>()
        val healthyUploadSessionService = mock<DbContentUploadSessionService>()
        whenever(healthySchema.schema).thenReturn("schema-b")
        whenever(healthySchema.uploadSessionService).thenReturn(healthyUploadSessionService)
        whenever(healthySchema.doInNewTxn<Any?>(any())).thenAnswer { invocation ->
            val action = invocation.getArgument<() -> Any?>(0)
            action()
        }
        whenever(healthyUploadSessionService.cleanupExpired(any(), any(), any())).thenReturn(0)

        whenever(dataSourceCtx.getSchemaContexts()).thenReturn(listOf(failingSchema, healthySchema))
        val dbDomainFactory = mock<DbDomainFactory>()
        whenever(dbDomainFactory.getDataSourceContext()).thenReturn(dataSourceCtx)

        newJob(dbDomainFactory).cleanup()

        // the schema after the failing one still got processed, so the failure didn't abort the forEach
        verify(healthyUploadSessionService).cleanupExpired(any(), any(), any())
    }
}
