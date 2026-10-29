package ru.citeck.ecos.model.domain.workspace

import org.apache.commons.dbcp2.BasicDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.model.domain.workspace.service.CustomWorkspaceApi
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.domain.workspace.service.UserWorkspaces
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.workspace.api.WsMembershipType
import ru.citeck.ecos.test.commons.containers.TestContainers
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.sql.Connection
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CustomWorkspaceApiConcurrencyTest {

    enum class Lookup { USER_WORKSPACES, USER_MANAGER, WORKSPACE_MANAGERS }

    /**
     * One request holds the last pool connection. A concurrent cache miss starts loading and
     * waits for that connection. The connection owner must still be able to resolve the same
     * key using its existing transaction, finish and return the connection to the pool.
     */
    @ParameterizedTest
    @EnumSource(Lookup::class)
    fun `connection owner does not wait for another cache loader`(lookup: Lookup) {
        val pool = Semaphore(1)
        val waitingForConnection = CountDownLatch(1)
        val ownsConnection = ThreadLocal.withInitial { false }
        val service = mock<EmodelWorkspaceService>()
        val api = CustomWorkspaceApi(service, mock<ModelServiceFactory>())
        val expected = configure(service, lookup) {
            if (!ownsConnection.get()) {
                waitingForConnection.countDown()
                check(pool.tryAcquire(10, TimeUnit.SECONDS)) { "Pool exhausted" }
                pool.release()
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        pool.acquire()
        try {
            val loader = executor.submit<Any?> { read(api, lookup) }
            assertThat(waitingForConnection.await(5, TimeUnit.SECONDS)).isTrue()
            val owner = executor.submit<Any?> {
                ownsConnection.set(true)
                try {
                    read(api, lookup)
                } finally {
                    ownsConnection.remove()
                    pool.release()
                }
            }
            assertThat(owner.get(2, TimeUnit.SECONDS)).isEqualTo(expected)
            assertThat(loader.get(2, TimeUnit.SECONDS)).isEqualTo(expected)
            assertThat(pool.availablePermits()).isEqualTo(1)
            assertThat(read(api, lookup)).isEqualTo(expected)
        } finally {
            // Also release the loader on the unfixed implementation, so a failed assertion
            // cannot leave a JVM thread stuck inside Caffeine's uninterruptible key lock.
            pool.release()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(Lookup::class)
    fun `JDBC transaction owner can finish while another loader waits for pool`(lookup: Lookup) {
        val database = TestContainers.getPostgres(UUID.randomUUID()) {
            database("workspace_cache")
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
        val waitingForConnection = CountDownLatch(1)
        val service = mock<EmodelWorkspaceService>()
        val api = CustomWorkspaceApi(service, mock<ModelServiceFactory>())
        val expected = configure(service, lookup) {
            fun query(connection: Connection) {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { result ->
                        check(result.next() && result.getInt(1) == 1)
                    }
                }
            }
            val existing = currentConnection.get()
            if (existing != null) {
                query(existing)
            } else {
                waitingForConnection.countDown()
                pool.connection.use { query(it) }
            }
        }
        val held = pool.connection
        try {
            held.createStatement().use { stmt ->
                stmt.executeQuery("SELECT version()").use { result ->
                    check(result.next())
                    println("COREDEV-660 JDBC regression: maxTotal=1, autoCommit=false, " + result.getString(1))
                }
            }
            val loader = executor.submit<Any?> { read(api, lookup) }
            assertThat(waitingForConnection.await(5, TimeUnit.SECONDS)).isTrue()
            val owner = executor.submit<Any?> {
                currentConnection.set(held)
                try {
                    read(api, lookup).also { held.commit() }
                } finally {
                    currentConnection.remove()
                    held.close()
                }
            }
            assertThat(owner.get(3, TimeUnit.SECONDS)).isEqualTo(expected)
            assertThat(loader.get(3, TimeUnit.SECONDS)).isEqualTo(expected)
            assertThat(pool.numActive).isZero()
            // A fresh database operation must succeed after the competing requests finish.
            pool.connection.use { connection ->
                connection.createStatement().use { stmt ->
                    assertThat(stmt.execute("SELECT 1")).isTrue()
                }
            }
        } finally {
            held.close()
            executor.shutdownNow()
            val terminated = executor.awaitTermination(5, TimeUnit.SECONDS)
            pool.close()
            database.release()
            assertThat(terminated).isTrue()
        }
    }

    private fun configure(service: EmodelWorkspaceService, lookup: Lookup, beforeRead: () -> Unit): Any {
        return when (lookup) {
            Lookup.USER_WORKSPACES -> {
                val result = setOf("workspace")
                whenever(service.getUserWorkspaces(any(), any(), any(), anyOrNull(), any(), any())).doAnswer {
                    beforeRead()
                    UserWorkspaces(result, 1)
                }
                result
            }
            Lookup.USER_MANAGER -> {
                whenever(service.isUserManagerOf(any(), any())).doAnswer {
                    beforeRead()
                    true
                }
                true
            }
            Lookup.WORKSPACE_MANAGERS -> {
                val result = setOf(EntityRef.valueOf("emodel/person@user"))
                whenever(service.getWorkspaceManagersRefs(any())).doAnswer {
                    beforeRead()
                    result
                }
                result
            }
        }
    }

    private fun read(api: CustomWorkspaceApi, lookup: Lookup): Any? {
        return when (lookup) {
            Lookup.USER_WORKSPACES -> api.getUserWorkspaces("user", WsMembershipType.ALL)
            Lookup.USER_MANAGER -> api.isUserManagerOf("user", "workspace")
            Lookup.WORKSPACE_MANAGERS -> api.getWorkspaceManagersRefs("workspace")
        }
    }
}
