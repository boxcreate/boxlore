package cx.aswin.boxlore.core.playback

/**
 * Decides what happens after a playback error. Skipping a failed episode is useful once, but when
 * every stream fails (offline, CDN outage) unbounded skipping never ends because Smart Queue keeps
 * refilling, and calling play() after each error overrides the listener's pause.
 */
internal class PlaybackErrorSkipGuard(
    private val maxConsecutiveFailures: Int = DEFAULT_MAX_CONSECUTIVE_FAILURES,
) {
    enum class Action {
        /** Remove the failed episode and start the next one. */
        SKIP_AND_PLAY,

        /** Remove the failed episode and load the next one without starting it. */
        SKIP_PAUSED,

        /** Stop skipping: keep the queue and stay paused on the failed episode. */
        STOP,

        /** Nothing left after the failed episode. */
        CLEAR_QUEUE,
    }

    var consecutiveFailures: Int = 0
        private set

    fun onError(hasNext: Boolean, playWhenReady: Boolean): Action {
        consecutiveFailures += 1
        return when {
            !hasNext -> Action.CLEAR_QUEUE
            consecutiveFailures >= maxConsecutiveFailures -> Action.STOP
            playWhenReady -> Action.SKIP_AND_PLAY
            else -> Action.SKIP_PAUSED
        }
    }

    /** Any episode actually playing proves streams work again. */
    fun onPlaybackSucceeded() {
        consecutiveFailures = 0
    }

    /** A listener-started queue gets a fresh allowance. */
    fun reset() {
        consecutiveFailures = 0
    }

    companion object {
        const val DEFAULT_MAX_CONSECUTIVE_FAILURES = 3
    }
}
