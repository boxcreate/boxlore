package cx.aswin.boxlore.feature.info.branded.jre

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Timings and shapes for the third eye, in milliseconds and dp, from the approved design board.
 * The button goes dark, an eye like the badge's opens in it, its dotted rays spread across the
 * button, it pulses once with a buzz, and it closes into Subscribed. The badge's own eye lights up
 * on the same clock.
 */
internal object JreEye {
    const val DURATION_MILLIS = 1_400

    /** Alerts and auto-download slide in as the eye closes. */
    const val REVEAL_MILLIS = 960

    /** The pulse, with its one buzz. */
    const val PULSE_MILLIS = 560

    /** The badge's third eye, as fractions of the badge. */
    const val BADGE_EYE_X = 0.4992f
    const val BADGE_EYE_Y = 0.2683f

    private val Open = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)
    private val Spread = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
    private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** "Subscribe" leaves, shrinking a little: its alpha and scale. */
    fun subscribeLabel(millis: Float): Pair<Float, Float> {
        val p = EaseIn.transform(progress(millis, 0f, 110f))
        return 1f - p to lerp(1f, 0.92f, p)
    }

    /** The button goes dark over 120ms. */
    fun darkness(millis: Float): Float = EaseOut.transform(progress(millis, 30f, 120f))

    /** Once dark, the Subscribe colour underneath is gone too, so no edge of it shows. */
    fun baseDark(millis: Float): Boolean = millis >= 150f

    /** How open the eye is (its height as a share of the open eye), or null before it appears. */
    fun lid(millis: Float): Float? = when {
        millis < 110f -> null
        millis < 780f -> keyframes(listOf(0f to 0.06f, 0.7f to 1.08f, 1f to 1f), progress(millis, 110f, 260f), Open)
        else -> lerp(1f, 0.06f, EaseIn.transform(progress(millis, 780f, 150f)))
    }

    /** The one beat: 16% larger and back, 560 to 730ms. */
    fun pulse(millis: Float): Float = keyframes(listOf(0f to 1f, 0.4f to 1.16f, 1f to 1f), progress(millis, PULSE_MILLIS.toFloat(), 170f), EaseOut)

    /** The warm glow behind the eye: up by 35% of its 420ms, then gone. */
    fun glow(millis: Float): Float = keyframes(listOf(0f to 0f, 0.35f to 1f, 1f to 0f), progress(millis, 420f, 420f), EaseOut)

    /**
     * Dot [index] along its ray: how far out it is (0 at the eye, 1 at its place), its scale and alpha.
     * Dots spread one after another from [firstMillis], then all fall back to the eye from 820ms.
     */
    fun dot(millis: Float, index: Int, firstMillis: Float): Triple<Float, Float, Float> {
        val back = EaseIn.transform(progress(millis, 820f + index * 10f, 140f))
        if (back > 0f) return Triple(1f - back, lerp(1f, 0.3f, back), 1f - back)
        val out = Spread.transform(progress(millis, firstMillis + index * 34f, 300f))
        return Triple(out, lerp(0.2f, 1f, out), out)
    }

    /** The Subscribed colour fills the button as the eye closes. */
    fun settle(millis: Float): Float = EaseOut.transform(progress(millis, 900f, 110f))

    /** "Subscribed" lands: fading in and settling from 4% larger. */
    fun subscribedLabel(millis: Float): Pair<Float, Float> {
        val p = EaseOut.transform(progress(millis, 980f, 180f))
        return p to lerp(1.04f, 1f, p)
    }

    /** One dot of the rays: where it ends up around the eye (dp), its radius and its place along the ray. */
    data class Dot(val target: Offset, val radius: Float, val index: Int)

    /** The button's rays: the badge's eight, the side ones running the width of the button. */
    val ButtonDots: List<Dot> = rays(
        listOf(
            0 to listOf(17f, 25f, 33f, 42f, 51f, 61f, 72f, 84f, 97f),
            180 to listOf(17f, 25f, 33f, 42f, 51f, 61f, 72f, 84f, 97f),
            90 to listOf(14f, 20f),
            270 to listOf(14f, 20f),
            45 to listOf(16f, 23f, 30f),
            135 to listOf(16f, 23f, 30f),
            225 to listOf(16f, 23f, 30f),
            315 to listOf(16f, 23f, 30f),
        ),
    ) { i -> max(1.2f, 3.0f - 0.22f * i) }

    /** The badge's rays, spreading just past its own dots. */
    val BadgeDots: List<Dot> = rays((0 until 360 step 45).map { it to listOf(10f, 15f, 21f, 28f) }) { i -> max(1.0f, 1.9f - 0.3f * i) }

    const val BUTTON_FIRST_DOT = 230f
    const val BADGE_FIRST_DOT = 300f

    private fun rays(spec: List<Pair<Int, List<Float>>>, radius: (Int) -> Float): List<Dot> = spec.flatMap { (angle, distances) ->
        val a = Math.toRadians(angle.toDouble())
        distances.mapIndexed { i, d -> Dot(Offset((d * cos(a)).toFloat(), (d * sin(a)).toFloat()), radius(i), i) }
    }

    private fun keyframes(frames: List<Pair<Float, Float>>, p: Float, easing: Easing): Float {
        val next = frames.indexOfFirst { it.first >= p }.coerceAtLeast(1)
        val (t0, v0) = frames[next - 1]
        val (t1, v1) = frames[next]
        val local = if (t1 > t0) ((p - t0) / (t1 - t0)).coerceIn(0f, 1f) else 1f
        return lerp(v0, v1, easing.transform(local))
    }

    private fun progress(millis: Float, start: Float, duration: Float): Float = ((millis - start) / duration).coerceIn(0f, 1f)
}
