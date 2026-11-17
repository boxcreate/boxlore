package cx.aswin.boxlore.core.catalog

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Library-wide work the API's rate limits keep from finishing in one go: shows a cloud pull left as
 * placeholders, OPML outlines still pending. Each [request] (after a foreground pass) runs [steps]
 * in order on [scope], one run at a time, paced by [ApiRouteBudget] so it may take minutes; it
 * runs apart from the pass, so pull to refresh never waits for it. It stops when the app leaves
 * the foreground; what is left stays saved and the next run continues it. A failing step is logged
 * and the next one still runs.
 */
class ForegroundCatchUp(
    private val scope: CoroutineScope,
    private val foreground: StateFlow<Boolean>,
    private val steps: List<suspend () -> Unit>,
) {
    private val running = AtomicBoolean(false)

    /** Starts a run unless one is running, the app is in the background, or there is nothing to run. */
    fun request(): Job? {
        if (steps.isEmpty() || !foreground.value || !running.compareAndSet(false, true)) return null
        return scope.launch {
            try {
                coroutineScope {
                    val work = async { steps.forEach { runStep(it) } }
                    val watcher =
                        launch {
                            foreground.first { !it }
                            work.cancel()
                        }
                    try {
                        work.await()
                    } finally {
                        watcher.cancel()
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun runStep(step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Catch-up step failed", e)
        }
    }

    private companion object {
        const val TAG = "ForegroundCatchUp"
    }
}
