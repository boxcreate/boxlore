package cx.aswin.boxlore.core.designsystem.component

/**
 * Peek transform for [PredictiveBackWrapper].
 *
 * Progress after a gesture ends (commit or cancel) must be rest, not 1.
 * The wrapper stays around the NavHost, so a completed Back that replaces
 * the start destination (cold-start Subscriptions → Home) would otherwise
 * leave scale at 0.9.
 */
internal object PredictiveBackPeek {
    const val REST_PROGRESS = 0f
    const val PEEK_SCALE_FACTOR = 0.1f

    fun scaleFor(progress: Float): Float = 1f - (progress * PEEK_SCALE_FACTOR)

    fun progressAfterGesture(): Float = REST_PROGRESS
}
