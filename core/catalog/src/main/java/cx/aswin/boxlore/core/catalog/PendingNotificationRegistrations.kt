package cx.aswin.boxlore.core.catalog

import android.annotation.SuppressLint
import android.content.SharedPreferences

/**
 * Podcast Index shows whose new-episode notifications must be registered again after an address
 * change and that Firebase hasn't acknowledged yet. Kept across launches, so a request lost with
 * the process, or one the capped background job gave up on, is asked for again
 * ([FeedAddressUpdater.resumePendingRegistrations]).
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): notifications follow a corrected address. Permanent: never remove (MIGRATION.md §5).
 */
interface PendingNotificationRegistrations {
    fun all(): Set<String>

    /** [podcastId]'s pending request, or null when none is pending; each [add] gives a new mark. */
    fun mark(podcastId: String): Long?

    fun add(podcastId: String)

    /** Clears [podcastId] only while its request is still [mark], so a newer address change stays pending. */
    fun remove(podcastId: String, mark: Long)
}

/** [PendingNotificationRegistrations] for one process only (tests, and callers built without the app's wiring). */
class MemoryPendingNotificationRegistrations : PendingNotificationRegistrations {
    private val marks = LinkedHashMap<String, Long>()
    private var lastMark = 0L

    @Synchronized
    override fun all(): Set<String> = marks.keys.toSet()

    @Synchronized
    override fun mark(podcastId: String): Long? = marks[podcastId]

    @Synchronized
    override fun add(podcastId: String) {
        marks[podcastId] = ++lastMark
    }

    @Synchronized
    override fun remove(podcastId: String, mark: Long) {
        if (marks[podcastId] == mark) marks -= podcastId
    }
}

/**
 * [PendingNotificationRegistrations] in SharedPreferences file [FILE_NAME]: one key per show ID,
 * the time it was asked for (epoch ms) as its value and mark, always later than the mark it
 * replaces. A restored copy only registers those shows again, which is harmless. Stable storage
 * name: never rename.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): notifications follow a corrected address. Permanent: never remove (MIGRATION.md §5).
 */
class SharedPreferencesPendingNotificationRegistrations(
    private val prefs: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PendingNotificationRegistrations {
    override fun all(): Set<String> = prefs.all.keys.toSet()

    override fun mark(podcastId: String): Long? = if (prefs.contains(podcastId)) prefs.getLong(podcastId, 0L) else null

    @Synchronized
    @SuppressLint("ApplySharedPref")
    override fun add(podcastId: String) {
        val mark = maxOf(nowMillis(), (mark(podcastId) ?: 0L) + 1)
        // Written to disk at once: the address change that needs it follows in the same call.
        prefs.edit().putLong(podcastId, mark).commit()
    }

    @Synchronized
    override fun remove(podcastId: String, mark: Long) {
        if (mark(podcastId) == mark) prefs.edit().remove(podcastId).apply()
    }

    companion object {
        const val FILE_NAME = "boxlore_notification_reregistrations"
    }
}
