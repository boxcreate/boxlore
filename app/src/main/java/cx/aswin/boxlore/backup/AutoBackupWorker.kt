package cx.aswin.boxlore.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.AutoBackupStore

/**
 * Saves one automatic library backup to the chosen folder ([AutoBackupScheduling] runs it). A lost grant or
 * a missing folder is recorded for Settings and never retried; other failures retry up to
 * [AutoBackupScheduling.MAX_ATTEMPTS] times.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    internal var store: AutoBackupStore = AutoBackupStore(applicationContext)
    internal var folderFor: (String) -> AutoBackupFolder = { SafAutoBackupFolder(applicationContext, it.toUri()) }
    internal var export: suspend () -> LibraryJsonBackup.Export = { LibraryJsonBackup.fromSharedGraph(applicationContext) }
    internal var nowMs: () -> Long = System::currentTimeMillis
    internal var report: (AutoBackupOutcome) -> Unit = ::track

    override suspend fun doWork(): Result {
        val settings = store.current()
        val folderUri = settings.folderUri
        // Turned off or the folder was cleared after this run was scheduled.
        if (!settings.isActive || folderUri == null) return Result.success()
        store.recordAttempt(nowMs())
        return when (val outcome = AutoBackupRunner(folderFor(folderUri), export, nowMs).run(settings.keepCount)) {
            is AutoBackupOutcome.Saved -> {
                store.recordSuccess(nowMs(), outcome.fileName, folderUri)
                report(outcome)
                Result.success()
            }
            is AutoBackupOutcome.Failed ->
                if (outcome.retryable && runAttemptCount < AutoBackupScheduling.MAX_ATTEMPTS - 1) {
                    Result.retry()
                } else {
                    store.recordProblem(outcome.code, folderUri)
                    report(outcome)
                    if (outcome.retryable) Result.failure() else Result.success()
                }
        }
    }

    private fun track(outcome: AutoBackupOutcome) {
        when (outcome) {
            is AutoBackupOutcome.Saved -> AnalyticsHelper.trackBackupRestoreResult(
                action = ACTION,
                success = true,
                itemCount = outcome.subscriptionCount,
                format = FORMAT,
            )
            is AutoBackupOutcome.Failed -> AnalyticsHelper.trackBackupRestoreResult(
                action = ACTION,
                success = false,
                format = FORMAT,
                errorMessage = outcome.code,
            )
        }
    }

    private companion object {
        const val ACTION = "auto_backup"
        const val FORMAT = "json"
    }
}
