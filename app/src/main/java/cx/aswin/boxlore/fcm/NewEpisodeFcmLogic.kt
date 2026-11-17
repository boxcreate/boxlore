package cx.aswin.boxlore.fcm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.await
import cx.aswin.boxlore.MainActivity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.navigation.PushTargetRouteAllowlist

/** Deep-link and id helpers for `type=new_episode` FCM payloads. */
internal object NewEpisodeFcmLogic {
    /** At most this many new-episode notifications show at once, one per show, each with its own tap slot. */
    const val MAX_EPISODE_NOTIFICATION_SLOTS = 16

    /** Every show's new-episode notification is posted under this id with the show's ID as its tag. */
    const val EPISODE_NOTIFICATION_ID_BASE = 10_000

    /**
     * Tap slots start here, clear of 10_000..10_015, which older builds' untagged notifications may
     * still use for their own taps, so a new placement never rewrites where one of those opens.
     */
    const val EPISODE_REQUEST_CODE_BASE = 10_200
    const val EPISODE_SUMMARY_NOTIFICATION_ID = 10_100
    const val EPISODE_SUMMARY_REQUEST_CODE = 10_100
    const val EPISODE_GROUP_KEY = "boxlore_new_episodes"

    /** The summary's tap is its own type, so it is not counted as a tap on a show's notification. */
    const val SUMMARY_NOTIFICATION_TYPE = "new_episode_summary"

    /**
     * A show's tap intent carries this plus the show's ID as its identifier. Android matches a
     * PendingIntent by request code and intent identity, so a tap slot that passes to another show
     * makes a new PendingIntent instead of rewriting one that a notification Android does not list
     * (a snoozed one) still holds.
     */
    const val EPISODE_INTENT_IDENTIFIER_PREFIX = "new_episode:"

    /** Where a tap on the summary goes: the New Episodes tab of Your shows, the same link the widget opens. */
    const val NEW_EPISODES_ROUTE = "boxlore://library/subscriptions?tab=1"

    const val MAX_ANNOUNCEMENT_SLOTS = 4
    const val ANNOUNCEMENT_NOTIFICATION_ID_BASE = 20_000
    const val ANNOUNCEMENT_REQUEST_CODE_BASE = 20_000
    const val ANNOUNCEMENT_ACTION_REQUEST_CODE_BASE = 20_100

    /**
     * The hashed slot (0..15) older builds posted a show's untagged notification in, at
     * [EPISODE_NOTIFICATION_ID_BASE] plus the slot; only used to clear those older copies.
     */
    fun episodeSlot(podcastId: String): Int =
        Math.floorMod(podcastId.hashCode(), MAX_EPISODE_NOTIFICATION_SLOTS)

    /** A new-episode notification that is showing: its show, its tap slot, when it was placed (oldest lowest) and its release. */
    data class PostedEpisodeNotification(val podcastId: String, val slot: Int, val order: Long, val releaseKey: String? = null)

    /** Where a show's notification goes: its tap [slot], its [order], and the show whose notification it replaces, if any. */
    data class EpisodeNotificationPlacement(val slot: Int, val order: Long, val replacesPodcastId: String?)

    /**
     * Picks the tap slot for [podcastId]'s notification from the ones [posted], so two showing
     * notifications never share a PendingIntent: the slot the show already holds, else the first free
     * one, else the oldest notification's, which it then replaces. A hash of the show's ID would let
     * two shows land on one slot and open each other. The slot count stays bounded so a large library
     * never piles up PendingIntents. [isUpdate] keeps an updated release in its place in the order.
     */
    fun placeEpisodeNotification(podcastId: String, posted: List<PostedEpisodeNotification>, isUpdate: Boolean): EpisodeNotificationPlacement {
        val valid = posted.filter { it.slot in 0 until MAX_EPISODE_NOTIFICATION_SLOTS }
        val nextOrder = (valid.maxOfOrNull { it.order } ?: 0L) + 1
        valid.firstOrNull { it.podcastId == podcastId }?.let { own ->
            return EpisodeNotificationPlacement(own.slot, if (isUpdate) own.order else nextOrder, null)
        }
        val used = valid.mapTo(HashSet()) { it.slot }
        (0 until MAX_EPISODE_NOTIFICATION_SLOTS).firstOrNull { it !in used }?.let { free ->
            return EpisodeNotificationPlacement(free, nextOrder, null)
        }
        val oldest = valid.minBy { it.order }
        return EpisodeNotificationPlacement(oldest.slot, nextOrder, oldest.podcastId)
    }

    /** The title of a show's new-episode notification; older builds used the same one. */
    fun episodeNotificationTitle(podcastTitle: String): String = "New Episode • $podcastTitle"

    /** An untagged notification an older build left in the new-episode id range, with its title. */
    data class LegacyEpisodeNotification(val id: Int, val title: String?)

    /**
     * The id of [podcastId]'s own copy from an older build, if one is showing, so it is not shown
     * twice. Older builds hashed shows into shared ids, so the copy at this show's hashed id counts
     * only when its [title] is this show's; another show's copy there stays until it goes.
     */
    fun legacyCopyToClear(podcastId: String, title: String, legacy: List<LegacyEpisodeNotification>): Int? {
        val id = EPISODE_NOTIFICATION_ID_BASE + episodeSlot(podcastId)
        return legacy.firstOrNull { it.id == id && it.title == title }?.id
    }

    /**
     * What a post does to the group summary. [cancelSummaryFirst] cancels it before the show's
     * notification goes up; [summaryCount], when set, posts it afterwards for that many shows.
     */
    data class SummaryPlan(val cancelSummaryFirst: Boolean, val summaryCount: Int?)

    /**
     * Android cancels every notification in a group when its summary is cancelled, so the summary is
     * never cancelled after a show's notification is posted. With [otherShowsShowing] other shows
     * still showing, the summary is posted after the show's notification. With none, a showing
     * summary is cancelled first, which can only take this show's old notification with it; an
     * [isUpdate] leaves it, so the update stays quiet instead of posting the show's notification anew.
     */
    fun planSummary(otherShowsShowing: Int, summaryShowing: Boolean, isUpdate: Boolean): SummaryPlan =
        if (otherShowsShowing > 0) {
            SummaryPlan(cancelSummaryFirst = false, summaryCount = otherShowsShowing + 1)
        } else {
            SummaryPlan(cancelSummaryFirst = summaryShowing && !isUpdate, summaryCount = null)
        }

    fun announcementSlot(announcementId: String?): Int =
        Math.floorMod(announcementId?.hashCode() ?: 0, MAX_ANNOUNCEMENT_SLOTS)

    fun createNormalizedPushIntent(
        context: Context,
        targetRoute: String?,
        notificationType: String = "push",
        podcastId: String? = null,
        episodeId: String? = null,
    ): Intent {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setClass(context, MainActivity::class.java)
                data = null
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("from_push", true)
                putExtra("notification_type", notificationType)
                if (!podcastId.isNullOrBlank()) {
                    putExtra("podcast_id", podcastId)
                }
                if (!episodeId.isNullOrBlank()) {
                    putExtra("episode_id", episodeId)
                }
            }
        val safeRoute = PushTargetRouteAllowlist.sanitize(targetRoute)
        if (safeRoute != null) {
            intent.putExtra("target_route", safeRoute)
        }
        return intent
    }

    fun usableEpisodeId(raw: String?): String? {
        val id = raw?.trim().orEmpty()
        if (id.isEmpty() || id == "0") return null
        return id
    }

    fun route(podcastId: String, episodeId: String?, podcastTitle: String,): String {
        val ep = usableEpisodeId(episodeId)
        return if (ep != null) {
            "boxlore://episode/$ep?autoplay=false&podcastId=${Uri.encode(podcastId)}" +
                "&podcastTitle=${Uri.encode(podcastTitle)}"
        } else {
            "boxlore://podcast/$podcastId"
        }
    }

    fun durationMinutes(localDurationSeconds: Int?, payloadDurationMinutes: String?,): Int {
        if (localDurationSeconds != null && localDurationSeconds > 0) {
            return localDurationSeconds / 60
        }
        return payloadDurationMinutes?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    /**
     * After a catalog persist, resolve the payload item by enclosure only.
     * No newest-in-feed fallback — an unmatched payload must not open a different episode.
     */
    fun pickHydratedEpisode(extras: List<Episode>, @Suppress("UNUSED_PARAMETER") newestTip: Episode?, enclosureUrl: String,): Episode? {
        val enclosure = enclosureUrl.trim()
        if (enclosure.isNotEmpty()) {
            extras.find { it.audioUrl.trim() == enclosure }?.let { return it }
        }
        return null
    }

    fun buildAutoDownloadWorkRequest(
        podcastId: String,
        episodeId: String,
        wifiOnly: Boolean,
    ): androidx.work.OneTimeWorkRequest = cx.aswin.boxlore.core.downloads.AutoDownloadScheduling.transferRequest(podcastId, episodeId, wifiOnly)

    suspend fun enqueueAutoDownload(
        workManager: androidx.work.WorkManager,
        podcastId: String,
        episodeId: String,
        wifiOnly: Boolean,
    ) {
        workManager.enqueueUniqueWork(
            cx.aswin.boxlore.core.downloads.AutoDownloadScheduling.episodeWorkName(episodeId),
            androidx.work.ExistingWorkPolicy.KEEP,
            buildAutoDownloadWorkRequest(podcastId, episodeId, wifiOnly),
        ).await()
    }

    suspend fun executeEpisodeDelivery(
        triggerAutoDownload: suspend () -> Unit,
        showNotification: () -> Unit,
    ) {
        try {
            triggerAutoDownload()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NewEpisodeFcmLogic", "Failed to trigger auto download", e)
        }
        try {
            showNotification()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NewEpisodeFcmLogic", "Failed to show new episode notification", e)
        }
    }
}
