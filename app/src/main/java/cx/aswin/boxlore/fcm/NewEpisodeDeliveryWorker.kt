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
    internal interface Dependencies {
        suspend fun getShow(podcastId: String): cx.aswin.boxlore.core.database.PodcastEntity?
        suspend fun resolveEpisode(podcastId: String, data: Map<String, String>): cx.aswin.boxlore.core.model.Episode?
        suspend fun acceptRelease(podcastId: String, episode: cx.aswin.boxlore.core.model.Episode)
        suspend fun scanCached(podcastId: String)
        suspend fun updateNotification(podcastId: String, data: Map<String, String>, episode: cx.aswin.boxlore.core.model.Episode)
    }

    internal var dependencies: Dependencies = AppDependencies(context)

    override suspend fun doWork(): Result {
        val data = inputData.keyValueMap.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
        val podcastId = FcmPayloadParser.podcastId(data) ?: return Result.failure()
        return try {
            val show = dependencies.getShow(podcastId)
            if (show?.isSubscribed != true) return Result.success()
            val local = dependencies.resolveEpisode(podcastId, data)
            if (local != null) dependencies.acceptRelease(podcastId, local)
            dependencies.scanCached(podcastId)
            if (show.notificationsEnabled && local != null) {
                // Update the already posted bounded slot with the exact Room ID and artwork.
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        dependencies.updateNotification(podcastId, data, local)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("NewEpisodeDeliveryWorker", "Notification update failed", e)
                }
            }
            if (local == null && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NewEpisodeDeliveryWorker", "Publisher release hydration failed", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private class AppDependencies(private val context: Context) : Dependencies {
        override suspend fun getShow(podcastId: String) = SharedAppDependenciesHolder.require().database.podcastDao().getPodcast(podcastId)

        override suspend fun resolveEpisode(podcastId: String, data: Map<String, String>): cx.aswin.boxlore.core.model.Episode? {
            val deps = SharedAppDependenciesHolder.require()
            return NewEpisodePushHydration.resolveLocalEpisode(
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
        }

        override suspend fun acceptRelease(podcastId: String, episode: cx.aswin.boxlore.core.model.Episode) {
            DownloadsDependenciesHolder.require().autoDownloadCoordinator.acceptRelease(podcastId, episode)
        }

        override suspend fun scanCached(podcastId: String) {
            DownloadsDependenciesHolder.require().autoDownloadCoordinator.scanCached(podcastId)
        }

        override suspend fun updateNotification(podcastId: String, data: Map<String, String>, episode: cx.aswin.boxlore.core.model.Episode) {
            NewEpisodeNotifications.show(context, podcastId, data, episode, fetchArtwork = true)
        }
    }

    companion object {
        private const val MAX_RETRIES = 5

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
