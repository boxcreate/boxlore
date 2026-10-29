package cx.aswin.boxlore.core.rss

import android.annotation.SuppressLint
import android.content.SharedPreferences
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Podcast Index shows that Podcast Index renumbered: show details asked for an old show ID answered
 * with another show ID (the same feed under a new number). Each entry maps the old ID to the ID it
 * answered as. The phone keeps every stored ID where it is: the follow moves to the new show
 * (`RenumberedShowFollows` in `:core:catalog`), and the old show's history, queue and download IDs
 * join the new show's episodes as aliases ([RenumberedShowLinker]). docs/EPISODE_IDS.md §5,
 * renumbered shows.
 *
 * Only canonical numeric Podcast Index show IDs are recorded ([EpisodeRecipeId.showIdOrNull]); `rss:`
 * shows never are. Each entry is the latest answer for its old ID: recording `B → A` after
 * `A → B` replaces the first, so a show Podcast Index numbers back never loops.
 */
interface RenumberedShows {
    /** Old show ID → the show ID it answered as, for every recorded show. */
    fun all(): Map<String, String>

    /**
     * Records that [oldId] answered as [newId]; false when it isn't a renumbering (the same ID, a
     * non-numeric ID) or couldn't be saved. Saving an entry already recorded changes nothing.
     */
    fun record(oldId: String, newId: String): Boolean

    /** The show [podcastId] now answers as, following later renumberings; null when it wasn't renumbered. */
    fun currentId(podcastId: String): String? = RenumberedShowIds.currentId(all(), podcastId)

    /** Every show recorded as renumbered into [podcastId], directly or through another renumbering. */
    fun formerIds(podcastId: String): Set<String> = RenumberedShowIds.formerIds(all(), podcastId)

    companion object {
        /** Records nothing; callers built without the app's wiring. */
        val NONE: RenumberedShows =
            object : RenumberedShows {
                override fun all(): Map<String, String> = emptyMap()

                override fun record(oldId: String, newId: String): Boolean = false
            }
    }
}

/**
 * The pure rules of [RenumberedShows]: which pairs are renumberings, and how chains resolve.
 *
 * Added for renumbered shows (docs/EPISODE_IDS.md §5, renumbered shows).
 */
object RenumberedShowIds {
    /** Longest chain followed; a longer one (never seen) stops there instead of looping. */
    private const val MAX_CHAIN = 8

    /** True when an answer for [oldId] naming [newId] means Podcast Index renumbered the show. */
    fun isRenumbering(oldId: String, newId: String): Boolean =
        oldId != newId && EpisodeRecipeId.showIdOrNull(oldId) != null && EpisodeRecipeId.showIdOrNull(newId) != null

    /** [entries] with `oldId → newId` recorded: the reverse entry (a show numbered back) is dropped. */
    fun withEntry(entries: Map<String, String>, oldId: String, newId: String): Map<String, String> {
        val updated = LinkedHashMap(entries)
        if (updated[newId] == oldId) updated.remove(newId)
        updated[oldId] = newId
        return updated
    }

    fun currentId(entries: Map<String, String>, podcastId: String): String? {
        var current = entries[podcastId] ?: return null
        val seen = hashSetOf(podcastId, current)
        repeat(MAX_CHAIN) {
            val next = entries[current] ?: return current
            if (!seen.add(next)) return current
            current = next
        }
        return current
    }

    fun formerIds(entries: Map<String, String>, podcastId: String): Set<String> =
        entries.keys.filterTo(LinkedHashSet()) { oldId -> oldId != podcastId && currentId(entries, oldId) == podcastId }
}

/** [RenumberedShows] for one process only (tests, and callers built without the app's wiring). */
class MemoryRenumberedShows : RenumberedShows {
    private var entries: Map<String, String> = emptyMap()

    @Synchronized
    override fun all(): Map<String, String> = entries

    @Synchronized
    override fun record(oldId: String, newId: String): Boolean {
        if (!RenumberedShowIds.isRenumbering(oldId, newId)) return false
        entries = RenumberedShowIds.withEntry(entries, oldId, newId)
        return true
    }
}

/**
 * [RenumberedShows] in SharedPreferences file [FILE_NAME]: one key per old show ID, the show ID it
 * answered as its value. A restored copy only points the same old shows at the same new ones,
 * which is harmless. Stable storage name: never rename.
 *
 * Added for renumbered shows (docs/EPISODE_IDS.md §5, renumbered shows).
 */
class SharedPreferencesRenumberedShows(private val prefs: SharedPreferences) : RenumberedShows {
    private var cached: Map<String, String>? = null

    // Synchronized with [record], so a load that started before a save never replaces the saved map.
    @Synchronized
    override fun all(): Map<String, String> = cached ?: load().also { cached = it }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    override fun record(oldId: String, newId: String): Boolean {
        if (!RenumberedShowIds.isRenumbering(oldId, newId)) return false
        if (all()[oldId] == newId) return true
        val editor = prefs.edit().putString(oldId, newId)
        if (prefs.getString(newId, null) == oldId) editor.remove(newId)
        // Written to disk at once: the follow that moves next depends on it.
        val saved = editor.commit()
        cached = load()
        return saved
    }

    private fun load(): Map<String, String> =
        prefs.all.entries
            .mapNotNull { (key, value) -> (value as? String)?.takeIf { RenumberedShowIds.isRenumbering(key, it) }?.let { key to it } }
            .toMap()

    companion object {
        /** Stable storage name (ARCHITECTURE.md, SharedPreferences files): never rename. */
        const val FILE_NAME = "boxlore_renumbered_shows"
    }
}
