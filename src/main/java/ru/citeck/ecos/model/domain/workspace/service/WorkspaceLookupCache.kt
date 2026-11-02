package ru.citeck.ecos.model.domain.workspace.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration

/**
 * Loads outside cache locks: a caller may already hold the last JDBC connection while
 * another caller loading the same key is waiting for one (COREDEV-660).
 * Concurrent misses deliberately load independently in their caller's transaction.
 */
internal class WorkspaceLookupCache<K : Any, V : Any>(
    ticker: Ticker = Ticker.systemTicker(),
    private val loader: (K) -> V
) {
    private val cache = Caffeine.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(1000)
        .build<K, V>()

    private val monitor = Any()
    private var generation = 0L

    fun get(key: K): V {
        val startedAt = synchronized(monitor) {
            cache.getIfPresent(key)?.let { return it }
            generation
        }
        val value = loader(key)
        synchronized(monitor) {
            // Invalidation must also cover misses currently loading, including absent keys.
            // An older read may finish for its caller, but must not repopulate shared rights.
            if (generation == startedAt) {
                cache.put(key, value)
            }
        }
        return value
    }

    fun invalidate(key: K) = invalidateMatching { it == key }

    fun invalidateMatching(predicate: (K) -> Boolean) {
        synchronized(monitor) {
            generation++
            cache.asMap().keys.removeIf(predicate)
        }
    }

    fun invalidateAll() {
        synchronized(monitor) {
            generation++
            cache.invalidateAll()
        }
    }
}
