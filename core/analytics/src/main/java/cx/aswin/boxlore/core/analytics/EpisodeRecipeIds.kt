package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Finds the recipe IDs of stored episode IDs off the caller's thread, for `recipe_id` on analytics
 * events. `:app` installs a Room-backed one ([AnalyticsHelper.installEpisodeRecipeIdLookup]); this
 * module never reads Room itself.
 *
 * [lookUp] must return at once and call `deliver` exactly once, later and on any thread, with the
 * IDs it found: the recipe ID of a trusted, keyed alias, never a guess. A failed lookup delivers an
 * empty map.
 *
 * Phase P4 PR 18, plan section "Identity at every boundary" (analytics).
 */
fun interface EpisodeRecipeIdLookup {
    fun lookUp(episodeIds: Set<String>, deliver: (Map<String, Long>) -> Unit)
}

/**
 * Fills `recipe_id` on the events that carry `episode_id` ([AnalyticsGlossary.EPISODE_ID_EVENTS])
 * and `recipe_ids` on episode surfaces of `home_surface_impression`, in [AnalyticsEmit]. An event
 * is never held back or slowed: an ID's recipe ID is
 *
 * 1. the ID itself when it is a recipe-range ID ([EpisodeRecipeId.isRecipeId]);
 * 2. else a value already known in memory: one a caller passed ([remember]: an episode's own
 *    `recipeId`, or a trusted alias the playback service read) or one an earlier lookup found;
 * 3. else unknown for this event, and the installed [EpisodeRecipeIdLookup] is asked in the
 *    background, so the next event of the episode carries it. An ID the lookup didn't find is not
 *    asked again for [MISS_RETRY_MS].
 *
 * So an impression asks for the surface's episodes and the tap and play that follow carry the same
 * value. Caller-provided `recipe_id` / `recipe_ids` properties are kept as they are. Bounded:
 * [MAX_KNOWN] known IDs and [MAX_MISSES] recent misses, least recently used dropped first.
 *
 * Phase P4 PR 18, plan section "Identity at every boundary" (analytics; decision D11).
 */
internal object EpisodeRecipeIds {
    const val MAX_KNOWN = 4_096
    const val MAX_MISSES = 1_024
    const val MISS_RETRY_MS = 10L * 60L * 1_000L

    private const val EPISODE_ID_PROPERTY = "episode_id"
    private const val EPISODE_IDS_PROPERTY = "episode_ids"
    private const val HOME_SURFACE_IMPRESSION = "home_surface_impression"

    private val lock = Any()
    private val known = lruMap<Long>(MAX_KNOWN)
    private val misses = lruMap<Long>(MAX_MISSES)
    private val inFlight = HashSet<String>()

    @Volatile
    private var lookup: EpisodeRecipeIdLookup? = null

    @Volatile
    var clock: () -> Long = System::currentTimeMillis

    fun install(lookup: EpisodeRecipeIdLookup?) {
        this.lookup = lookup
    }

    /** Records caller-known recipe IDs (values outside the recipe range are ignored). */
    fun remember(recipeIds: Map<String, Long?>) {
        synchronized(lock) {
            recipeIds.forEach { (id, recipeId) ->
                val key = id.trim()
                if (key.isNotEmpty() && recipeId != null && EpisodeRecipeId.isRecipeId(recipeId)) {
                    known[key] = recipeId
                    misses.remove(key)
                }
            }
        }
    }

    /** [properties] with `recipe_id` / `recipe_ids` added where they are known; see the class description. */
    fun withRecipeIds(event: String, properties: Map<String, Any>): Map<String, Any> = when {
        event in AnalyticsGlossary.EPISODE_ID_EVENTS && AnalyticsGlossary.RECIPE_ID_PROPERTY !in properties -> {
            val recipeId = (properties[EPISODE_ID_PROPERTY] as? String)?.let { recipeIdsFor(listOf(it)).firstOrNull() }
            if (recipeId == null) properties else properties + (AnalyticsGlossary.RECIPE_ID_PROPERTY to recipeId.toString())
        }
        event == HOME_SURFACE_IMPRESSION && AnalyticsGlossary.RECIPE_IDS_PROPERTY !in properties -> {
            val ids = (properties[EPISODE_IDS_PROPERTY] as? List<*>)?.filterIsInstance<String>().orEmpty()
            val recipeIds = recipeIdsFor(ids).distinct().map(Long::toString)
            if (recipeIds.isEmpty()) properties else properties + (AnalyticsGlossary.RECIPE_IDS_PROPERTY to recipeIds)
        }
        else -> properties
    }

    /** Known recipe IDs of [episodeIds], in their order; unknown ones are asked for in the background. */
    private fun recipeIdsFor(episodeIds: List<String>): List<Long> {
        if (episodeIds.isEmpty()) return emptyList()
        val keys = episodeIds.map(String::trim)
        val source = lookup
        if (source != null) {
            val missing =
                synchronized(lock) {
                    keys.filterTo(HashSet()) { ownRecipeId(it) == null && it !in known && askable(it) }.also { inFlight += it }
                }
            ask(source, missing)
        }
        return synchronized(lock) { keys.mapNotNull { ownRecipeId(it) ?: known[it] } }
    }

    /** A numeric local ID not in flight and not missed within [MISS_RETRY_MS]. Call holding [lock]. */
    private fun askable(id: String): Boolean {
        if (id.toLongOrNull() == null || id in inFlight) return false
        val missedAt = misses[id] ?: return true
        return clock() - missedAt >= MISS_RETRY_MS
    }

    /** Asks [source] for [ids], already marked in flight. */
    private fun ask(source: EpisodeRecipeIdLookup, ids: Set<String>) {
        if (ids.isEmpty()) return
        try {
            source.lookUp(ids) { found -> settle(ids, found) }
        } catch (_: RuntimeException) {
            settle(ids, emptyMap())
        }
    }

    private fun settle(asked: Set<String>, found: Map<String, Long>) {
        val now = clock()
        synchronized(lock) {
            inFlight -= asked
            asked.forEach { id ->
                val recipeId = found[id]?.takeIf(EpisodeRecipeId::isRecipeId)
                if (recipeId != null) {
                    known[id] = recipeId
                    misses.remove(id)
                } else if (id !in known) {
                    misses[id] = now
                }
            }
        }
    }

    private fun ownRecipeId(id: String): Long? = id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId)

    /** Test hook: forgets everything and removes the lookup. */
    fun reset() {
        synchronized(lock) {
            known.clear()
            misses.clear()
            inFlight.clear()
        }
        lookup = null
        clock = System::currentTimeMillis
    }

    private fun <V> lruMap(capacity: Int): LinkedHashMap<String, V> = object : LinkedHashMap<String, V>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > capacity
    }

    private const val LOAD_FACTOR = 0.75f
}
