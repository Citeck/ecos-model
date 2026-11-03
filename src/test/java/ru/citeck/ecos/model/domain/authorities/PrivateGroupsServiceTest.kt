package ru.citeck.ecos.model.domain.authorities

import org.apache.commons.dbcp2.BasicDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.listener.ListenerConfig
import ru.citeck.ecos.model.domain.authorities.service.PrivateGroupsService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.test.commons.containers.TestContainers
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.task.EcosTasksApi
import ru.citeck.ecos.webapp.api.task.scheduler.EcosScheduledTask
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi
import java.sql.Connection
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PrivateGroupsServiceTest {

    enum class Reset { TIMER, GROUP_CHANGE }

    private lateinit var records: RecordsService
    private lateinit var service: PrivateGroupsService
    private lateinit var timer: (EcosScheduledTask) -> Unit
    private lateinit var listener: ListenerConfig<Unit>

    @BeforeEach
    fun setUp() {
        records = mock()
        val events = mock<EventsService>()
        val tasks = mock<EcosTasksApi>()
        val scheduler = mock<EcosTaskSchedulerApi>()
        whenever(tasks.getMainScheduler()).thenReturn(scheduler)
        whenever(scheduler.schedule(any(), any(), any())).thenAnswer {
            timer = it.getArgument(2)
            mock<EcosScheduledTask>()
        }
        whenever(events.addListener<Unit>(any<ListenerConfig.Builder<Unit>.() -> Unit>())).thenAnswer {
            listener = ListenerConfig.create(it.getArgument<ListenerConfig.Builder<Unit>.() -> Unit>(0))
            mock<ru.citeck.ecos.events2.listener.ListenerHandle>()
        }
        service = PrivateGroupsService(events, records, tasks)
        service.init()
    }

    @Test
    fun `JDBC transaction owner finishes while a competing private groups load waits for pool`() {
        val database = TestContainers.getPostgres(UUID.randomUUID()) {
            database("private_groups_cache")
            userName("test")
            password("test")
            releaseAfterTest(false)
        }
        val pool = BasicDataSource().apply {
            driverClassName = "org.postgresql.Driver"
            url = database.getConnectionString()
            username = "test"
            password = "test"
            maxTotal = 1
            defaultAutoCommit = false
            setMaxWait(Duration.ofSeconds(10))
        }
        val executor = Executors.newFixedThreadPool(2)
        val currentConnection = ThreadLocal<Connection>()
        val borrowing = CountDownLatch(1)
        whenever(records.query(any<RecordsQuery>())).thenAnswer {
            assertThat(AuthContext.isRunAsSystem()).isTrue()
            fun query(connection: Connection) {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { result -> assertThat(result.next()).isTrue() }
                }
            }
            val current = currentConnection.get()
            if (current == null) {
                borrowing.countDown()
                pool.connection.use { query(it) }
            } else {
                query(current)
            }
            result("private-team")
        }
        val held = pool.connection
        try {
            held.createStatement().use { it.execute("SELECT 1") }
            val loader = executor.submit<Set<String>> { service.getPrivateGroups() }
            assertThat(borrowing.await(5, TimeUnit.SECONDS)).isTrue()
            val owner = executor.submit<Set<String>> {
                currentConnection.set(held)
                try {
                    service.getPrivateGroups().also { held.commit() }
                } finally {
                    currentConnection.remove()
                    held.close()
                }
            }
            assertThat(owner.get(3, TimeUnit.SECONDS)).containsExactly("GROUP_private-team")
            assertThat(loader.get(3, TimeUnit.SECONDS)).containsExactly("GROUP_private-team")
            assertThat(pool.numActive).isZero()
            pool.connection.use { connection ->
                connection.createStatement().use { assertThat(it.execute("SELECT 1")).isTrue() }
            }
        } finally {
            // Release the waiting baseline loader even when the owner times out on the monitor.
            held.close()
            executor.shutdownNow()
            val stopped = executor.awaitTermination(5, TimeUnit.SECONDS)
            pool.close()
            database.release()
            assertThat(stopped).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(Reset::class)
    fun `invalidation prevents an older load from repopulating the cache`(reset: Reset) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        whenever(records.query(any<RecordsQuery>())).thenAnswer {
            if (reads.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                result("old-team")
            } else {
                result("new-team")
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val oldRead = executor.submit<Set<String>> { service.getPrivateGroups() }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            reset(reset)
            release.countDown()
            assertThat(oldRead.get(3, TimeUnit.SECONDS)).containsExactly("GROUP_old-team")
            assertThat(service.getPrivateGroups()).containsExactly("GROUP_new-team")
            assertThat(service.getPrivateGroups()).containsExactly("GROUP_new-team")
            assertThat(reads.get()).isEqualTo(2)
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(Reset::class)
    fun `completed cache entries refresh after timer or group change`(reset: Reset) {
        whenever(records.query(any<RecordsQuery>())).thenReturn(result("first"), result("second"))
        assertThat(service.getPrivateGroups()).containsExactly("GROUP_first")
        assertThat(service.getPrivateGroups()).containsExactly("GROUP_first")
        reset(reset)
        assertThat(service.getPrivateGroups()).containsExactly("GROUP_second")
        verify(records, times(2)).query(any<RecordsQuery>())
    }

    @Test
    fun `empty results are cached and a failed load is retried`() {
        whenever(records.query(any<RecordsQuery>())).thenThrow(IllegalStateException("Query failed"))
            .thenReturn(result())
        assertThrows<IllegalStateException> { service.getPrivateGroups() }
        assertThat(service.getPrivateGroups()).isEmpty()
        assertThat(service.getPrivateGroups()).isEmpty()
        verify(records, times(2)).query(any<RecordsQuery>())
    }

    private fun reset(reset: Reset) {
        when (reset) {
            Reset.TIMER -> timer(mock())
            Reset.GROUP_CHANGE -> listener.action.accept(Unit)
        }
    }

    private fun result(vararg ids: String) = RecsQueryRes<EntityRef>().apply {
        setRecords(ids.map { EntityRef.create("emodel", "authority-group", it) })
    }
}
