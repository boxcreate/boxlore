package cx.aswin.boxlore.backup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import cx.aswin.boxlore.core.prefs.AutoBackupFrequency
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.core.prefs.AutoBackupStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Schedules [AutoBackupWorker] from the stored settings. The unique work names are listed in
 * ARCHITECTURE.md's identity table; never rename them.
 */
internal object AutoBackupScheduling {
    const val PERIODIC_WORK_NAME = "boxlore_auto_backup_periodic"
    const val NOW_WORK_NAME = "boxlore_auto_backup_now"

    /** Tries per run, the first included, before a failure that may pass on its own is recorded. */
    const val MAX_ATTEMPTS = 3

    /** What the schedule depends on; other settings (keep count, last result) never touch WorkManager. */
    data class Plan(val active: Boolean, val frequency: AutoBackupFrequency)

    fun planOf(settings: AutoBackupSettings): Plan = Plan(settings.isActive, settings.frequency)

    /** No network: the backup only writes a local file, and a sync app moves it on its own. */
    fun periodicConstraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    /** Back up now was asked for, so it doesn't wait for the battery; it still needs room to save. */
    fun nowConstraints(): Constraints = Constraints.Builder().setRequiresStorageNotLow(true).build()

    fun periodicRequest(frequency: AutoBackupFrequency): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<AutoBackupWorker>(
            frequency.intervalDays,
            TimeUnit.DAYS,
            if (frequency == AutoBackupFrequency.DAILY) DAILY_FLEX_HOURS else WEEKLY_FLEX_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(periodicConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_MINUTES, TimeUnit.MINUTES)
            .build()

    fun nowRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
        .setConstraints(nowConstraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, NOW_RETRY_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Schedules the periodic backup while on with a folder (keeping its timing), otherwise cancels both runs. */
    suspend fun reconcile(context: Context, plan: Plan) {
        val manager = WorkManager.getInstance(context)
        if (!plan.active) {
            manager.cancelUniqueWork(PERIODIC_WORK_NAME).await()
            manager.cancelUniqueWork(NOW_WORK_NAME).await()
            return
        }
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodicRequest(plan.frequency)).await()
    }

    /** Settings changes and process starts (restores included) rebuild the schedule from the `auto_backup` store. */
    fun start(context: Context, scope: CoroutineScope) {
        // A read failure leaves the schedule as it was; it must not cancel backups the listener turned on.
        scope.launch { follow(context, AutoBackupStore(context).settingsWhenReadable) }
    }

    /** Follows [settings] for the life of the process, reconciling only when the plan changes. */
    suspend fun follow(context: Context, settings: Flow<AutoBackupSettings>) {
        settings.map(::planOf).distinctUntilChanged().collectLatest { plan ->
            try {
                reconcile(context, plan)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "The automatic backup schedule wasn't updated", error)
            }
        }
    }

    /**
     * A pending Back up now is kept, so repeated taps save one backup. [newFolder] replaces it instead: a run
     * still writing to the previous folder (whose grant was just released) is stopped, and the new folder
     * gets its first backup.
     */
    fun backUpNow(context: Context, newFolder: Boolean = false) {
        val policy = if (newFolder) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, policy, nowRequest())
    }

    /** True while a backup runs, or while Back up now waits to retry after a try that didn't save. */
    fun isBackingUp(context: Context): Flow<Boolean> {
        val manager = WorkManager.getInstance(context)
        return combine(
            manager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME),
            manager.getWorkInfosForUniqueWorkFlow(NOW_WORK_NAME),
            ::isBackingUp,
        ).distinctUntilChanged()
    }

    /**
     * A Back up now that hasn't started yet doesn't count: it may wait hours for free storage, and the page
     * would otherwise say "Backing up…" and ignore Back up now and Try again the whole time.
     */
    fun isBackingUp(periodic: List<WorkInfo>, now: List<WorkInfo>): Boolean =
        periodic.any { it.state == WorkInfo.State.RUNNING } ||
            now.any { it.state == WorkInfo.State.RUNNING || (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0) }

    private const val TAG = "AutoBackupScheduling"
    private const val DAILY_FLEX_HOURS = 4L
    private const val WEEKLY_FLEX_HOURS = 24L
    private const val RETRY_MINUTES = 15L
    private const val NOW_RETRY_SECONDS = 30L
}
