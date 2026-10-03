package cx.aswin.boxlore.fcm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** Persisted raw GUID/enclosure hint, including releases that do not have a PI episode ID yet. */
class NewEpisodeDeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = inputData.keyValueMap.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
        val podcastId = FcmPayloadParser.podcastId(data) ?: return Result.failure()
        return try {
            val deps = SharedAppDependenciesHolder.require()
            val show = deps.database.podcastDao().getPodcast(podcastId)
            if (show?.isSubscribed != true) return Result.success()
            val local = NewEpisodePushHydration.resolveLocalEpisode(
                podcastId,
                FcmPayloadParser.feedUrl(data),
                FcmPayloadParser.enclosureUrl(data),
                FcmPayloadParser.guid(data),
                NewEpisodePushHydration.Sources(
                    deps.subscriptionRepository,
                    deps.podcastRepository.episodeSupplementRepository,
                    deps.podcastRepository.localEpisodeCatalog,
                    NewEpisodePushHydration.piBaselineLoader { id, limit -> deps.podcastRepository.loadPiEpisodesForBaseline(id, limit) }
                ),
            )
            val coordinator = DownloadsDependenciesHolder.require().autoDownloadCoordinator
            if (local != null) coordinator.acceptRelease(podcastId, local)
            coordinator.scanCached(podcastId)
            if (show.notificationsEnabled && local != null) {
                // Update the already posted bounded slot with the exact Room ID and artwork.
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        NewEpisodeNotifications.show(applicationContext, podcastId, data, local, fetchArtwork = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("NewEpisodeDeliveryWorker", "Notification update failed", e)
                }
            }
            if (local == null && runAttemptCount < 5) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NewEpisodeDeliveryWorker", "Publisher release hydration failed", e)
            Result.retry()
        }
    }

    companion object {
        fun workName(data: Map<String, String>): String {
            val key = listOf(
                FcmPayloadParser.podcastId(data),
                FcmPayloadParser.guid(data),
                FcmPayloadParser.enclosureUrl(data),
                FcmPayloadParser.episodeId(data)
            ).joinToString("|")
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
            return "new-episode-delivery-$hash"
        }

        fun enqueue(context: Context, data: Map<String, String>, highPriority: Boolean = false): androidx.work.Operation {
            val input = Data.Builder().apply { data.forEach { (key, value) -> putString(key, value) } }.build()
            // Durable ordinary work also accepts normal-priority pushes; audio is independently constrained.
            val request = OneTimeWorkRequestBuilder<NewEpisodeDeliveryWorker>().setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .apply { if (highPriority) setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }.build()
            return WorkManager.getInstance(context).enqueueUniqueWork(workName(data), ExistingWorkPolicy.KEEP, request)
        }
    }
}
