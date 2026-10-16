package cx.aswin.boxlore.core.analytics

import android.util.Log

/**
 * PostHog setup reads package info over binder, which stalled app startup on the main thread
 * (a startup ANR). Setup now runs on a background thread. Calls made before it finishes are
 * queued in order and replayed once it completes, so early events and super-properties are kept.
 * If setup fails (any throwable), PostHog never counts as ready: the queue is dropped and later
 * calls are ignored, so nothing waits on a client that was never initialised.
 */
object PostHogStartup {
    private const val TAG = "PostHogStartup"

    /** Bounds memory if setup never finishes; the oldest queued call is dropped first. */
    internal const val MAX_PENDING = 500

    private val lock = Any()
    private val pending = ArrayDeque<() -> Unit>()

    @Volatile
    var isReady: Boolean = false
        private set

    /** Setup threw; PostHog stays not ready and calls are dropped instead of queued. */
    @Volatile
    var hasFailed: Boolean = false
        private set

    /** Runs [action] now when PostHog is set up, otherwise after setup completes. */
    fun runWhenReady(action: () -> Unit) {
        synchronized(lock) {
            if (hasFailed) return
            if (!isReady) {
                if (pending.size >= MAX_PENDING) pending.removeFirst()
                pending.addLast(action)
                return
            }
        }
        runSafely(action)
    }

    /** Runs [setup] on a background thread, then replays queued calls. */
    fun start(setup: () -> Unit) {
        Thread({ finishSetup(setup) }, "posthog-setup").start()
    }

    /** Runs [setup]; replays the queue only when it succeeded, otherwise marks setup failed. */
    internal fun finishSetup(setup: () -> Unit, onFailure: (Throwable) -> Unit = { Log.e(TAG, "PostHog setup failed", it) }) {
        val succeeded =
            try {
                setup()
                true
            } catch (t: Throwable) {
                onFailure(t)
                false
            }
        if (succeeded) markReady() else markFailed()
    }

    private fun markFailed() {
        synchronized(lock) {
            hasFailed = true
            pending.clear()
        }
    }

    internal fun markReady() {
        while (true) {
            val batch =
                synchronized(lock) {
                    if (pending.isEmpty()) {
                        isReady = true
                        return
                    }
                    pending.toList().also { pending.clear() }
                }
            batch.forEach(::runSafely)
        }
    }

    /** Test hook: back to the pre-setup state. */
    internal fun resetForTest() {
        synchronized(lock) {
            pending.clear()
            isReady = false
            hasFailed = false
        }
    }

    private fun runSafely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.e(TAG, "PostHog call failed", e)
        }
    }
}
