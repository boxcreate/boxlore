package cx.aswin.boxlore.feature.info.branded.waveform

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.util.lerp

/**
 * Timings for the Waveform logo, in milliseconds, from the approved design boards: the bars that
 * move while an episode plays, and the logo intro that plays when you subscribe.
 */
internal object WaveformMotion {
    /** The whole logo intro, from the tap to the clean logo with Subscribed and its controls. */
    const val INTRO_MILLIS = 1_250

    /** When alerts and auto-download start sliding in beside Subscribed. */
    const val REVEAL_MILLIS = 820

    /** The red block's slice lands with the one haptic tap. */
    const val HAPTIC_MILLIS = 300

    private const val RISE_MILLIS = 430f
    private val Rise = CubicBezierEasing(0.25f, 1f, 0.4f, 1f)
    private val Slice = CubicBezierEasing(0.7f, 0f, 0.2f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** A bar's height during the intro: flat until its [delay], bursting past full, then settling. */
    fun introBarScale(millis: Float, delay: Int): Float = riseScale((millis - delay) / RISE_MILLIS)

    /** The rise: 4% to 132% by 68%, back to 94% by 84%, then 100%. */
    fun riseScale(progress: Float): Float = when {
        progress <= 0f -> 0.04f
        progress < 0.68f -> lerp(0.04f, 1.32f, Rise.transform(progress / 0.68f))
        progress < 0.84f -> lerp(1.32f, 0.94f, Rise.transform((progress - 0.68f) / 0.16f))
        progress < 1f -> lerp(0.94f, 1f, Rise.transform((progress - 0.84f) / 0.16f))
        else -> 1f
    }

    /** The red block slices in from its left edge between 300 and 540ms. */
    fun introBlockScale(millis: Float): Float = Slice.transform(progress(millis, 300f, 240f))

    /** A white flash over the block as it lands, fading from 470 to 730ms. */
    fun introFlash(millis: Float): Float = if (millis < 470f) 0f else 0.95f * (1f - EaseOut.transform(progress(millis, 470f, 260f)))

    /** Which of the glitch's four 60ms frames shows, from 560 to 800ms, or -1 outside it. */
    fun glitchFrame(millis: Float): Int = if (millis < 560f || millis >= 800f) -1 else ((millis - 560f) / 60f).toInt()

    /** The logo's jitter during the glitch, in dp: five 48ms steps. */
    fun introJitter(millis: Float): Pair<Float, Float> {
        if (millis < 560f || millis >= 800f) return 0f to 0f
        return JitterSteps[((millis - 560f) / 48f).toInt().coerceAtMost(JitterSteps.lastIndex)]
    }

    /** One beat after the glitch: the logo swells 14% taller and back, 860 to 1120ms. */
    fun introBeat(millis: Float): Float {
        val p = progress(millis, 860f, 260f)
        return when {
            p <= 0f || p >= 1f -> 1f
            p < 0.45f -> lerp(1f, 1.14f, EaseOut.transform(p / 0.45f))
            else -> lerp(1.14f, 1f, EaseOut.transform((p - 0.45f) / 0.55f))
        }
    }

    /** A bar's height while an episode plays: each of the four groups breathes on its own beat. */
    fun liveScale(millis: Long, phase: Int): Float {
        val (period, start) = LivePhases[phase.mod(LivePhases.size)]
        val local = millis - start
        val cycle = local.floorDiv(period)
        val within = local.mod(period).toFloat() / period
        val p = if (cycle % 2 == 0L) within else 1f - within
        return lerp(0.45f, 1.12f, EaseInOut.transform(p))
    }

    fun progress(millis: Float, start: Float, duration: Float): Float = ((millis - start) / duration).coerceIn(0f, 1f)

    private val JitterSteps = listOf(0f to 0f, -3f to 0f, 2f to 1f, -1f to -1f, 3f to 0f)

    /** Each group's period and how far into it the animation starts, as the board's negative delays. */
    private val LivePhases = listOf(820L to 0L, 1_050L to -300L, 680L to -500L, 1_240L to -200L)
}
