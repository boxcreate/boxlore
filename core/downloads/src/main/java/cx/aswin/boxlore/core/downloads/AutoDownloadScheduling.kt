package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import java.util.concurrent.TimeUnit

object AutoDownloadScheduling {
    const val DISCOVERY_NAME = "auto-download-discovery"
    const val TRANSFER_TAG = "auto-download-transfer"
    fun episodeWorkName(episodeId: String): String = "auto-download-episode-$episodeId"
    fun showTag(podcastId: String): String = "auto-download-show-$podcastId"

    fun transferRequest(podcastId: String, episodeId: String, wifiOnly: Boolean) =
        OneTimeWorkRequestBuilder<AutoDownloadWorker>()
            .setInputData(
                Data.Builder()
                .putString(AutoDownloadWorker.KEY_PODCAST_ID, podcastId)
                .putString(AutoDownloadWorker.KEY_EPISODE_ID, episodeId).build()
            )
            .setConstraints(
                Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TRANSFER_TAG).addTag(showTag(podcastId)).build()

    suspend fun enqueueEpisode(context: Context, podcastId: String, episodeId: String, wifiOnly: Boolean) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            episodeWorkName(episodeId),
            ExistingWorkPolicy.KEEP,
            transferRequest(podcastId, episodeId, wifiOnly),
        ).await()
    }

    suspend fun reconcile(context: Context, active: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!active) {
            manager.cancelUniqueWork(DISCOVERY_NAME).await()
            manager.cancelUniqueWork("$DISCOVERY_NAME-now").await()
            return
        }
        manager.enqueueUniquePeriodicWork(
            DISCOVERY_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoDownloadDiscoveryWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        ).await()
    }

    suspend fun catchUp(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$DISCOVERY_NAME-now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutoDownloadDiscoveryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        ).await()
    }
}
