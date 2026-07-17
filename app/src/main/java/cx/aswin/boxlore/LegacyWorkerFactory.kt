package cx.aswin.boxlore

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Maps pre-rename WorkManager worker class names (`cx.aswin.boxcast.*`) to the
 * current `cx.aswin.boxlore.*` implementations for one release after the package rename.
 */
class LegacyWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val resolvedClassName = LEGACY_WORKER_ALIASES[workerClassName] ?: return null
        return try {
            val clazz = Class.forName(resolvedClassName).asSubclass(ListenableWorker::class.java)
            val constructor = clazz.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            constructor.newInstance(appContext, workerParameters)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val LEGACY_WORKER_ALIASES = mapOf(
            "cx.aswin.boxcast.core.data.SmartDownloadWorker" to
                "cx.aswin.boxlore.core.data.SmartDownloadWorker",
            "cx.aswin.boxcast.core.data.AutoDownloadWorker" to
                "cx.aswin.boxlore.core.data.AutoDownloadWorker",
            "cx.aswin.boxcast.core.data.PurgeSmartDownloadsWorker" to
                "cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker",
        )
    }
}
