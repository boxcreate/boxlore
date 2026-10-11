package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.prefs.FullPlayerStyle

/** Video retains its existing viewing, audio-only and fullscreen controls. */
internal fun useMinimalPlayer(style: String, isVideo: Boolean): Boolean =
    FullPlayerStyle.sanitize(style) == FullPlayerStyle.BUNPOD && !isVideo

internal data class RemainingTimeParts(val hours: Long, val minutes: Long)

/** Round partial minutes up; never show a negative time or split a unit across lines. */
internal fun minimalRemainingTime(durationMs: Long, positionMs: Long): RemainingTimeParts {
    val remaining = durationMs.coerceAtLeast(0L) - positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val minutes = remaining / 60_000L + if (remaining % 60_000L > 0L) 1L else 0L
    return RemainingTimeParts(minutes / 60L, minutes % 60L)
}

/** Only unconsumed upward drags reveal the queue; flings and downward collapse do not. */
internal class MinimalQueueSwipe(private val threshold: Float) {
    private var distance = 0f
    private var triggered = false

    fun drag(availableY: Float): Boolean {
        if (!availableY.isFinite() || availableY >= 0f) {
            reset()
            return false
        }
        distance += -availableY
        if (triggered || distance < threshold.coerceAtLeast(1f)) return false
        triggered = true
        return true
    }

    fun reset() {
        distance = 0f
        triggered = false
    }
}
