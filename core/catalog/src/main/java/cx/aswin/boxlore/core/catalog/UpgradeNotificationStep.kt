package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.UpgradeRepairDao
import kotlinx.coroutines.CancellationException

/**
 * Step 4 of [UpgradeAddressRepair]: asks for every followed Podcast Index show with notifications
 * on, whose own repair steps are over, to be registered again, records each request in [marker]
 * once the request is saved ([requestRegistration] returns), and records the step done once no
 * show has steps left and every request was saved. A request that couldn't be saved is not
 * recorded, so the next run asks again.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair; "Android backup").
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
internal class UpgradeNotificationStep(
    private val repairDao: UpgradeRepairDao,
    private val podcastDao: PodcastDao,
    private val marker: UpgradeNotificationMarker,
    private val requestRegistration: suspend (podcastId: String) -> Unit,
) {
    /** Asks for every target's registration; returns how many requests were saved. */
    suspend fun register(): Int {
        if (marker.isComplete) return 0
        val requested = mutableListOf<String>()
        var saved = 0
        var allRequested = true
        for (podcastId in targets()) {
            if (request(podcastId)) {
                requested += podcastId
                saved++
            } else {
                allRequested = false
            }
            if (requested.size >= MARKER_BATCH) {
                marker.addRegistered(requested)
                requested.clear()
            }
        }
        marker.addRegistered(requested)
        if (allRequested && repairDao.pendingCount() == 0) marker.markComplete()
        return saved
    }

    /** Followed Podcast Index shows with notifications on, not yet registered here, whose repair steps are over. */
    suspend fun targets(): List<String> {
        val registered = marker.registered()
        val waiting = repairDao.pendingShows().mapTo(HashSet()) { it.podcastId }
        return podcastDao
            .getSubscribedPodcastsList()
            .filter { !it.isRss && it.notificationsEnabled && it.podcastId !in registered && it.podcastId !in waiting }
            .map { it.podcastId }
    }

    private suspend fun request(podcastId: String): Boolean = try {
        requestRegistration(podcastId)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Couldn't ask to register notifications again for $podcastId", e)
        false
    }

    private companion object {
        const val TAG = "UpgradeAddressRepair"
        const val MARKER_BATCH = 50
    }
}
