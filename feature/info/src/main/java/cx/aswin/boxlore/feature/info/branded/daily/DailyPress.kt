package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.util.lerp

/**
 * Timings for "Off the press", in milliseconds and dp, from the approved design board: a steel
 * roller runs across the Subscribe button, printing a fresh sheet with Subscribed on it behind it.
 */
internal object DailyPress {
    const val DURATION_MILLIS = 1_150

    /** Alerts and auto-download slide in once the sheet is printed. */
    const val REVEAL_MILLIS = 720

    /** The roller bites with one light tap. */
    const val TAP_MILLIS = 150

    private const val ROLL_START = 90f
    private const val ROLL_LENGTH = 600f

    private val Press = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
    private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** "Subscribe" lifts away: its alpha, and how far up it has gone (0 to 1 of 6dp). */
    fun subscribeLabel(millis: Float): Pair<Float, Float> {
        val p = EaseIn.transform(progress(millis, 0f, 120f))
        return (1f - p) to p
    }

    /** How much of the button is printed, left to right; the roller sits at that edge. */
    fun printed(millis: Float): Float = Press.transform(progress(millis, ROLL_START, ROLL_LENGTH))

    /** The roller's opacity and height as it lifts off the sheet, or null before it bites and after it has gone. */
    fun roller(millis: Float): Pair<Float, Float>? {
        if (millis < ROLL_START || millis >= 840f) return null
        val off = EaseIn.transform(progress(millis, 690f, 150f))
        return (1f - off) to lerp(1f, 0.55f, off)
    }

    /** The printed "Subscribed" comes up from 78% to full ink as the roller lifts. */
    fun printedInk(millis: Float): Float = lerp(0.78f, 1f, EaseOut.transform(progress(millis, 680f, 220f)))

    private fun progress(millis: Float, start: Float, duration: Float): Float = ((millis - start) / duration).coerceIn(0f, 1f)
}
