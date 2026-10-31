package cx.aswin.boxlore.feature.info.branded.rottenmango

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlin.math.floor

/**
 * Timings for "Bada bing, bada boom" on tape, in milliseconds and dp, from the approved design board.
 * Each keyframe step is eased on its own, as the board's CSS eases them.
 */
internal object RottenMangoTape {
    const val DURATION_MILLIS = 1_480

    /** Alerts and auto-download slide in as Subscribed lands. */
    const val REVEAL_MILLIS = 1_040

    /** "BADA BOOM" hits, with the one buzz. */
    const val BOOM_MILLIS = 610

    private val Snap = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
    private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** "Subscribe" goes in 60ms. */
    fun subscribeLabel(millis: Float): Float = 1f - EaseIn.transform(progress(millis, 0f, 60f))

    /** The button is black under the set from 40ms. */
    fun baseBlack(millis: Float): Boolean = millis >= 40f

    /** The set's picture: how visible, how wide and tall, and how bright. */
    data class Screen(val alpha: Float, val scaleX: Float, val scaleY: Float, val brightness: Float)

    /** It switches on from a bright line at 40ms, then off to a line and a dot from 900ms; null while it is off. */
    fun screen(millis: Float): Screen? = when {
        millis < 40f -> null
        millis < 900f -> {
            val p = progress(millis, 40f, 150f)
            Screen(
                alpha = 1f,
                scaleX = 1f,
                scaleY = keyframes(listOf(0f to 0.03f, 0.6f to 1.05f, 1f to 1f), p, Snap),
                brightness = keyframes(listOf(0f to 4f, 0.6f to 1.6f, 1f to 1f), p, Snap),
            )
        }
        millis < 1_060f -> {
            val p = progress(millis, 900f, 160f)
            Screen(
                alpha = keyframes(listOf(0f to 1f, 0.8f to 1f, 1f to 0f), p, EaseIn),
                scaleX = keyframes(listOf(0f to 1f, 0.45f to 1f, 0.8f to 0.04f, 1f to 0f), p, EaseIn),
                scaleY = keyframes(listOf(0f to 1f, 0.45f to 0.03f, 1f to 0.03f), p, EaseIn),
                brightness = keyframes(listOf(0f to 1f, 0.45f to 3f, 0.8f to 5f, 1f to 5f), p, EaseIn),
            )
        }
        else -> null
    }

    private val GrainSpots = listOf(
        Offset(0f, 0f),
        Offset(-23f, 11f),
        Offset(17f, -19f),
        Offset(-9f, 27f),
        Offset(31f, 4f),
        Offset(-30f, -14f),
        Offset(8f, 22f),
        Offset(-14f, -31f),
    )

    /** The grain jumps to a new spot every 50ms. */
    fun grain(millis: Float): Offset = GrainSpots[floor(millis.coerceAtLeast(0f) % 400f / 50f).toInt()]

    /** The burst of static, from 540 to 610ms: the grain turns up and brightens. */
    fun isStatic(millis: Float): Boolean = millis >= 540f && millis < 610f

    fun grainAlpha(millis: Float): Float = if (isStatic(millis)) 0.92f else 0.26f

    fun grainGlow(millis: Float): Float = if (isStatic(millis)) 1.5f else 1f

    /** Tracking lines rolling up the picture, each 0 at the bottom edge and 1 just above the top. */
    fun trackingLines(millis: Float): List<Float> = listOfNotNull(rolling(millis, 250f, 300f), rolling(millis, 700f, 260f))

    /** "BADA BING", 190 to 540ms, jittering every 60ms; null while it is not showing. */
    fun bing(millis: Float): Offset? = if (millis < 190f || millis >= 540f) null else jitter(millis - 190f)

    /** "BADA BOOM": its size, how far its colours split, its glow (dp) and its jitter. */
    data class Boom(val scale: Float, val split: Float, val glow: Float, val jitter: Offset)

    /** It slams in at 610ms from 40% larger with its colours split wide, then jitters from 760ms. */
    fun boom(millis: Float): Boom? {
        if (millis < BOOM_MILLIS) return null
        val p = Snap.transform(progress(millis, BOOM_MILLIS.toFloat(), 140f))
        return Boom(lerp(1.4f, 1f, p), lerp(8f, 2f, p), lerp(12f, 9f, p), if (millis < 760f) Offset.Zero else jitter(millis - 760f))
    }

    /** The vertical hold slips as "BADA BOOM" hits: up 7dp, down 4, up 2 and back, in 10ms steps. */
    fun hold(millis: Float): Float {
        if (millis < BOOM_MILLIS || millis >= BOOM_MILLIS + 160f) return 0f
        val frames = listOf(0f, -7f, 4f, -2f, 0f)
        val p = (millis - BOOM_MILLIS) / 160f * 4f
        val step = floor(p).toInt().coerceAtMost(3)
        val within = floor((p - step) * 4f) / 4f
        return lerp(frames[step], frames[step + 1], within)
    }

    /** The Subscribed colour fills the button as the set goes off. */
    fun settle(millis: Float): Float = EaseOut.transform(progress(millis, 990f, 100f))

    /** "Subscribed" lands: fading in and settling from 4% larger. */
    fun subscribedLabel(millis: Float): Pair<Float, Float> {
        val p = EaseOut.transform(progress(millis, 1_060f, 180f))
        return p to lerp(1.04f, 1f, p)
    }
}

private fun rolling(millis: Float, start: Float, duration: Float): Float? =
    if (millis < start || millis >= start + duration) null else (millis - start) / duration

/** Where a jittering word sits, in dp, changing every 60ms. */
private val Jitter = listOf(Offset(0f, 0f), Offset(-2f, 0f), Offset(1f, 1f), Offset(3f, 0f), Offset(-1f, -1f))

private fun jitter(sinceMillis: Float): Offset = Jitter[floor(sinceMillis % 300f / 60f).toInt()]

private fun keyframes(frames: List<Pair<Float, Float>>, p: Float, easing: Easing): Float {
    val next = frames.indexOfFirst { it.first >= p }.coerceAtLeast(1)
    val (t0, v0) = frames[next - 1]
    val (t1, v1) = frames[next]
    val local = if (t1 > t0) ((p - t0) / (t1 - t0)).coerceIn(0f, 1f) else 1f
    return lerp(v0, v1, easing.transform(local))
}

private fun progress(millis: Float, start: Float, duration: Float): Float = ((millis - start) / duration).coerceIn(0f, 1f)
