package cx.aswin.boxlore.backup

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.core.prefs.AutoBackupStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Saves one automatic library backup to the chosen folder ([AutoBackupScheduling] runs it). A lost grant or
 * a missing folder is recorded for Settings and never retried; other failures retry up to
 * [AutoBackupScheduling.MAX_ATTEMPTS] times.
 *
 * Notes in the store (attempt, success, problem) are tried a few times in place. One that still can't be
 * saved never stops the backup, and never runs it again: that would save a second file.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    internal var store: AutoBackupStore = AutoBackupStore(applicationContext)
    internal var folderFor: (String) -> AutoBackupFolder = { SafAutoBackupFolder(applicationContext, it.toUri()) }
    internal var export: suspend () -> LibraryJsonBackup.Export = { LibraryJsonBackup.fromSharedGraph(applicationContext) }
    internal var nowMs: () -> Long = System::currentTimeMillis
    internal var report: (AutoBackupOutcome) -> Unit = ::track

    /** Runs one store write; tests swap it to make the disk fail. */
    internal var writeStore: suspend (suspend AutoBackupStore.() -> Unit) -> Unit = { write -> store.write() }
    internal var storeRetryDelayMs: Long = STORE_RETRY_DELAY_MS

    /** The settings, or null when the store can't be read right now; tests swap it. */
    internal var readSettings: suspend () -> AutoBackupSettings? = { store.readable() }

    override suspend fun doWork(): Result {
        // Unreadable isn't "off": the run is tried again rather than skipped as if backups were turned off.
        val settings = readSettings() ?: return if (runAttemptCount < AutoBackupScheduling.MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        val folderUri = settings.folderUri
        // Turned off or the folder was cleared after this run was scheduled.
        if (!settings.isActive || folderUri == null) return Result.success()
        record { recordAttempt(nowMs()) }
        return when (val outcome = AutoBackupRunner(folderFor(folderUri), export, nowMs).run(settings.keepCount)) {
            is AutoBackupOutcome.Saved -> {
                // The file is saved either way; Settings catches up with the next run's note.
                record { recordSuccess(nowMs(), outcome.fileName, folderUri) }
                report(outcome)
                Result.success()
            }
            is AutoBackupOutcome.Failed ->
                if (outcome.retryable && runAttemptCount < AutoBackupScheduling.MAX_ATTEMPTS - 1) {
                    Result.retry()
                } else {
                    val recorded = record { recordProblem(outcome.code, folderUri) }
                    report(outcome)
                    // Unrecorded, the problem can't reach Settings, so the run at least ends as failed.
                    if (outcome.retryable || !recorded) Result.failure() else Result.success()
                }
        }
    }

    /** Saves one note, trying again briefly; false when every try failed (logged, never thrown). */
    private suspend fun record(write: suspend AutoBackupStore.() -> Unit): Boolean {
        repeat(STORE_TRIES) { attempt ->
            try {
                writeStore(write)
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w(TAG, "An automatic backup note wasn't saved: ${error.javaClass.simpleName}")
                if (attempt < STORE_TRIES - 1) delay(storeRetryDelayMs)
            }
        }
        return false
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
        const val TAG = "AutoBackupWorker"
        const val STORE_TRIES = 3
        const val STORE_RETRY_DELAY_MS = 500L
    }
}
