package cx.aswin.boxlore.core.analytics

import android.util.Log

/**
 * PostHog setup reads package info over binder, which stalled app startup on the main thread
 * (a startup ANR). Setup now runs on a background thread. Calls made before it finishes are
 * queued in order and replayed once it completes, so early events and super-properties are kept.
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

    /** Runs [action] now when PostHog is set up, otherwise after setup completes. */
    fun runWhenReady(action: () -> Unit) {
        synchronized(lock) {
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
        Thread(
            {
                runSafely(setup)
                markReady()
            },
            "posthog-setup",
        ).start()
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
