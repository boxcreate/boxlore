package cx.aswin.boxlore.core.model.text

/**
 * Whole minutes of listening left, one rule for every screen that says "time left", so Home's hero
 * and the mini player under it agree: a partial minute counts as a whole one, and there is always at
 * least one minute left. Null when the length isn't known.
 */
object ListeningTime {
    private const val MILLIS_PER_MINUTE = 60_000L

    /** Minutes left from [positionMs] in an episode [durationMs] long. */
    fun minutesLeft(positionMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0L) return null
        val left = durationMs - positionMs.coerceIn(0L, durationMs)
        return ((left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).coerceAtLeast(1L)
    }

    /** Minutes left from a stored share played, [progress] (0 to 1), of an episode [durationSec] long. */
    fun minutesLeft(durationSec: Long, progress: Float): Long? {
        if (durationSec <= 0L) return null
        val played = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val durationMs = durationSec * 1_000L
        return minutesLeft((durationMs * played.toDouble()).toLong(), durationMs)
    }
}
