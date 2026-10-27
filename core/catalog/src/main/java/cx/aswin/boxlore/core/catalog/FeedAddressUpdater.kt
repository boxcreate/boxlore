package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.FeedAddressWrite
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import kotlinx.coroutines.CancellationException

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

    /**
     * True when [source] marks an address this phone confirmed as a correction (the registry, a feed
     * move, a broken-feed report or the `/podcast` re-check), which a cloud pull of the address it
     * replaced never undoes.
     */
    fun isConfirmedCorrection(source: String?): Boolean =
        source != null && (source.startsWith(REGISTRY) || source == FEED_MOVE || source == FEED_REPORT || source == PODCAST_LOOKUP)

    /**
     * The address a follow writes with the row: a Podcast Index show's saved HTTPS address stays
     * (it may have been corrected since the show was last followed; only confirmed moves replace it,
     * through [FeedAddressUpdater]), else the [supplied] one. `rss:` shows keep the address they
     * were opened with.
     */
    fun followedFeedUrl(isRss: Boolean, saved: String?, supplied: String?): String? {
        if (isRss) return supplied
        return saved?.takeIf { TrackedPodcastRtdbLogic.httpsFeedUrl(it) == it } ?: supplied
    }
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

    /**
     * A pulled address is the one a confirmed correction on this phone replaced: the correction is
     * kept and the follow is marked for the next push, so the account gets it back.
     */
    KEPT_CORRECTION,
}

/**
 * The one writer of a show's saved feed address. In one transaction ([PodcastDao.replaceFeedAddress])
 * it writes the publisher and transport addresses and keeps the old one for undo, resets every
 * stored ETag and Last-Modified of the show, and marks a followed show for the next sync push (not
 * for addresses that came from the cloud). It then asks for the show's new-episode notifications to
 * be registered again, acknowledged, when they are on ([requestNotificationRegistration]; the same
 * `tracked_podcasts/<id>` node and topic), and reports a correction of a followed show's address
 * as `feed_url_corrected`.
 *
 * A registration is recorded as pending ([pendingRegistrations]) before the address is written and
 * cleared only once Firebase acknowledged it ([registrationSettled]), so one the process lost or
 * the background job gave up on is asked for again ([resumePendingRegistrations]).
 *
 * Every address writer goes through it: recovery of a missing address, the dead-address re-check,
 * feeds that moved, broken-feed reports, the daily check, auto-download, backup restores and cloud
 * pulls. `rss:` shows are refused: their address is their identity and notification consent. A
 * cloud pull never puts back the address a confirmed correction replaced ([FeedAddressUpdate.KEPT_CORRECTION]).
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): saved addresses stay reachable. Permanent: never remove (MIGRATION.md §5).
 */
class FeedAddressUpdater(
    private val podcastDao: PodcastDao,
    private val requestNotificationRegistration: (podcastId: String) -> Unit = {},
    private val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
    private val pendingRegistrations: PendingNotificationRegistrations = MemoryPendingNotificationRegistrations(),
) {
    suspend fun update(change: FeedAddressChange): FeedAddressUpdate {
        if (change.podcastId.startsWith("rss:")) return FeedAddressUpdate.REFUSED
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(change.feedUrl) ?: return FeedAddressUpdate.REFUSED
        val row = podcastDao.getPodcast(change.podcastId) ?: return FeedAddressUpdate.NO_SHOW
        notWritten(row, change, https)?.let { return it }
        val notificationsOn = row.isSubscribed && row.notificationsEnabled
        if (notificationsOn) pendingRegistrations.add(row.podcastId)
        val written =
            podcastDao.replaceFeedAddress(
                FeedAddressWrite(
                    podcastId = row.podcastId,
                    expectedFeedUrl = row.feedUrl,
                    feedUrl = https,
                    transportUrl = change.transportUrl,
                    source = change.source,
                    checkedAt = change.checkedAt,
                    markDirty = !change.fromCloud && row.isSubscribed,
                ),
            )
        if (!written) return FeedAddressUpdate.CHANGED_MEANWHILE
        if (notificationsOn) requestRegistration(row.podcastId)
        reportCorrection(row, change)
        return FeedAddressUpdate.UPDATED
    }

    /**
     * Asks again for every registration still pending: one whose request was lost with the process,
     * or that the background job gave up on. Run once per foreground sync pass.
     */
    fun resumePendingRegistrations() {
        pendingRegistrations.all().filterNot { it.startsWith("rss:") }.forEach(::requestRegistration)
    }

    /** Records what an acknowledged registration achieved: only a retry stays pending. */
    fun registrationSettled(podcastId: String, outcome: NotificationRegistrationOutcome) {
        if (outcome != NotificationRegistrationOutcome.RETRY) pendingRegistrations.remove(podcastId)
    }

    private fun requestRegistration(podcastId: String) {
        try {
            requestNotificationRegistration(podcastId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't ask to register notifications again for $podcastId", e)
        }
    }

    private fun reportCorrection(row: PodcastEntity, change: FeedAddressChange) {
        val reason = change.reason ?: return
        val step = change.step ?: return
        if (row.isSubscribed && !row.feedUrl.isNullOrBlank()) telemetry.feedUrlCorrected(FeedUrlCorrection(row.podcastId, reason, step))
    }

    /** Why [change] isn't written to [row], or null when it is. */
    private suspend fun notWritten(row: PodcastEntity, change: FeedAddressChange, https: String): FeedAddressUpdate? = when {
        row.isRss -> FeedAddressUpdate.REFUSED
        change.onlyReplacing != null && row.feedUrl?.trim() != change.onlyReplacing.trim() -> FeedAddressUpdate.CHANGED_MEANWHILE
        row.feedUrl == https -> FeedAddressUpdate.UNCHANGED
        change.fromCloud && undoesCorrection(row, https) -> {
            podcastDao.markFollowedDirty(row.podcastId)
            FeedAddressUpdate.KEPT_CORRECTION
        }
        else -> null
    }

    /** The pulled address is the one a confirmed correction on this phone replaced. */
    private fun undoesCorrection(row: PodcastEntity, pulled: String): Boolean =
        FeedUrlSources.isConfirmedCorrection(row.feedUrlSource) && row.previousFeedUrl?.trim() == pulled

    private companion object {
        const val TAG = "FeedAddressUpdater"
    }
}
