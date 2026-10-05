package cx.aswin.boxlore.fcm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Event-driven registration reconciliation; this worker never polls publisher feeds. */
class RssNotificationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    internal var reconcile: suspend () -> Boolean = { SharedAppDependenciesHolder.require().subscriptionRepository.reconcileRssNotificationRegistrations() }

    override suspend fun doWork(): Result = try {
        if (reconcile()) Result.success() else Result.retry()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "rss-notification-registration-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RssNotificationSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // The durable journal survives cancellation; a settings change must not wait behind old retry backoff.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
