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
    const val MAX_EPISODE_NOTIFICATION_SLOTS = 16
    const val EPISODE_NOTIFICATION_ID_BASE = 10_000
    const val EPISODE_REQUEST_CODE_BASE = 10_000

    const val MAX_ANNOUNCEMENT_SLOTS = 4
    const val ANNOUNCEMENT_NOTIFICATION_ID_BASE = 20_000
    const val ANNOUNCEMENT_REQUEST_CODE_BASE = 20_000
    const val ANNOUNCEMENT_ACTION_REQUEST_CODE_BASE = 20_100

    fun episodeSlot(podcastId: String): Int =
        Math.floorMod(podcastId.hashCode(), MAX_EPISODE_NOTIFICATION_SLOTS)

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
    ): androidx.work.OneTimeWorkRequest {
        val requiredNetwork =
            if (wifiOnly) {
                androidx.work.NetworkType.UNMETERED
            } else {
                androidx.work.NetworkType.CONNECTED
            }
        val inputData =
            androidx.work.Data
                .Builder()
                .putString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_PODCAST_ID, podcastId)
                .putString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_EPISODE_ID, episodeId)
                .build()
        return androidx.work.OneTimeWorkRequestBuilder<cx.aswin.boxlore.core.downloads.AutoDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(
                androidx.work.Constraints
                    .Builder()
                    .setRequiredNetworkType(requiredNetwork)
                    .build(),
            ).build()
    }

    suspend fun enqueueAutoDownload(
        workManager: androidx.work.WorkManager,
        podcastId: String,
        episodeId: String,
        wifiOnly: Boolean,
    ) {
        val workRequest = buildAutoDownloadWorkRequest(podcastId, episodeId, wifiOnly)
        val operation = workManager.enqueue(workRequest)
        operation.await()
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
