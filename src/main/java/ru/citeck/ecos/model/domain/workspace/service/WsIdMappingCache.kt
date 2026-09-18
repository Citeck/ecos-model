package ru.citeck.ecos.model.domain.workspace.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration

/**
 * Cache for workspace identifier mappings: workspaceId -> workspace system id and back.
 *
 * Unlike a plain `expireAfterWrite` cache, values which were not resolved live much shorter
 * than resolved ones. A mapping may fail to resolve for a reason which has nothing to do with
 * the workspace itself: a query to a records source which is not registered yet returns an
 * empty result without any error, so during the application startup "workspace is not found"
 * is indistinguishable from "workspace can't be read right now". Keeping such an answer for
 * the whole positive TTL makes every artifact of the workspace unreadable by its ref until the
 * entry expires, which is exactly what COREDEV-514 describes.
 */
object WsIdMappingCache {

    /**
     * @param isResolved must return false for values which mean "mapping is not resolved"
     *        (an empty optional, a generated fallback identifier and so on).
     * @param loader must throw when the mapping can't be read at all - nothing is cached
     *        when a Caffeine loader throws, so the next call will try again.
     */
    fun <V : Any> create(
        positiveTtl: Duration,
        negativeTtl: Duration,
        maxSize: Long,
        isResolved: (V) -> Boolean,
        ticker: Ticker = Ticker.systemTicker(),
        loader: (String) -> V
    ): LoadingCache<String, V> {
        return Caffeine.newBuilder()
            .ticker(ticker)
            .maximumSize(maxSize)
            .expireAfter(
                object : Expiry<String, V> {

                    override fun expireAfterCreate(key: String, value: V, currentTime: Long): Long {
                        return ttlOf(value)
                    }

                    override fun expireAfterUpdate(
                        key: String,
                        value: V,
                        currentTime: Long,
                        currentDuration: Long
                    ): Long {
                        return ttlOf(value)
                    }

                    override fun expireAfterRead(
                        key: String,
                        value: V,
                        currentTime: Long,
                        currentDuration: Long
                    ): Long {
                        return currentDuration
                    }

                    private fun ttlOf(value: V): Long {
                        return if (isResolved(value)) {
                            positiveTtl.toNanos()
                        } else {
                            negativeTtl.toNanos()
                        }
                    }
                }
            ).build { key: String -> loader(key) }
    }
}
