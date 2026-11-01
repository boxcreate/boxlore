package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.util.lerp

/**
 * Timings for "Daybreak", in milliseconds and dp, from the approved design board: the show's dawn
 * rises inside the Subscribe button, a sun clears the horizon, and daylight settles into Subscribed.
 */
internal object DailyDawn {
    const val DURATION_MILLIS = 1_200

    /** Alerts and auto-download slide in as Subscribed settles. */
    const val REVEAL_MILLIS = 1_000

    /** The one light tap, as the sun's disc clears the button's bottom edge. */
    const val SUN_CLEAR_MILLIS = 460

    /** The sun's diameter, its left edge's share of the way across the button, and the button's height, in dp. */
    const val SUN_DIAMETER = 26f
    const val SUN_ACROSS = 0.3f
    const val BUTTON_HEIGHT = 48f

    /** How far the sun's soft glow reaches from its centre, in dp. */
    const val SUN_GLOW_RADIUS = 30f

    /** Where the sun's top starts, below the button with its glow out of sight, and where it comes to rest. */
    private const val SUN_START_TOP = 80f
    private const val SUN_END_TOP = 11f

    private val Sky = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)
    private val Rise = CubicBezierEasing(0.15f, 0.55f, 0.25f, 1f)
    private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val Daylight = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** "Subscribe" sinks away: its alpha, and how far down it has gone (0 to 1 of 5dp). */
    fun subscribeLabel(millis: Float): Pair<Float, Float> {
        val p = EaseIn.transform(progress(millis, 0f, 140f))
        return (1f - p) to p
    }

    /** How much of the button the dawn has risen up from the bottom edge, 0 to 1. */
    fun sky(millis: Float): Float = Sky.transform(progress(millis, 60f, 640f))

    /** The sun's top, in dp from the button's top. */
    fun sunTop(millis: Float): Float = lerp(SUN_START_TOP, SUN_END_TOP, Rise.transform(progress(millis, 200f, 620f)))

    /** The sun fades into the daylight. */
    fun sunAlpha(millis: Float): Float = 1f - EaseIn.transform(progress(millis, 860f, 260f))

    /** Daylight washes the sky into the Subscribed colour, 0 to 1. */
    fun daylight(millis: Float): Float = Daylight.transform(progress(millis, 840f, 310f))

    /** "Subscribed" comes up as the daylight settles. */
    fun subscribedLabel(millis: Float): Float = EaseOut.transform(progress(millis, 900f, 280f))

    private fun progress(millis: Float, start: Float, duration: Float): Float = ((millis - start) / duration).coerceIn(0f, 1f)
}
