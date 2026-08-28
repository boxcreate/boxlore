package cx.aswin.boxlore.core.ranking

import kotlin.math.exp

/**
 * Subscribe-recency score floor for deterministic Your Shows ranking.
 *
 * A genuinely new subscription stays near the top for three days, then decays
 * smoothly. The slight 0.97→0.94 slope preserves newest-first ordering among
 * multiple new subscriptions. Existing shows with stronger native scores can
 * still rank above the floor, so this is not a forced list position.
 */
object YourShowsSubscriptionRecency {
    const val PEAK_FLOOR = 0.97
    const val WINDOW_END_FLOOR = 0.94
    const val WINDOW_HOURS = 72.0
    const val POST_WINDOW_DECAY_HOURS = 48.0

    fun apply(
        score: Double,
        subscribedAt: Long,
        nowMs: Long,
    ): Double = maxOf(score, floor(subscribedAt, nowMs)).coerceIn(-1.0, 1.0)

    fun floor(
        subscribedAt: Long,
        nowMs: Long,
    ): Double {
        if (subscribedAt <= 0L) return -1.0
        val hours = (nowMs - subscribedAt).toDouble().coerceAtLeast(0.0) / 3_600_000.0
        return if (hours <= WINDOW_HOURS) {
            val progress = hours / WINDOW_HOURS
            PEAK_FLOOR - (PEAK_FLOOR - WINDOW_END_FLOOR) * progress
        } else {
            WINDOW_END_FLOOR * exp(-(hours - WINDOW_HOURS) / POST_WINDOW_DECAY_HOURS)
        }.coerceIn(0.0, 1.0)
    }
}
