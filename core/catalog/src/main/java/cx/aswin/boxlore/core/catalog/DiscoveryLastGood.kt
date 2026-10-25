package cx.aswin.boxlore.core.catalog

import android.content.SharedPreferences
import cx.aswin.boxlore.core.model.Episode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The last good answer of each `/v2` discovery request in this process, by request key, so a
 * failed request keeps what the listener saw instead of blanking the surface. Bounded LRU with no
 * expiry: it is only read after a failure.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */
internal class LastGoodCache<V : Any>(private val maxEntries: Int = DEFAULT_LAST_GOOD_ENTRIES,) {
    private val lock = Any()
    private val map =
        object : LinkedHashMap<String, V>(maxEntries, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > maxEntries
        }

    operator fun get(key: String): V? = synchronized(lock) { map[key] }

    operator fun set(key: String, value: V) {
        synchronized(lock) { map[key] = value }
    }

    fun clear() = synchronized(lock) { map.clear() }

    private companion object {
        const val DEFAULT_LAST_GOOD_ENTRIES = 16
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * The last good `/v2` personal picks per surface and country, kept across restarts. When a `/v2`
 * recommendation request fails, the v2 build shows these instead of nothing and never falls back to
 * the older v1 recommendations, whose episodes carry other IDs (decision D15).
 *
 * Episodes are stored as the answer named them (recipe IDs) and translated to local owners again
 * when read, so an alias learned since is used. A stored list that can't be read is ignored.
 * Storage: SharedPreferences file [FILE], one key per surface and country.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes; decision D15).
 * COMPAT(own-ids): the file name and keys are stable storage. Permanent: never rename (MIGRATION.md §5).
 */
internal class RecommendationsLastGood(private val preferences: () -> SharedPreferences,) {
    private val memory = LastGoodCache<LastGoodPicks>()

    fun save(surface: Surface, country: String, picks: LastGoodPicks,) {
        if (picks.episodes.isEmpty()) return
        val key = key(surface, country)
        memory[key] = picks
        val encoded = runCatching { json.encodeToString(serializer, picks.episodes) }.getOrNull() ?: return
        runCatching {
            preferences()
                .edit()
                .putString(key, encoded)
                .putString(key + FALLBACK_SUFFIX, picks.isFallback.toString())
                .apply()
        }
    }

    fun load(surface: Surface, country: String,): LastGoodPicks? {
        val key = key(surface, country)
        memory[key]?.let { return it }
        val prefs = runCatching(preferences).getOrNull() ?: return null
        val stored = runCatching { prefs.getString(key, null) }.getOrNull() ?: return null
        val episodes = runCatching { json.decodeFromString(serializer, stored) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val isFallback = runCatching { prefs.getString(key + FALLBACK_SUFFIX, null) }.getOrNull() == true.toString()
        return LastGoodPicks(episodes, isFallback).also { memory[key] = it }
    }

    /** Picks as the `/v2` answer named them; [isFallback] marks picks the server chose without personal seeds. */
    data class LastGoodPicks(val episodes: List<Episode>, val isFallback: Boolean,)

    /** The surfaces whose last picks are kept: [PERSONAL] (`/v2/recommendations`) and [HOME] (the bootstrap's picks). */
    enum class Surface(val wire: String) {
        PERSONAL("personal"),
        HOME("home"),
    }

    companion object {
        /** SharedPreferences file of the last good picks. Stable storage name: never rename. */
        const val FILE = "boxlore_discovery_last_good"

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(Episode.serializer())

        private const val FALLBACK_SUFFIX = "|fallback"

        internal fun key(surface: Surface, country: String): String = "v2_recommendations|${surface.wire}|$country"
    }
}
