package ru.citeck.ecos.model.domain.content.job

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.data.sql.content.storage.EcosContentStorageConstants
import ru.citeck.ecos.data.sql.content.upload.ContentUploadSessionStatus
import ru.citeck.ecos.data.sql.content.upload.DbContentUploadSessionEntity
import ru.citeck.ecos.data.sql.context.DbSchemaContext
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant

/**
 * Integration test for [ContentUploadSessionCleanupJob] against the real schema context. The storage
 * cannot be mocked (the app context wires the production one), so LOCAL storage's refusal to do
 * chunked aborts drives the abort-failed and quarantine branches.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class ContentUploadSessionCleanupJobTest {

    companion object {
        private const val SCHEMA = "ecos_data"
    }

    @Autowired
    private lateinit var dbDomainFactory: DbDomainFactory

    @Autowired
    private lateinit var job: ContentUploadSessionCleanupJob

    private fun schemaCtx(): DbSchemaContext {
        return dbDomainFactory.getDataSourceContext().getSchemaContext(SCHEMA)
    }

    private fun everythingExpired(): Instant = Instant.now().plusSeconds(3600)

    private fun newSession(status: ContentUploadSessionStatus, storageRef: Long): DbContentUploadSessionEntity {
        val ctx = schemaCtx()
        val entity = DbContentUploadSessionEntity()
        entity.creator = ctx.recordRefService.getOrCreateIdByEntityRef(
            EntityRef.create("emodel", "person", "cleanup-job-test-user")
        )
        entity.status = status.name
        entity.declaredSize = 100
        entity.confirmedOffset = 0
        entity.chunkSize = 10
        entity.storageRef = storageRef
        entity.storageState = "fake-state-${System.nanoTime()}"
        // the row must outlive this test method's own transaction, same as any real caller's
        return ctx.doInNewTxn { ctx.uploadSessionService.create(entity) }
    }

    @Test
    fun cleanupDeletesTerminalSessionWithoutCallingAbort() {
        val ctx = schemaCtx()
        val localRefId = ctx.recordRefService.getOrCreateIdByEntityRef(
            EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF
        )
        // LOCAL storage's chunkedAbort always throws, so deleting a DONE session proves it was skipped
        val saved = newSession(ContentUploadSessionStatus.DONE, localRefId)

        job.cleanup(everythingExpired())

        assertThat(ctx.uploadSessionService.findByExtId(saved.extId)).isNull()
    }

    /** LOCAL storage refuses chunked aborts outright, a failure no retry can fix. */
    @Test
    fun cleanupQuarantinesActiveSessionWhoseAbortFailsPermanently() {
        val ctx = schemaCtx()
        val localRefId = ctx.recordRefService.getOrCreateIdByEntityRef(
            EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF
        )
        val saved = newSession(ContentUploadSessionStatus.ACTIVE, localRefId)

        job.cleanup(everythingExpired())

        val afterFirstRun = ctx.uploadSessionService.findByExtId(saved.extId)
        assertThat(afterFirstRun).isNotNull()
        assertThat(afterFirstRun?.status).isEqualTo(ContentUploadSessionStatus.ABORTED.name)

        job.cleanup(everythingExpired())

        assertThat(ctx.uploadSessionService.findByExtId(saved.extId)).isNull()
    }

    @Test
    fun cleanupKeepsASessionThatIsNotIdleYet() {
        val ctx = schemaCtx()
        val localRefId = ctx.recordRefService.getOrCreateIdByEntityRef(
            EcosContentStorageConstants.LOCAL_CONTENT_STORAGE_REF
        )
        val saved = newSession(ContentUploadSessionStatus.ACTIVE, localRefId)

        job.cleanup()

        assertThat(ctx.uploadSessionService.findByExtId(saved.extId)).isNotNull()
    }

    @Test
    fun cleanupDeletesActiveSessionWithNoStorageRef() {
        val ctx = schemaCtx()
        // nothing was provisioned on the storage side, so no storage abort must be attempted
        val saved = newSession(ContentUploadSessionStatus.ACTIVE, -1L)

        job.cleanup(everythingExpired())

        assertThat(ctx.uploadSessionService.findByExtId(saved.extId)).isNull()
    }

    @Test
    fun cleanupDeletesActiveSessionWithUnresolvableStorageRef() {
        val ctx = schemaCtx()
        // a ref-table id that was never registered - resolveStorageRef must handle it explicitly
        val saved = newSession(ContentUploadSessionStatus.ACTIVE, 999_999_999L)

        job.cleanup(everythingExpired())

        assertThat(ctx.uploadSessionService.findByExtId(saved.extId)).isNull()
    }
}
