package cx.aswin.boxlore.backup

import android.util.Log
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import java.io.FileNotFoundException
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One finished automatic backup run. */
internal sealed interface AutoBackupOutcome {
    data class Saved(val fileName: String, val subscriptionCount: Int, val pruned: Int) : AutoBackupOutcome

    /** [code] is an [AutoBackupProblem] code; [retryable] problems may go away on their own. */
    data class Failed(val code: String, val retryable: Boolean) : AutoBackupOutcome
}

/** Builds one backup and saves it to [folder], then removes automatic backups beyond the ones to keep. */
internal class AutoBackupRunner(
    private val folder: AutoBackupFolder,
    private val export: suspend () -> LibraryJsonBackup.Export,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun run(keep: Int): AutoBackupOutcome = gate.withLock {
        withContext(Dispatchers.IO) {
            reach() ?: save(keep)
        }
    }

    private fun reach(): AutoBackupOutcome.Failed? = try {
        folder.checkReachable()
        null
    } catch (error: Exception) {
        classify(error)
    }

    private suspend fun save(keep: Int): AutoBackupOutcome {
        val backup =
            try {
                export()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "The automatic backup couldn't be built: ${kind(error)}")
                return AutoBackupOutcome.Failed(AutoBackupProblem.EXPORT_FAILED, retryable = true)
            }
        val name = AutoBackupFiles.fileName(nowMs(), zone)
        try {
            folder.write(name, AutoBackupFiles.tempName(name), backup.bytes)
        } catch (error: Exception) {
            Log.w(TAG, "The automatic backup couldn't be saved: ${kind(error)}")
            return writeFailure(error)
        }
        return AutoBackupOutcome.Saved(name, backup.subscriptionCount, prune(keep, name))
    }

    /** Pruning never fails a saved backup; a file left over is removed next time. */
    private fun prune(keep: Int, saved: String): Int = try {
        val names = folder.listNames()
        val stale = AutoBackupFiles.toDelete(names, keep, saved) + names.filter(AutoBackupFiles::isLeftoverTemp)
        stale.count { name -> runCatching { folder.delete(name) }.isSuccess }
    } catch (error: Exception) {
        Log.w(TAG, "Older automatic backups weren't removed: ${kind(error)}")
        0
    }

    /**
     * Only the exception's type is logged: storage providers put the folder's path in their messages, and
     * logs can travel with feedback.
     */
    private fun kind(error: Exception): String = error.javaClass.simpleName

    /**
     * A file the provider couldn't open doesn't prove the folder is gone (a sync app may have removed the new
     * file), so the folder is checked again: still there, the run is retried like any other write error.
     */
    private fun writeFailure(error: Exception): AutoBackupOutcome.Failed =
        if (error is FileNotFoundException) {
            reach() ?: AutoBackupOutcome.Failed(AutoBackupProblem.IO_ERROR, retryable = true)
        } else {
            classify(error)
        }

    private fun classify(error: Exception): AutoBackupOutcome.Failed = when (error) {
        is SecurityException -> AutoBackupOutcome.Failed(AutoBackupProblem.PERMISSION_LOST, retryable = false)
        is FileNotFoundException -> AutoBackupOutcome.Failed(AutoBackupProblem.FOLDER_MISSING, retryable = false)
        else -> AutoBackupOutcome.Failed(AutoBackupProblem.IO_ERROR, retryable = true)
    }

    private companion object {
        const val TAG = "AutoBackupRunner"

        /** The scheduled run and Back up now never write at the same time. */
        val gate = Mutex()
    }
}
