package cx.aswin.boxlore.lifecycle

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Resume and preference reconciliation share one cancellable, foreground-only scan owner. */
internal class AutoDownloadForegroundScan(
    private val scope: CoroutineScope,
    private val loadIds: suspend () -> Set<String>,
    private val scanCached: suspend (id: String, canProceed: suspend () -> Boolean) -> Unit,
) {
    private val foreground = AtomicBoolean(false)
    private val activeJob = AtomicReference<Job?>(null)

    fun setForeground(active: Boolean) {
        foreground.set(active)
        if (!active) activeJob.getAndSet(null)?.cancel()
    }

    fun request(ids: Set<String>? = null) {
        if (!foreground.get()) return
        // Publish ownership before running so onStop can also cancel a newly requested scan.
        val job = scope.launch(start = CoroutineStart.LAZY) { scan(ids) }
        activeJob.getAndSet(job)?.cancel()
        if (foreground.get()) job.start() else job.cancel()
    }

    private suspend fun scan(ids: Set<String>?) {
        val scanContext = currentCoroutineContext()
        val canProceed: suspend () -> Boolean = { foreground.get() && scanContext.isActive }
        try {
            for (id in ids ?: loadIds()) {
                if (!canProceed()) return
                scanCached(id, canProceed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AutoDownloadLifecycle", "Unable to scan cached releases", e)
        }
    }
}
