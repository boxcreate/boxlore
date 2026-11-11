package cx.aswin.boxlore.core.playback.service

import android.os.SystemClock
import androidx.media3.common.Player

/**
 * Keeps a seek from looking like a stop to the car.
 *
 * A seek makes the player report "buffering" for a split second, even inside audio it has already
 * loaded. Android Auto redraws its whole screen whenever playback stops or starts, and the redraw
 * forgets how far a list was scrolled: pressing seek on the steering wheel sent the open list back
 * to the top. While a seek settles, the session goes on reporting "playing" instead. Buffering
 * that outlasts [SETTLE_MS] is real, and is reported as it is.
 */
internal class SeekSettleWindow(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    /** A seek's buffering is now being kept back; the owner checks on it once [SETTLE_MS] has passed. */
    private val onSettling: () -> Unit = {},
) {
    private var settlingUntilMs = 0L

    /**
     * Call just before a seek within the current episode; [wasPlaying] is whether audio was actually
     * running. A press that lands while an earlier one is still settling belongs to the same seek:
     * the audio has stopped for the moment, but only because of that seek.
     */
    fun onSeek(wasPlaying: Boolean) {
        if (!wasPlaying && now() >= settlingUntilMs) {
            settlingUntilMs = 0L
            return
        }
        settlingUntilMs = now() + SETTLE_MS
        onSettling()
    }

    /** What the session reports while the player's real state is [state]. */
    fun reported(state: Int): Int = if (state == Player.STATE_BUFFERING && now() < settlingUntilMs) Player.STATE_READY else state

    companion object {
        const val SETTLE_MS = 1_000L
    }
}
