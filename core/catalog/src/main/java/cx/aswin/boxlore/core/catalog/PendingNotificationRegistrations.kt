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

    fun add(podcastId: String)

    fun remove(podcastId: String)
}

/** [PendingNotificationRegistrations] for one process only (tests, and callers built without the app's wiring). */
class MemoryPendingNotificationRegistrations : PendingNotificationRegistrations {
    private val ids = LinkedHashSet<String>()

    @Synchronized
    override fun all(): Set<String> = ids.toSet()

    @Synchronized
    override fun add(podcastId: String) {
        ids += podcastId
    }

    @Synchronized
    override fun remove(podcastId: String) {
        ids -= podcastId
    }
}

/**
 * [PendingNotificationRegistrations] in SharedPreferences file [FILE_NAME]: one key per show ID,
 * the time it was asked for (epoch ms) as its value. A restored copy only registers those shows
 * again, which is harmless. Stable storage name: never rename.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): notifications follow a corrected address. Permanent: never remove (MIGRATION.md §5).
 */
class SharedPreferencesPendingNotificationRegistrations(
    private val prefs: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PendingNotificationRegistrations {
    override fun all(): Set<String> = prefs.all.keys.toSet()

    @SuppressLint("ApplySharedPref")
    override fun add(podcastId: String) {
        // Written to disk at once: the address change that needs it follows in the same call.
        prefs.edit().putLong(podcastId, nowMillis()).commit()
    }

    override fun remove(podcastId: String) {
        prefs.edit().remove(podcastId).apply()
    }

    companion object {
        const val FILE_NAME = "boxlore_notification_reregistrations"
    }
}
