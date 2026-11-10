package ru.citeck.ecos.model.domain.txn

import org.apache.commons.dbcp2.BasicDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.data.sql.datasource.DbDataSourceImpl
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.test.commons.EcosWebAppApiMock
import ru.citeck.ecos.test.commons.containers.TestContainers
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.txn.lib.commit.CommitCoordinatorImpl
import ru.citeck.ecos.txn.lib.commit.TxnCommitData
import ru.citeck.ecos.txn.lib.commit.repo.TwoPhaseCommitStatus
import ru.citeck.ecos.txn.lib.manager.TransactionManagerImpl
import ru.citeck.ecos.txn.lib.transaction.TxnId
import ru.citeck.ecos.txn.lib.transaction.xid.EcosXid
import ru.citeck.ecos.webapp.api.datasource.JdbcDataSource
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.spring.context.datasource.jdbc.JdbcDataSourceProperties
import ru.citeck.ecos.webapp.lib.spring.context.datasource.jdbc.JdbcDataSourceTypeFactory
import java.util.UUID
import javax.sql.DataSource
import javax.sql.XADataSource
import javax.transaction.xa.XAResource

/**
 * Verifies journal and mapping durability with an isolated PostgreSQL database and a one-connection pool.
 */
@SpringBootTest(classes = [EcosModelApp::class])
class NestedTransactionJournalTest {

    @Autowired
    private lateinit var domainFactory: DbDomainFactory

    @Autowired
    private lateinit var discovery: WebAppDiscoveryService

    @Test
    fun `journal decision survives caller rollback with the only base connection occupied`() {
        withDatabase { fixture ->
            val repo = fixture.repo
            val source = fixture.source
            val id = TxnId.create("emodel", "journal-probe")
            val data = TxnCommitData(mapOf("emodel" to emptySet()), emptyMap())

            // Initialize the real journal schema before taking the only base connection.
            repo.beforePrepare(id, data)
            repo.afterRollback(id, setOf("emodel"), emptyMap())
            val raw = source.unwrap(BasicDataSource::class.java)

            val failure = assertThrows<IllegalStateException> {
                TxnContext.doInNewTxn {
                    source.connection.use { conn ->
                        conn.createStatement().use { it.executeUpdate("INSERT INTO journal_probe VALUES (1)") }
                    }
                    assertThat(raw.numActive).isEqualTo(1)
                    repo.beforePrepare(id, data)
                    repo.beforeCommit(id, setOf("emodel"))
                    assertThat(raw.numActive).isEqualTo(1)
                    throw IllegalStateException("rollback caller")
                }
            }

            assertThat(failure).hasMessage("rollback caller")
            assertThat(repo.getRecoveryData(id)?.status).isEqualTo(TwoPhaseCommitStatus.COMMITTING)
            assertThat(count(source, "SELECT count(*) FROM journal_probe")).isZero()
            assertThat(raw.numActive).isZero()
            repo.afterCommit(id, setOf("emodel"), emptyMap())
            assertThat(repo.getRecoveryData(id)).isNull()
        }
    }

    @Test
    fun `record ID mapping survives caller rollback and cache invalidation with a saturated pool`() {
        withDatabase { fixture ->
            val source = fixture.source
            val mappings = fixture.domainFactory.getSchemaContext("public").recordRefService
            mappings.createTableIfNotExists()
            val ref = EntityRef.valueOf("emodel/nesting-probe@" + UUID.randomUUID())
            var id = -1L

            val failure = assertThrows<IllegalStateException> {
                TxnContext.doInNewTxn {
                    source.connection.use { conn ->
                        conn.createStatement().use { it.executeUpdate("INSERT INTO journal_probe VALUES (1)") }
                    }
                    id = mappings.getOrCreateIdByEntityRef(ref)
                    assertThat(id).isPositive()
                    throw IllegalStateException("rollback record creation")
                }
            }
            assertThat(failure).hasMessage("rollback record creation")
            assertThat(count(source, "SELECT count(*) FROM journal_probe")).isZero()

            mappings.invalidate(ref.toString())
            assertThat(mappings.getIdByExtId(ref.toString())).isEqualTo(id)
            assertThat(mappings.findById(id)?.extId).isEqualTo(ref.toString())
            assertThat(source.unwrap(BasicDataSource::class.java).numActive).isZero()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["prepare-one", "prepare-all", "commit-none", "commit-one", "rollback-one"])
    fun `fresh manager recovers durable decisions after XA connection loss`(window: String) {
        withDatabase { fixture ->
            val repo = fixture.repo
            val source = fixture.source
            val id = TxnId.create("emodel", "lost-instance")
            val xids = (1..2).map { EcosXid.create(id, "emodel", "branch-$it") }
            repo.beforePrepare(id, TxnCommitData(mapOf("emodel" to xids.toSet()), emptyMap()))
            val branchesToPrepare = if (window == "prepare-one") {
                1
            } else {
                2
            }
            prepareBranches(fixture.xaSource, xids.take(branchesToPrepare))

            assertThat(count(source, "SELECT count(*) FROM pg_prepared_xacts WHERE database=current_database()"))
                .isEqualTo(branchesToPrepare)

            val commit = window.startsWith("commit")
            if (commit) {
                repo.beforeCommit(id, setOf("emodel"))
            }
            if (window == "rollback-one") {
                repo.beforeRollback(id, setOf("emodel"))
            }

            if (window.endsWith("-one") && window != "prepare-one") {
                // The resource finished, but its success reply never reached the journal.
                val xa = fixture.xaSource.xaConnection
                try {
                    if (commit) {
                        xa.xaResource.commit(xids[0], false)
                    } else {
                        xa.xaResource.rollback(xids[0])
                    }
                } finally {
                    xa.close()
                }
            }

            val manager = fixture.newManager()
            assertThat(manager.getTransactionOrNull(id)).isNull()
            makeRecoveryDue(source, id)

            CommitCoordinatorImpl(repo, manager).runTxnRecovering()
            assertThat(repo.getRecoveryData(id)).isNull()
            assertThat(count(source, "SELECT count(*) FROM pg_prepared_xacts WHERE database=current_database()")).isZero()
            val expectedRows = if (commit) {
                2
            } else {
                0
            }
            assertThat(count(source, "SELECT count(*) FROM journal_probe")).isEqualTo(expectedRows)
        }
    }

    @Test
    fun `fixture restores context and closes remaining resources after setup and cleanup fail`() {
        val previous = TxnContext.getManager()
        val setupFailure = IllegalStateException("fixture initialization failed")
        val closeFailure = IllegalStateException("fixture cleanup failed")
        val pool = BasicDataSource()
        val app = EcosWebAppApiMock("fixture-cleanup")
        val manager = TransactionManagerImpl()

        val thrown = assertThrows<IllegalStateException> {
            FixtureResources().use { resources ->
                resources.onClose { TxnContext.setManager(previous) }
                resources.onClose { pool.close() }
                resources.onClose { app.dispose() }
                resources.onClose { manager.shutdown() }
                manager.init(app)
                TxnContext.setManager(manager)
                resources.onClose { throw closeFailure }
                throw setupFailure
            }
        }

        assertThat(thrown).isSameAs(setupFailure)
        assertThat(thrown.suppressed).containsExactly(closeFailure)
        assertThat(TxnContext.getManager()).isSameAs(previous)
        assertThat(pool.isClosed).isTrue()
    }

    @Test
    fun `nested rollback cleans the real journal before the outer transaction releases its connection`() {
        withDatabase { fixture ->
            val source = fixture.source
            val failure = IllegalStateException("rollback child")
            val outerFailure = IllegalStateException("rollback outer")
            var childId = TxnId.EMPTY

            val outerThrown = assertThrows<IllegalStateException> {
                TxnContext.doInNewTxn {
                    insert(source, 100)
                    val childThrown = assertThrows<IllegalStateException> {
                        TxnContext.doInNewTxn {
                            childId = TxnContext.getTxn().getId()
                            insert(source, 1)
                            throw failure
                        }
                    }
                    assertThat(childThrown).isSameAs(failure)
                    assertThat(childThrown.suppressed).isEmpty()
                    assertThat(fixture.repo.getRecoveryData(childId)).isNull()
                    assertThat(source.unwrap(BasicDataSource::class.java).numActive).isEqualTo(1)
                    throw outerFailure
                }
            }

            assertThat(outerThrown).isSameAs(outerFailure)
            assertThat(outerThrown.suppressed).isEmpty()
            assertThat(count(source, "SELECT count(*) FROM journal_probe")).isZero()
        }
    }

    @Test
    fun `nested two phase commit cleans the real journal while the outer transaction retains its connection`() {
        withDatabase { fixture ->
            val source = fixture.source
            val secondSource = fixture.newDataSource()
            val outerFailure = IllegalStateException("rollback outer")
            var childId = TxnId.EMPTY

            val thrown = assertThrows<IllegalStateException> {
                TxnContext.doInNewTxn {
                    insert(source, 100)
                    TxnContext.doInNewTxn {
                        childId = TxnContext.getTxn().getId()
                        insert(source, 1)
                        insert(secondSource, 2)
                    }
                    assertThat(fixture.repo.getRecoveryData(childId)).isNull()
                    assertThat(source.unwrap(BasicDataSource::class.java).numActive).isEqualTo(1)
                    throw outerFailure
                }
            }

            assertThat(thrown).isSameAs(outerFailure)
            assertThat(thrown.suppressed).isEmpty()
            assertThat(count(source, "SELECT count(*) FROM journal_probe")).isEqualTo(2)
            assertThat(count(source, "SELECT count(*) FROM pg_prepared_xacts WHERE database=current_database()")).isZero()
        }
    }

    private fun insert(source: DataSource, id: Int) {
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO journal_probe VALUES ($id)")
            }
        }
    }

    private fun prepareBranches(source: XADataSource, xids: List<EcosXid>) {
        xids.forEachIndexed { index, xid ->
            val xa = source.xaConnection
            try {
                xa.xaResource.start(xid, XAResource.TMNOFLAGS)
                xa.connection.createStatement().use { statement ->
                    statement.executeUpdate("INSERT INTO journal_probe VALUES (${index + 1})")
                }
                xa.xaResource.end(xid, XAResource.TMSUCCESS)
                assertThat(xa.xaResource.prepare(xid)).isEqualTo(XAResource.XA_OK)
            } finally {
                // Disconnect the original owner: only durable PostgreSQL branches remain.
                xa.close()
            }
        }
    }

    private fun makeRecoveryDue(source: DataSource, id: TxnId) {
        // Advance only this fixture's retry time; recovery itself uses the real coordinator.
        val sql = "UPDATE ecos_txn_2pc_data SET __recovery_time = now() - interval '1 minute' WHERE __txn_id = ?"
        source.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, id.toString())
                assertThat(statement.executeUpdate()).isEqualTo(1)
            }
            connection.commit()
        }
    }

    private fun withDatabase(action: (JournalFixture) -> Unit) {
        FixtureResources().use { resources ->
            val previous = TxnContext.getManager()
            resources.onClose { TxnContext.setManager(previous) }

            val database = TestContainers.getPostgres(UUID.randomUUID()) {
                database("nested_journal")
                userName("test")
                password("test")
                releaseAfterTest(false)
            }
            resources.onClose { database.release() }

            val app = EcosWebAppApiMock("emodel")
            resources.onClose { app.dispose() }
            val repo = EmodelTwoPhaseCommitCoordinatorRepo().apply { setEcosWebAppApi(app) }

            fun createManager(): TransactionManagerImpl {
                val manager = TransactionManagerImpl()
                resources.onClose { manager.shutdown() }
                manager.init(app, twoPhaseCommitRepo = repo)
                TxnContext.setManager(manager)
                return manager
            }

            fun createSource(manager: TransactionManagerImpl): JdbcDataSource {
                val properties = JdbcDataSourceProperties(
                    url = database.getConnectionString(),
                    username = "test",
                    password = "test",
                    xa = true,
                    maxPoolSize = 1,
                    minIdle = 0,
                    maxWaitMillis = 5000
                )
                val source = JdbcDataSourceTypeFactory(app.getProperties(), manager).create("journal-probe", properties)
                resources.onClose { (source as AutoCloseable).close() }
                return source
            }

            val manager = createManager()
            val jdbc = createSource(manager)
            val derived = domainFactory.withDataSource(DbDataSourceImpl(jdbc))
            resources.onClose { derived.stopBackgroundTasks() }
            repo.init(derived, discovery)
            val source = jdbc.getJavaDataSource()

            source.connection.use { conn ->
                conn.createStatement().use { it.executeUpdate("CREATE TABLE journal_probe (id INTEGER PRIMARY KEY)") }
                conn.commit()
            }
            val fixture = JournalFixture(
                repo = repo,
                source = source,
                xaSource = database.getXaDataSource(),
                domainFactory = derived,
                newDataSource = { createSource(manager).getJavaDataSource() },
                newManager = {
                    val recoveryManager = createManager()
                    createSource(recoveryManager)
                    recoveryManager
                }
            )
            action(fixture)
        }
    }

    private fun count(source: DataSource, sql: String): Int {
        return source.connection.use { connection ->
            val value = connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    check(rows.next())
                    rows.getInt(1)
                }
            }
            connection.rollback()
            value
        }
    }

    private class FixtureResources : AutoCloseable {

        private val cleanup = ArrayDeque<() -> Unit>()

        fun onClose(action: () -> Unit) {
            cleanup.addFirst(action)
        }

        override fun close() {
            var failure: Throwable? = null
            while (cleanup.isNotEmpty()) {
                try {
                    cleanup.removeFirst().invoke()
                } catch (error: Throwable) {
                    if (failure == null) {
                        failure = error
                    } else {
                        failure.addSuppressed(error)
                    }
                }
            }
            failure?.let { throw it }
        }
    }

    private class JournalFixture(
        val repo: EmodelTwoPhaseCommitCoordinatorRepo,
        val source: DataSource,
        val xaSource: XADataSource,
        val domainFactory: DbDomainFactory,
        val newDataSource: () -> DataSource,
        val newManager: () -> TransactionManagerImpl
    )
}
