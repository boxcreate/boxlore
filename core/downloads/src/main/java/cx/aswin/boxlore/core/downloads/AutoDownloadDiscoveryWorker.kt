package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class AutoDownloadDiscoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val preferences = cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder.require().userPreferencesRepository
        val gate = AutoDownloadBackgroundGate.create(applicationContext, preferences)
        val complete = kotlinx.coroutines.withTimeoutOrNull(AutoDownloadWorker.WORK_SLICE_MS) {
            gate.runGuarded { DownloadsDependenciesHolder.require().autoDownloadCoordinator.discover(gate::allowed) }
        }
        if (complete == true || !preferences.autoDownloadBackgroundSettingsStream.first().enabled) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}
