package cx.aswin.boxlore.feature.info.branded.crimejunkie

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Timings for the flashlight moment, in milliseconds, from the approved design board. The lights
 * go out on the button, a torch beam sweeps across and back with a slight hand wobble, finds the
 * show's ink splatter with one tap, opens up to fill the button as the lights come on, and the
 * button settles on Subscribed.
 */
internal object CrimeJunkieTorch {
    const val DURATION_MILLIS = 1_350

    /** Alerts and auto-download slide in as the light floods the button. */
    const val REVEAL_MILLIS = 1_000

    /** The beam finds the splatter: one tap. */
    const val FOUND_MILLIS = 740

    private const val SCAN_START = 60f
    private const val SCAN_MILLIS = 680f
    private const val FLOOD_START = 780f
    private const val FLOOD_MILLIS = 240f
    private const val FLOOD_RADIUS = 260f
    private const val BEAM_RADIUS = 30f
    private const val LIGHTS_ON_START = 820f
    private const val DARK_UNTIL = 1_010f
    private const val SETTLED_FROM = 1_050f

    private val Scan = CubicBezierEasing(0.45f, 0.05f, 0.4f, 1f)
    private val Flood = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Where the beam points, as fractions of the button's width and height, through the sweep. */
    private val BeamX = listOf(0f to 0.06f, 0.38f to 0.88f, 0.64f to 0.28f, 0.78f to 0.46f, 0.88f to 0.43f, 1f to 0.44f)
    private val BeamY = listOf(0f to 0.52f, 0.38f to 0.42f, 0.64f to 0.60f, 0.78f to 0.47f, 0.88f to 0.52f, 1f to 0.50f)
    private val BeamRadius = listOf(0f to 0f, 0.1f to BEAM_RADIUS, 1f to BEAM_RADIUS)

    /** The splatter's centre, as fractions of the button. */
    val InkCentre = Offset(0.44f, 0.5f)

    /** The beam's centre (fractions of the button) and its lit radius in dp, the flood included. */
    fun beam(millis: Float): Pair<Offset, Float> {
        val p = progress(millis, SCAN_START, SCAN_MILLIS)
        val flood = FLOOD_RADIUS * Flood.transform(progress(millis, FLOOD_START, FLOOD_MILLIS))
        return Offset(keyframes(BeamX, p), keyframes(BeamY, p)) to keyframes(BeamRadius, p) + flood
    }

    /** The beam switches on over 80ms. */
    fun beamAlpha(millis: Float): Float = Ease.transform(progress(millis, SCAN_START, 80f))

    /** The lit patch turns from torchlight into the Subscribed colour as the lights come on. */
    fun lightsOn(millis: Float): Float = EaseInOut.transform(progress(millis, LIGHTS_ON_START, 220f))

    /** The beam's warm glow: on with the beam, gone as the lights come on. */
    fun glowAlpha(millis: Float): Float = beamAlpha(millis) * (1f - EaseOut.transform(progress(millis, 880f, 200f)))

    /** The splatter pops 12% when found. */
    fun inkScale(millis: Float): Float {
        val p = progress(millis, FOUND_MILLIS.toFloat(), 220f)
        val pop = when {
            p <= 0f || p >= 1f -> 1f
            p < 0.45f -> lerp(1f, 1.12f, EaseOut.transform(p / 0.45f))
            else -> lerp(1.12f, 1f, EaseOut.transform((p - 0.45f) / 0.55f))
        }
        return pop * lerp(1f, 0.92f, inkGone(millis))
    }

    /** The splatter fades as the lights come on. */
    fun inkAlpha(millis: Float): Float = 1f - inkGone(millis)

    private fun inkGone(millis: Float) = EaseIn.transform(progress(millis, 900f, 140f))

    /** "Subscribe" goes with the lights. */
    fun subscribeLabelOut(millis: Float): Float = EaseIn.transform(progress(millis, 0f, 120f))

    /** The dark room behind the beam, until the light has flooded the button. */
    fun dark(millis: Float): Boolean = millis < DARK_UNTIL

    /** The usual Subscribed colour fills the button. */
    fun settled(millis: Float): Boolean = millis >= SETTLED_FROM

    /** "Subscribed" lands: fading in and settling from 4% larger. */
    fun subscribedLabel(millis: Float): Pair<Float, Float> {
        val p = EaseOut.transform(progress(millis, 1_030f, 180f))
        return p to lerp(1.04f, 1f, p)
    }

    private fun keyframes(frames: List<Pair<Float, Float>>, p: Float, easing: Easing = Scan): Float {
        val next = frames.indexOfFirst { it.first >= p }.coerceAtLeast(1)
        val (t0, v0) = frames[next - 1]
        val (t1, v1) = frames[next]
        val local = if (t1 > t0) ((p - t0) / (t1 - t0)).coerceIn(0f, 1f) else 1f
        return lerp(v0, v1, easing.transform(local))
    }

    private fun progress(millis: Float, start: Float, duration: Float): Float = ((millis - start) / duration).coerceIn(0f, 1f)

    /** The splatter's outline in dp around its centre: a blob with seven short spikes, a little flattened. */
    val InkOutline: List<Offset> = (0 until 144).map { i ->
        val t = 2 * PI * i / 144
        var r = 15 * (1 + 0.10 * sin(4 * t + 1) + 0.06 * sin(7 * t + 2))
        listOf(0.3, 1.25, 2.1, 2.9, 3.7, 4.6, 5.5).forEachIndexed { k, spike ->
            val d = min(abs(t - spike), 2 * PI - abs(t - spike))
            r += (if (k % 2 == 1) 6.5 else 4.0) * exp(-(d / 0.1) * (d / 0.1))
        }
        Offset((r * cos(t)).toFloat(), (r * sin(t) * 0.85).toFloat())
    }

    /** The splatter's droplets: centre and radius in dp, and which of the splatter's colours. */
    val Droplets = listOf(
        Triple(Offset(-30f, -8f), 3.2f, 0),
        Triple(Offset(-24f, 11f), 2.2f, 0),
        Triple(Offset(27f, -12f), 2.8f, 2),
        Triple(Offset(33f, 6f), 2f, 2),
        Triple(Offset(14f, 15f), 1.8f, 1),
        Triple(Offset(-12f, -16f), 1.6f, 1),
    )
}
