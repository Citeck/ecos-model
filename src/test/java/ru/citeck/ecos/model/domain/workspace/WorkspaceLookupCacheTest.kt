package ru.citeck.ecos.model.domain.workspace

import com.github.benmanes.caffeine.cache.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.citeck.ecos.model.domain.workspace.service.WorkspaceLookupCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class WorkspaceLookupCacheTest {

    @ParameterizedTest
    @ValueSource(strings = ["key", "matching", "all"])
    fun `invalidation prevents an in flight read from restoring stale rights`(mode: String) {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val reads = AtomicInteger()
        val cache = WorkspaceLookupCache<String, String> {
            if (reads.incrementAndGet() == 1) {
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                "old-rights"
            } else {
                "new-rights"
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val oldRead = executor.submit<String> { cache.get("user") }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
            when (mode) {
                "key" -> cache.invalidate("user")
                "matching" -> cache.invalidateMatching { it == "user" }
                "all" -> cache.invalidateAll()
            }
            assertThat(cache.get("user")).isEqualTo("new-rights")
            finish.countDown()
            assertThat(oldRead.get(5, TimeUnit.SECONDS)).isEqualTo("old-rights")
            assertThat(cache.get("user")).isEqualTo("new-rights")
            assertThat(reads.get()).isEqualTo(2)
        } finally {
            finish.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    @Test
    fun `failed load is retried and false values are cached`() {
        val reads = AtomicInteger()
        val cache = WorkspaceLookupCache<String, Boolean> {
            check(reads.incrementAndGet() != 1) { "Database unavailable" }
            false
        }
        assertThatThrownBy { cache.get("user") }.hasMessage("Database unavailable")
        assertThat(cache.get("user")).isFalse()
        assertThat(cache.get("user")).isFalse()
        assertThat(reads.get()).isEqualTo(2)
    }

    @Test
    fun `entries expire thirty seconds after write even when read repeatedly`() {
        val time = AtomicLong()
        val reads = AtomicInteger()
        val cache = WorkspaceLookupCache<String, Int>(Ticker { time.get() }) { reads.incrementAndGet() }
        assertThat(cache.get("user")).isEqualTo(1)
        time.set(TimeUnit.SECONDS.toNanos(29))
        assertThat(cache.get("user")).isEqualTo(1)
        time.set(TimeUnit.SECONDS.toNanos(30))
        assertThat(cache.get("user")).isEqualTo(2)
    }

    @Test
    fun `selective invalidation preserves other cached keys`() {
        val reads = AtomicInteger()
        val cache = WorkspaceLookupCache<String, Int> { reads.incrementAndGet() }
        assertThat(cache.get("a")).isEqualTo(1)
        assertThat(cache.get("b")).isEqualTo(2)
        cache.invalidateMatching { it == "a" }
        assertThat(cache.get("b")).isEqualTo(2)
        assertThat(cache.get("a")).isEqualTo(3)
    }
}
