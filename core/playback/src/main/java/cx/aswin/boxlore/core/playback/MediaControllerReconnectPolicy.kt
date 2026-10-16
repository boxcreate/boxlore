package cx.aswin.boxlore.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bounded retries for a MediaController connection the session rejected. */
internal object MediaControllerReconnectPolicy {
    const val MAX_ATTEMPTS = 3
    private const val BASE_DELAY_MS = 1_000L

    fun delayMs(attempt: Int): Long = BASE_DELAY_MS * attempt.coerceAtLeast(1)
}

/** Schedules [connect] on the main thread after a failed attempt, within [MediaControllerReconnectPolicy]. */
internal class MediaControllerReconnector(
    private val scope: CoroutineScope,
    private val needsConnection: () -> Boolean,
    private val connect: () -> Unit,
) {
    private var attempts = 0

    fun onConnected() {
        attempts = 0
    }

    fun schedule() {
        val attempt = attempts + 1
        if (attempt > MediaControllerReconnectPolicy.MAX_ATTEMPTS) return
        attempts = attempt
        scope.launch {
            delay(MediaControllerReconnectPolicy.delayMs(attempt))
            withContext(PlaybackThreadPolicy.mainDispatcher) {
                if (needsConnection()) connect()
            }
        }
    }
}
