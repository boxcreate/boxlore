package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Welcome entry choreography on the shared motion clock. The cover reel leads: it starts at rest
 * and speeds up (see [WelcomeReelMotion]), the headline lines arrive while it gathers speed, the
 * reply follows, and Echo rises once the copy has landed and the reel is cruising.
 */
internal object WelcomeEntryTiming {
    const val FirstLineDelayMillis = 220L
    const val SecondLineDelayMillis = 360L
    const val ReplyDelayMillis = 700L
    const val LineDurationMillis = 700L

    const val EchoRiseDelayMillis = 1100L
    const val EchoRiseMillis = 480L

    /** Material 3 emphasized decelerate: copy glides in and settles softly. */
    private val LineEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Eased 0..1 entry progress for a line that starts at [delayMillis]. */
    fun lineProgress(elapsedMillis: Long, delayMillis: Long): Float =
        LineEasing.transform(((elapsedMillis - delayMillis).toFloat() / LineDurationMillis).coerceIn(0f, 1f))

    /** The reveal flag must not cut off a line that is still entering. */
    const val LastLineEndMillis = ReplyDelayMillis + LineDurationMillis
}
