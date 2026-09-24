package cx.aswin.boxlore.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.sync.SyncResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val deps = SharedAppDependenciesHolder.instance
            val coordinator = deps?.userSyncCoordinator
            if (coordinator == null) {
                Log.w(TAG, "userSyncCoordinator unavailable; completing CloudSyncWorker")
                return Result.success()
            }

            when (val syncResult = coordinator.syncNow()) {
                is SyncResult.Success -> {
                    Log.i(TAG, "Cloud sync succeeded in worker: $syncResult")
                    Result.success()
                }
                is SyncResult.SkippedNoAuth -> {
                    Log.i(TAG, "Cloud sync skipped (no authenticated user)")
                    Result.success()
                }
                is SyncResult.SkippedOffline -> {
                    Log.w(TAG, "Cloud sync skipped due to offline state, retrying later")
                    Result.retry()
                }
                is SyncResult.Failure -> {
                    Log.w(TAG, "Cloud sync failed in worker: ${syncResult.error.message}")
                    Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in CloudSyncWorker", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CloudSyncWorker"
        const val WORK_NAME_PERIODIC = "boxlore_cloud_sync_periodic"
        const val WORK_NAME_FLUSH = "boxlore_cloud_sync_flush"
        private const val PERIODIC_INTERVAL_HOURS = 6L

        fun enqueueOneShotSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setConstraints(constraints)
                    .addTag(WORK_NAME_FLUSH)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_FLUSH,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue one-shot cloud sync", e)
            }
        }

        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(
                    PERIODIC_INTERVAL_HOURS,
                    TimeUnit.HOURS,
                )
                    .setConstraints(constraints)
                    .addTag(WORK_NAME_PERIODIC)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic cloud sync", e)
            }
        }
    }
}
