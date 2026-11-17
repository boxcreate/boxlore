package cx.aswin.boxlore.core.catalog

internal const val MAX_SAFE_PAGE_LIMIT = 100

/**
 * Most items the extras' match list asks for (`/episodes/match`). Safe above [MAX_SAFE_PAGE_LIMIT]:
 * match items carry no description or full text (about 200 KB for 1,000).
 *
 * LEGACY(own-ids): the extras of the Podcast Index show page. Remove at sunset step S7
 * (MIGRATION.md §6) once show pages read feeds for every listener.
 */
internal const val MAX_MATCH_BASELINE = 1000
internal const val DEFAULT_EPISODE_CACHE_TTL_MS = 300_000L // 5 minutes

/** Shows whose server's list in-show search keeps at once ([PodcastRepository.showSearchCache]). */
internal const val SHOW_SEARCH_CACHE_ENTRIES = 1

/**
 * Rows of a show's server's list that in-show search keeps as full episodes, the newest first (as
 * many as one search kept before it read whole lists); older rows are kept slim ([ShowSearchScan]).
 */
internal const val SEARCH_FULL_ROWS = 1000

/** Characters of an episode's notes, tags removed, that in-show search matches on a slim row. */
internal const val SEARCH_SLIM_NOTES_CHARS = 500

/**
 * Older episodes matched by in-show search kept whole for the show searched last
 * ([PodcastRepository.showSearchMatches]), so typing on never reads their pages again: two
 * searches' worth of results.
 */
internal const val SEARCH_MATCH_EPISODES = 2 * MAX_SHOW_SEARCH_RESULTS

/** Shows and sorts whose last continuation row is remembered ([PodcastRepository.windowScanHints]). */
internal const val WINDOW_SCAN_HINTS = 8

/**
 * How long a continuation row is remembered: longer than any episode, since the next one is asked
 * for when it starts. A row that no longer fits only costs the read from the start it saved.
 */
internal const val WINDOW_SCAN_HINT_TTL_MS = 12 * 60 * 60 * 1_000L
internal const val DEFAULT_RECOMMENDATIONS_CACHE_TTL_MS = 900_000L // 15 minutes

/** The clock the catalog's shared caches age by ([PodcastRepository.episodesCache] and the rest); tests move it on. */
internal object CatalogCacheClock {
    @Volatile var timeProvider: () -> Long = System::currentTimeMillis

    fun now(): Long = timeProvider()
}

/**
 * Clamps requested pagination page size to [MAX_SAFE_PAGE_LIMIT] to prevent catastrophic memory
 * pressure and [OutOfMemoryError] on large feeds.
 */
internal fun clampPageLimit(limit: Int): Int = limit.coerceIn(1, MAX_SAFE_PAGE_LIMIT)

/**
 * Bounded, thread-safe LRU cache with time-to-live (TTL) expiry.
 * Evicts oldest accessed entries when exceeding [maxEntries] and discards expired entries upon access
 * and whenever an entry is stored.
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

    /** Stores [value] and drops expired entries, so nothing outlives its time only because it isn't read again. */
    operator fun set(key: K, value: V) = synchronized(lock) {
        val now = timeProvider()
        map.entries.removeAll { now - it.value.second > ttlMillis }
        map[key] = Pair(value, now)
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
