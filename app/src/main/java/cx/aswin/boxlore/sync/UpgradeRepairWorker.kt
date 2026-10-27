package cx.aswin.boxlore.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.UpgradeAddressRepair
import cx.aswin.boxlore.core.catalog.UpgradeRepairRun
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Runs the one-time repair of saved feed addresses after the update ([UpgradeAddressRepair] in
 * `:core:catalog`, reached through [SharedAppDependenciesHolder]). The logic and its stored
 * progress live there; this shell only maps a run to a WorkManager result.
 *
 * Enqueued on every launch while the repair has work left ([scheduleIfNeeded], from
 * `BoxLoreApplication`), so a restore, which never brings WorkManager's queue, starts it again.
 * Unique work [WORK_NAME] (a permanent identity), KEEP, network-gated, exponential backoff from
 * 30 s. A launch's chain ends after [MAX_RUNS] runs; the next launch enqueues it again while
 * work is left.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
class UpgradeRepairWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    internal var repair: suspend () -> UpgradeRepairRun = {
        SharedAppDependenciesHolder.require().upgradeAddressRepair?.run() ?: UpgradeRepairRun.DONE
    }

    override suspend fun doWork(): Result {
        val run =
            try {
                repair()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Feed address repair failed", error)
                UpgradeRepairRun.RETRY
            }
        return when (run) {
            UpgradeRepairRun.DONE, UpgradeRepairRun.PAUSED -> Result.success()
            UpgradeRepairRun.RETRY -> if (runAttemptCount + 1 < MAX_RUNS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "UpgradeRepairWorker"

        /** Permanent unique-work name. Never rename. */
        const val WORK_NAME = "feed-address-upgrade-repair"

        /** Runs per launch, the first included. */
        const val MAX_RUNS = 10

        private const val BACKOFF_SECONDS = 30L

        fun workRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<UpgradeRepairWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Enqueues the repair unless it is already waiting or running. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest())
        }

        /**
         * Enqueues the repair when it has work left. A fresh install, or one with only `rss:`
         * follows, settles here without network and enqueues nothing.
         */
        suspend fun scheduleIfNeeded(context: Context, repair: UpgradeAddressRepair): Boolean {
            if (!repair.needsWork()) return false
            enqueue(context)
            return true
        }
    }
}
