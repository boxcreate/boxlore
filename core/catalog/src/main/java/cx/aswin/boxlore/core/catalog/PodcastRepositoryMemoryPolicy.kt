package cx.aswin.boxlore.core.catalog

internal const val MAX_SAFE_PAGE_LIMIT = 100
internal const val DEFAULT_EPISODE_CACHE_TTL_MS = 300_000L // 5 minutes
internal const val DEFAULT_RECOMMENDATIONS_CACHE_TTL_MS = 900_000L // 15 minutes

/**
 * Clamps requested pagination page size to [MAX_SAFE_PAGE_LIMIT] to prevent catastrophic memory
 * pressure and [OutOfMemoryError] on large feeds.
 */
internal fun clampPageLimit(limit: Int): Int = limit.coerceIn(1, MAX_SAFE_PAGE_LIMIT)

/**
 * Bounded, thread-safe LRU cache with time-to-live (TTL) expiry.
 * Evicts oldest accessed entries when exceeding [maxEntries] and discards expired entries upon access.
 */
internal class TimedLruCache<K : Any, V : Any>(
    private val maxEntries: Int = 25,
    private val ttlMillis: Long = DEFAULT_EPISODE_CACHE_TTL_MS,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val map =
        object : LinkedHashMap<K, Pair<V, Long>>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<V, Long>>?): Boolean =
                size > maxEntries
        }

    operator fun get(key: K): V? = synchronized(lock) {
        val entry = map[key] ?: return null
        val now = timeProvider()
        if (now - entry.second > ttlMillis) {
            map.remove(key)
            null
        } else {
            entry.first
        }
    }

    operator fun set(key: K, value: V) = synchronized(lock) {
        map[key] = Pair(value, timeProvider())
    }

    fun put(key: K, value: V) = set(key, value)

    fun invalidateIf(predicate: (K) -> Boolean) = synchronized(lock) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next().key)) {
                iterator.remove()
            }
        }
    }

    fun clear() = synchronized(lock) {
        map.clear()
    }

    fun size(): Int = synchronized(lock) {
        map.size
    }
}
