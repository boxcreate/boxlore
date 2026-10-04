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
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import java.util.concurrent.TimeUnit

object AutoDownloadScheduling {
    const val DISCOVERY_NAME = "auto-download-discovery"
    const val TRANSFER_TAG = "auto-download-transfer"
    const val BACKGROUND_TRANSFER_TAG = "auto-download-background-transfer"
    const val POLICY_TRANSFER_TAG = "auto-download-policy-v2"
    fun episodeWorkName(episodeId: String): String = "auto-download-episode-$episodeId"
    fun showTag(podcastId: String): String = "auto-download-show-$podcastId"

    fun transferRequest(podcastId: String, episodeId: String, wifiOnly: Boolean, background: AutoDownloadBackgroundSettings? = null) =
        OneTimeWorkRequestBuilder<AutoDownloadWorker>()
            .setInputData(
                Data.Builder()
                .putString(AutoDownloadWorker.KEY_PODCAST_ID, podcastId)
                .putBoolean(AutoDownloadWorker.KEY_BACKGROUND_CHECK, background != null)
                .putString(AutoDownloadWorker.KEY_EPISODE_ID, episodeId).build()
            )
            .setConstraints(
                transferConstraints(wifiOnly, background)
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TRANSFER_TAG).addTag(POLICY_TRANSFER_TAG).addTag(showTag(podcastId))
            .apply { if (background != null) addTag(BACKGROUND_TRANSFER_TAG) }.build()

    suspend fun enqueueEpisode(context: Context, podcastId: String, episodeId: String, wifiOnly: Boolean, background: AutoDownloadBackgroundSettings? = null) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            episodeWorkName(episodeId),
            ExistingWorkPolicy.KEEP,
            transferRequest(podcastId, episodeId, wifiOnly, background),
        ).await()
    }

    suspend fun reconcile(context: Context, active: Boolean, background: AutoDownloadBackgroundSettings = AutoDownloadBackgroundSettings()) {
        val manager = WorkManager.getInstance(context)
        // Remove the old implicit foreground catch-up work on every upgrade/start.
        manager.cancelUniqueWork("$DISCOVERY_NAME-now").await()
        if (!active || !background.enabled) {
            manager.cancelUniqueWork(DISCOVERY_NAME).await()
            manager.cancelUniqueWork("$DISCOVERY_NAME-now").await()
            manager.cancelAllWorkByTag(BACKGROUND_TRANSFER_TAG).await()
            return
        }
        manager.enqueueUniquePeriodicWork(
            DISCOVERY_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoDownloadDiscoveryWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(backgroundConstraints(background))
                .build()
        ).await()
    }

    fun backgroundConstraints(settings: AutoDownloadBackgroundSettings): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true).setRequiresCharging(settings.chargingOnly).build()

    fun transferConstraints(wifiOnly: Boolean, background: AutoDownloadBackgroundSettings?): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly || background?.wifiOnly == true) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(background != null).setRequiresCharging(background?.chargingOnly == true).build()
}
