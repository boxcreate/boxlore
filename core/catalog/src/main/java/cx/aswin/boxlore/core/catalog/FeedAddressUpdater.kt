package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.FeedAddressWrite
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep

/**
 * Where a saved feed address came from, stored in `podcasts.feedUrlSource`. An address the
 * registry answered with keeps the registry's own `feedSource` (for example `registry:apple`).
 * Stored strings: never rename.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): feed address bookkeeping. Permanent: never remove (MIGRATION.md §5).
 */
object FeedUrlSources {
    /** The feed moved for good: permanent redirects or `itunes:new-feed-url`, same show. */
    const val FEED_MOVE = "feed_move"

    /** The server's `/podcast` answer: address recovery, the dead-address re-check, auto-download. */
    const val PODCAST_LOOKUP = "podcast_lookup"

    /** Another device of the same account, through a cloud sync pull. */
    const val CLOUD_SYNC = "cloud_sync"

    /** The API's answer to a broken-feed report, read successfully before it was saved. */
    const val FEED_REPORT = "feed_report"

    /** A library backup being restored. */
    const val BACKUP_RESTORE = "backup_restore"

    /** The registry answered without naming its source. */
    const val REGISTRY = "registry"
}

/**
 * One request to replace a show's saved feed address, for [FeedAddressUpdater.update].
 *
 * - [feedUrl]: the new publisher address; only HTTPS is saved.
 * - [source]: where it came from, a [FeedUrlSources] value or the registry's `feedSource`.
 * - [transportUrl]: the address to read through when the publisher's can't be read directly.
 * - [checkedAt]: when the registry confirmed [feedUrl] (epoch ms), so the daily check and its
 *   fallback skip the show for a day; 0 when it wasn't checked against the registry.
 * - [fromCloud]: the address came from the cloud, so the row is not marked for a push back.
 * - [onlyReplacing]: when set, the change applies only while the saved address is still this one.
 * - [reason] and [step]: why and by which path a followed show's address was corrected; both set
 *   send one `feed_url_corrected`. Fills of a missing address, restores and pulls leave them null.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 */
data class FeedAddressChange(
    val podcastId: String,
    val feedUrl: String,
    val source: String,
    val transportUrl: String? = null,
    val checkedAt: Long = 0L,
    val fromCloud: Boolean = false,
    val onlyReplacing: String? = null,
    val reason: FeedUrlCorrectionReason? = null,
    val step: FeedUrlCorrectionStep? = null,
)

/** What [FeedAddressUpdater.update] did. */
enum class FeedAddressUpdate {
    /** The address was replaced. */
    UPDATED,

    /** The row already holds this address; nothing was written. */
    UNCHANGED,

    /** An `rss:` show, or an address that isn't HTTPS: refused. */
    REFUSED,

    /** No row for the show. */
    NO_SHOW,

    /** The saved address changed after it was read; nothing was written. */
    CHANGED_MEANWHILE,
}

/**
 * The one writer of a show's saved feed address. In one transaction ([PodcastDao.replaceFeedAddress])
 * it writes the publisher and transport addresses and keeps the old one for undo, resets every
 * stored ETag and Last-Modified of the show, and marks the row for the next sync push (not for
 * addresses that came from the cloud). It then asks for the show's new-episode notifications to be
 * registered again, acknowledged, when they are on ([requestNotificationRegistration]; the same
 * `tracked_podcasts/<id>` node and topic), and reports a correction of a followed show's address
 * as `feed_url_corrected`.
 *
 * Every address writer goes through it: recovery of a missing address, the dead-address re-check,
 * feeds that moved, broken-feed reports, the daily check, auto-download, backup restores and cloud
 * pulls. `rss:` shows are refused: their address is their identity and notification consent.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): saved addresses stay reachable. Permanent: never remove (MIGRATION.md §5).
 */
class FeedAddressUpdater(
    private val podcastDao: PodcastDao,
    private val requestNotificationRegistration: (podcastId: String) -> Unit = {},
    private val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
) {
    suspend fun update(change: FeedAddressChange): FeedAddressUpdate {
        if (change.podcastId.startsWith("rss:")) return FeedAddressUpdate.REFUSED
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(change.feedUrl) ?: return FeedAddressUpdate.REFUSED
        val row = podcastDao.getPodcast(change.podcastId) ?: return FeedAddressUpdate.NO_SHOW
        if (row.isRss) return FeedAddressUpdate.REFUSED
        if (change.onlyReplacing != null && row.feedUrl != change.onlyReplacing) return FeedAddressUpdate.CHANGED_MEANWHILE
        if (row.feedUrl == https) return FeedAddressUpdate.UNCHANGED
        val written =
            podcastDao.replaceFeedAddress(
                FeedAddressWrite(
                    podcastId = row.podcastId,
                    expectedFeedUrl = row.feedUrl,
                    feedUrl = https,
                    transportUrl = change.transportUrl,
                    source = change.source,
                    checkedAt = change.checkedAt,
                    markDirty = !change.fromCloud,
                ),
            )
        if (!written) return FeedAddressUpdate.CHANGED_MEANWHILE
        afterReplace(
            podcastId = row.podcastId,
            followed = row.isSubscribed,
            notificationsOn = row.isSubscribed && row.notificationsEnabled,
            hadAddress = !row.feedUrl.isNullOrBlank(),
            change = change,
        )
        return FeedAddressUpdate.UPDATED
    }

    private fun afterReplace(podcastId: String, followed: Boolean, notificationsOn: Boolean, hadAddress: Boolean, change: FeedAddressChange) {
        if (notificationsOn) {
            try {
                requestNotificationRegistration(podcastId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Couldn't ask to register notifications again for $podcastId", e)
            }
        }
        val reason = change.reason ?: return
        val step = change.step ?: return
        if (followed && hadAddress) telemetry.feedUrlCorrected(FeedUrlCorrection(podcastId, reason, step))
    }

    private companion object {
        const val TAG = "FeedAddressUpdater"
    }
}
