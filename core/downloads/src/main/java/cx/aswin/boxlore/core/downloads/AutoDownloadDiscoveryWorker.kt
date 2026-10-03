package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class AutoDownloadDiscoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val complete = kotlinx.coroutines.withTimeoutOrNull(AutoDownloadWorker.WORK_SLICE_MS) {
            DownloadsDependenciesHolder.require().autoDownloadCoordinator.discover()
        } == true
        if (complete) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}
