package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** What one acknowledged notification registration achieved. */
enum class NotificationRegistrationOutcome {
    /** Registered, or nothing to register (not followed, notifications off, no row). */
    DONE,

    /** It can never work (an `rss:` show, or the rules refused the write): stop trying. */
    DROPPED,

    /** Firebase didn't acknowledge in time: try again later. */
    RETRY,
}

/**
 * Registers a Podcast Index show's new-episode notifications again after its saved address
 * changed, and waits for Firebase to acknowledge both writes: the `tracked_podcasts/<id>` node
 * (title, artwork and the HTTPS address; written only once the show has a latest episode, so the
 * shared node never loses its address) and the
 * [TrackedPodcastRtdbLogic.topic] subscription. Same node and topic as turning notifications on,
 * so nothing old builds or the checker read changes. The app retries [NotificationRegistrationOutcome.RETRY]
 * with a capped background job. `rss:` shows have their own journal ([RssNotificationReconciler]).
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): notifications follow a corrected address. Permanent: never remove (MIGRATION.md §5).
 */
class PodcastNotificationRegistrar internal constructor(
    private val podcastDao: PodcastDao,
    private val firebase: TrackedPodcastFirebase,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    constructor(podcastDao: PodcastDao) : this(podcastDao, LiveTrackedPodcastFirebase)

    suspend fun register(podcastId: String): NotificationRegistrationOutcome {
        if (podcastId.startsWith("rss:")) return NotificationRegistrationOutcome.DROPPED
        val row = podcastDao.getPodcast(podcastId) ?: return NotificationRegistrationOutcome.DONE
        if (row.isRss) return NotificationRegistrationOutcome.DROPPED
        if (!row.isSubscribed || !row.notificationsEnabled) return NotificationRegistrationOutcome.DONE
        return try {
            val acknowledged = withTimeoutOrNull(timeoutMs) { publish(row) } != null
            if (acknowledged) NotificationRegistrationOutcome.DONE else NotificationRegistrationOutcome.RETRY
        } catch (e: CancellationException) {
            throw e
        } catch (_: TrackedPodcastWriteRefused) {
            Log.w(TAG, "Notification rules refused $podcastId")
            NotificationRegistrationOutcome.DROPPED
        } catch (e: Exception) {
            Log.w(TAG, "Notification registration for $podcastId not acknowledged", e)
            NotificationRegistrationOutcome.RETRY
        }
    }

    private suspend fun publish(row: PodcastEntity) {
        // The node is one per show for every follower and its write replaces it whole: it is written
        // only with an address. A stub row from a cloud pull, or a row with no latest episode yet,
        // subscribes the topic only, as turning notifications on does; the latest-episode save
        // attaches the address later.
        val feedUrl = TrackedPodcastRtdbLogic.attachableFeedUrl(row.feedUrl, row.latestEpisode?.id)
        if (row.title.isNotBlank() && row.title != STUB_TITLE && feedUrl != null) {
            firebase.set(row.podcastId, TrackedPodcastRtdbLogic.payload(row.title, row.imageUrl, feedUrl))
        }
        firebase.subscribe(TrackedPodcastRtdbLogic.topic(row.podcastId))
    }

    private companion object {
        const val TAG = "PodcastNotifications"
        const val STUB_TITLE = "Loading..."
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
