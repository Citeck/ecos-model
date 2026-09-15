package ru.citeck.ecos.model.domain.workspace

import com.github.benmanes.caffeine.cache.Ticker
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.citeck.ecos.model.domain.workspace.service.WsIdMappingCache
import java.time.Duration
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

/**
 * Expiration policy of workspace identifier mappings. See COREDEV-514: an unresolved mapping
 * cached for the full positive TTL made all artifacts of a workspace unreadable by their refs.
 */
class WsIdMappingCacheTest {

    companion object {
        private val POSITIVE_TTL: Duration = Duration.ofMinutes(30)
        private val NEGATIVE_TTL: Duration = Duration.ofSeconds(10)
    }

    private val ticker = FakeTicker()

    private fun createCache(loader: (String) -> Optional<String>) = WsIdMappingCache.create(
        positiveTtl = POSITIVE_TTL,
        negativeTtl = NEGATIVE_TTL,
        maxSize = 100,
        isResolved = { value: Optional<String> -> value.isPresent },
        ticker = ticker,
        loader = loader
    )

    @Test
    fun resolvedValueIsCachedForPositiveTtl() {

        val loadsCount = AtomicInteger()
        val cache = createCache {
            loadsCount.incrementAndGet()
            Optional.of("workspace-$it")
        }

        assertThat(cache.get("sys-id")).contains("workspace-sys-id")

        ticker.advance(POSITIVE_TTL.minusSeconds(1))
        cache.cleanUp()
        assertThat(cache.get("sys-id")).contains("workspace-sys-id")
        assertThat(loadsCount.get()).isEqualTo(1)

        ticker.advance(Duration.ofSeconds(2))
        cache.cleanUp()
        assertThat(cache.get("sys-id")).contains("workspace-sys-id")
        assertThat(loadsCount.get()).isEqualTo(2)
    }

    @Test
    fun unresolvedValueIsReloadedAfterNegativeTtl() {

        val loadsCount = AtomicInteger()
        val cache = createCache {
            loadsCount.incrementAndGet()
            Optional.empty()
        }

        assertThat(cache.get("sys-id")).isEmpty
        assertThat(loadsCount.get()).isEqualTo(1)

        // within the negative TTL the answer is still taken from the cache
        ticker.advance(NEGATIVE_TTL.minusSeconds(1))
        cache.cleanUp()
        assertThat(cache.get("sys-id")).isEmpty
        assertThat(loadsCount.get()).isEqualTo(1)

        // ... and right after it the mapping is checked again, long before the positive TTL
        ticker.advance(Duration.ofSeconds(2))
        cache.cleanUp()
        assertThat(cache.get("sys-id")).isEmpty
        assertThat(loadsCount.get()).isEqualTo(2)
    }

    @Test
    fun unresolvedValueDoesNotHideWorkspaceCreatedLater() {

        val resolvedValue = AtomicInteger()
        val cache = createCache {
            if (resolvedValue.get() == 0) {
                Optional.empty()
            } else {
                Optional.of("workspace-$it")
            }
        }

        assertThat(cache.get("sys-id")).isEmpty

        resolvedValue.set(1)
        ticker.advance(NEGATIVE_TTL.plusSeconds(1))
        cache.cleanUp()

        assertThat(cache.get("sys-id")).contains("workspace-sys-id")
    }

    @Test
    fun nothingIsCachedWhenLoaderFails() {

        val failuresLeft = AtomicInteger(1)
        val cache = createCache {
            if (failuresLeft.getAndDecrement() > 0) {
                error("Records source is not registered")
            }
            Optional.of("workspace-$it")
        }

        assertThatThrownBy { cache.get("sys-id") }
            .hasMessageContaining("Records source is not registered")

        // the failure was not remembered - the very next call resolves the mapping
        assertThat(cache.get("sys-id")).contains("workspace-sys-id")
    }

    private class FakeTicker : Ticker {

        @Volatile
        private var nanos: Long = 0

        override fun read(): Long = nanos

        fun advance(duration: Duration) {
            nanos += duration.toNanos()
        }
    }
}
