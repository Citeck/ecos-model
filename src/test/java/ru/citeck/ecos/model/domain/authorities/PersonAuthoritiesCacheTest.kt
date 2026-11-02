package ru.citeck.ecos.model.domain.authorities

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.map.IMap
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.txn.lib.TxnContext
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PersonAuthoritiesCacheTest : AuthoritiesTestBase() {

    @Autowired
    lateinit var hazelcast: HazelcastInstance

    @Autowired
    lateinit var authorityService: AuthorityService

    @Test
    fun `failed best effort removal preserves the lock timeout and attaches the cleanup error`() {

        val cleanupError = IllegalStateException("Cache removal unavailable")
        val cache = object : IMap<String, Set<String>> by hazelcast.getMap("person-authorities-cache") {
            override fun remove(key: String): Set<String>? {
                throw cleanupError
            }
        }
        val locks = object : IMap<String, Boolean> by hazelcast.getMap("person-authorities-cache-locks") {
            override fun tryLock(key: String, time: Long, timeunit: TimeUnit?): Boolean = false
        }
        val failedCacheHazelcast = object : HazelcastInstance by hazelcast {
            @Suppress("UNCHECKED_CAST")
            override fun <K, V> getMap(name: String): IMap<K, V> {
                return when (name) {
                    "person-authorities-cache" -> cache as IMap<K, V>
                    "person-authorities-cache-locks" -> locks as IMap<K, V>
                    else -> hazelcast.getMap(name)
                }
            }
        }
        val service = AuthorityService(failedCacheHazelcast, recordsService)
        val exception = assertThrows<IllegalStateException> { service.resetPersonCache("cache-cleanup-failure") }
        assertThat(exception).hasMessageContaining("within 30 seconds")
        assertThat(exception.suppressed).containsExactly(cleanupError)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `contended cache lock times out without releasing another owners lock`(reset: Boolean) {

        val user = "cache-lock-timeout-$reset"
        val locks: IMap<String, Boolean> = hazelcast.getMap("person-authorities-cache-locks")
        val cache: IMap<String, Set<String>> = hazelcast.getMap("person-authorities-cache")
        if (reset) {
            cache[user] = setOf(user)
        }
        val executor = Executors.newSingleThreadExecutor()
        locks.lock(user)
        try {
            val operation = executor.submit(
                Callable {
                    if (reset) authorityService.resetPersonCache(user) else authorityService.getAuthoritiesForPerson(user)
                }
            )
            val exception = assertThrows<ExecutionException> { operation.get(45, TimeUnit.SECONDS) }
            assertThat(exception.cause).isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("within 30 seconds")
                .hasMessageContaining(user)
            assertThat(locks.isLocked(user)).isTrue()
            assertThat(cache.containsKey(user)).isFalse()
        } finally {
            locks.unlock(user)
            executor.shutdownNow()
        }
        assertThat(authorityService.getAuthoritiesForPerson(user)).contains(user)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `interrupted lock wait preserves interruption and releases a subsequently acquired lock`(reset: Boolean) {

        val user = "cache-lock-interrupt-$reset"
        val locks: IMap<String, Boolean> = hazelcast.getMap("person-authorities-cache-locks")
        val cache: IMap<String, Set<String>> = hazelcast.getMap("person-authorities-cache")
        if (reset) {
            cache[user] = setOf(user)
        }
        val executor = Executors.newSingleThreadExecutor()
        val waitingThread = AtomicReference<Thread>()
        locks.lock(user)
        var ownerLocked = true
        try {
            val operation = executor.submit(
                Callable {
                    waitingThread.set(Thread.currentThread())
                    val exception = assertThrows<IllegalStateException> {
                        if (reset) authorityService.resetPersonCache(user) else authorityService.getAuthoritiesForPerson(user)
                    }
                    assertThat(exception).hasCauseInstanceOf(InterruptedException::class.java)
                    assertThat(Thread.currentThread().isInterrupted).isTrue()
                    Thread.interrupted()
                }
            )
            await().atMost(10, TimeUnit.SECONDS).until {
                waitingThread.get()?.stackTrace?.any { it.methodName.contains("tryLock") } == true
            }
            waitingThread.get().interrupt()
            assertThat(locks.isLocked(user)).isTrue()
            // Hazelcast completes the remote invocation before reporting the interruption.
            locks.unlock(user)
            ownerLocked = false
            operation.get(10, TimeUnit.SECONDS)
            assertThat(locks.isLocked(user)).isFalse()
            assertThat(cache.containsKey(user)).isEqualTo(reset)
        } finally {
            if (ownerLocked) {
                locks.unlock(user)
            }
            executor.shutdownNow()
        }
        cache.remove(user)
        assertThat(authorityService.getAuthoritiesForPerson(user)).contains(user)
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `commit invalidates a cache load that started before commit and finishes afterwards`(admin: Boolean, member: Boolean) {

        val user = createUser(admin)
        val group = createGroup("cache-target")
        if (member) {
            addUserToGroup(user, group.getLocalId())
        }
        assertMembership(user, member)
        val loaded = CountDownLatch(1)
        val release = CountDownLatch(1)
        val commitActionsStarted = CountDownLatch(1)
        val commitActionsFinished = CountDownLatch(1)
        // Delegate to the real Records/DB pipeline, delaying only the return of the committed groups.
        val delayedRecords = object : RecordsService by recordsService {
            override fun getAtt(record: Any?, attribute: String): DataValue {
                val value = recordsService.getAtt(record, attribute)
                if (attribute == "authorityGroups[]?localId") {
                    loaded.countDown()
                    check(release.await(30, TimeUnit.SECONDS))
                }
                return value
            }
        }
        val delayedService = AuthorityService(hazelcast, delayedRecords)
        val executor = Executors.newSingleThreadExecutor()
        try {
            lateinit var reader: java.util.concurrent.Future<Set<String>>
            TxnContext.doInNewTxn {
                if (member) {
                    removeUserFromGroup(user, group.getLocalId())
                } else {
                    addUserToGroup(user, group.getLocalId())
                }
                reader = executor.submit(Callable { AuthContext.runAsSystem { delayedService.getAuthoritiesForPerson(user) } })
                assertThat(loaded.await(30, TimeUnit.SECONDS)).isTrue()
                TxnContext.doAfterCommit(-2000f, false) { commitActionsStarted.countDown() }
                TxnContext.doAfterCommit(0f, false) { commitActionsFinished.countDown() }
            }
            assertThat(commitActionsStarted.await(30, TimeUnit.SECONDS)).isTrue()
            // Allow an unlocked invalidation to finish before the old value is published.
            // A serialized invalidation instead waits for this reader and removes its result afterwards.
            commitActionsFinished.await(1, TimeUnit.SECONDS)
            release.countDown()
            assertThat(reader.get(30, TimeUnit.SECONDS).contains("GROUP_cache-target")).isEqualTo(member)
            assertThat(commitActionsFinished.await(30, TimeUnit.SECONDS)).isTrue()
            assertMembership(user, !member)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `commit invalidates membership refilled by a concurrent reader`(admin: Boolean) {

        val user = createUser(admin)
        val group = createGroup("cache-target")
        assertMembership(user, false)

        TxnContext.doInNewTxn {
            addUserToGroup(user, group.getLocalId())
            // The writer has invalidated the cache, but the other connection still sees committed data.
            assertMembership(user, false)
        }
        await().atMost(10, TimeUnit.SECONDS).untilAsserted { assertMembership(user, true) }

        TxnContext.doInNewTxn {
            removeUserFromGroup(user, group.getLocalId())
            assertMembership(user, true)
        }
        await().atMost(10, TimeUnit.SECONDS).untilAsserted { assertMembership(user, false) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `rollback discards membership cached from the writers uncommitted state`(admin: Boolean) {

        val user = createUser(admin)
        val group = createGroup("cache-target")
        assertMembership(user, false)

        assertThrows<IllegalStateException> {
            TxnContext.doInNewTxn {
                addUserToGroup(user, group.getLocalId())
                val authorities = recordsService.getAtt(AuthorityType.PERSON.getRef(user), "authorities.list[]?str").asStrList()
                assertThat(authorities).contains("GROUP_cache-target")
                error("Rollback membership addition")
            }
        }
        assertMembership(user, false)

        addUserToGroup(user, group.getLocalId())
        assertMembership(user, true)
        assertThrows<IllegalStateException> {
            TxnContext.doInNewTxn {
                removeUserFromGroup(user, group.getLocalId())
                val authorities = recordsService.getAtt(AuthorityType.PERSON.getRef(user), "authorities.list[]?str").asStrList()
                assertThat(authorities).doesNotContain("GROUP_cache-target")
                error("Rollback membership removal")
            }
        }
        assertMembership(user, true)
    }

    private fun createUser(admin: Boolean): String {
        val user = if (admin) "cache-admin" else "cache-person"
        val groups = if (admin) listOf(AuthorityType.GROUP.getRef(AuthorityGroupConstants.ADMIN_GROUP)) else emptyList()
        createPerson(user, "authorityGroups" to groups)
        assertThat(readAuthorities(user).contains("ROLE_ADMIN")).isEqualTo(admin)
        return user
    }

    private fun assertMembership(user: String, expected: Boolean) {
        val authorities = readAuthorities(user)
        assertThat(authorities.contains("ROLE_ADMIN")).isEqualTo(user == "cache-admin")
        assertThat(authorities.contains("GROUP_cache-target"))
            .describedAs("Committed membership of %s", user)
            .isEqualTo(expected)
    }

    private fun readAuthorities(user: String): List<String> {
        // A separate thread gets its own Records request, transaction and PostgreSQL connection.
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(
                Callable {
                    AuthContext.runAsSystem {
                        recordsService.getAtt(AuthorityType.PERSON.getRef(user), "authorities.list[]?str").asStrList()
                    }
                }
            ).get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }
}
