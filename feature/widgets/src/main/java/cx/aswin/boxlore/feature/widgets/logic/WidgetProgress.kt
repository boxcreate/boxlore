package cx.aswin.boxlore.feature.widgets.logic

/** What the progress ring and the time-left label show, derived from a playback position. */
object WidgetProgress {
    /** How many visible steps the ring has. A position change inside one step is not worth a repaint. */
    const val RING_STEPS = 120

    private const val MS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60

    /** Played share of the episode, or 0 while the length is unknown. */
    fun fraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs).toFloat()
    }

    /** The ring step [positionMs] falls in, from 0 to [RING_STEPS]. */
    fun ringStep(positionMs: Long, durationMs: Long): Int = (fraction(positionMs, durationMs) * RING_STEPS).toInt()

    /**
     * Whole minutes left, rounded up so the label reaches zero only when the episode ends, or null while
     * the length is unknown.
     */
    fun minutesLeft(positionMs: Long, durationMs: Long): Int? {
        if (durationMs <= 0L) return null
        val remainingMs = (durationMs - positionMs.coerceIn(0L, durationMs))
        return ((remainingMs + MS_PER_MINUTE - 1) / MS_PER_MINUTE).toInt()
    }

    /** Hours and minutes of a [minutesLeft] value, for the label. */
    data class TimeLeft(val hours: Int, val minutes: Int)

    fun timeLeft(positionMs: Long, durationMs: Long): TimeLeft? {
        val minutes = minutesLeft(positionMs, durationMs) ?: return null
        if (minutes <= 0) return null
        return TimeLeft(hours = minutes / MINUTES_PER_HOUR, minutes = minutes % MINUTES_PER_HOUR)
    }
}
