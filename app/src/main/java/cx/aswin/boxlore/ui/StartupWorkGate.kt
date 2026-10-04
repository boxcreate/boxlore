package cx.aswin.boxlore.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide optional work waits for UI readiness, or an idle headless launch. */
internal class StartupWorkGate {
    private val ready = CompletableDeferred<Unit>()
    private val initialization = Mutex()
    private var uiCreated = false

    /** Called on the main thread before the Activity starts building its content. */
    fun onUiCreated() {
        uiCreated = true
    }

    /** Workers/widget broadcasts have no screen to signal readiness. */
    fun onMainQueueIdle() {
        if (!uiCreated) markReady()
    }

    /** There is no visible first frame to protect once the UI moves to the background. */
    fun onUiStopped() {
        markReady()
    }

    fun markReady() {
        ready.complete(Unit)
    }

    suspend fun awaitReady() {
        ready.await()
    }

    /** Suspend callers instead of releasing all optional initialization at once. */
    suspend fun runWhenReady(work: suspend () -> Unit) {
        awaitReady()
        initialization.withLock { work() }
    }
}
