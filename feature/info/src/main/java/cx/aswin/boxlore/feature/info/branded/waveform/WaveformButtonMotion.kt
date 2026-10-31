package cx.aswin.boxlore.feature.info.branded.waveform

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.util.lerp

/** Timings for the Subscribe button during the logo intro, in milliseconds from the tap. */
internal object WaveformButtonMotion {
    /** The Subscribe button turns to the Subscribed colours in one cut. */
    const val BUTTON_SWAP_MILLIS = 780f

    /** "Subscribe" drops 10dp and fades in the first 120ms. */
    fun subscribeLabelOut(millis: Float): Float = EaseIn.transform(WaveformMotion.progress(millis, 0f, 120f))

    /** The button's small waveform: in from 100ms, flickering through the glitch, gone by 900ms. */
    fun buttonWaveAlpha(millis: Float): Float = when {
        millis < 100f -> 0f
        millis < 560f -> 1f
        millis < BUTTON_SWAP_MILLIS -> FlickAlpha[flickStep(millis)]
        else -> 1f - Ease.transform(WaveformMotion.progress(millis, BUTTON_SWAP_MILLIS, 120f))
    }

    /** The button waveform's shift in dp: a twitch during the glitch, then a 10dp drop as it leaves. */
    fun buttonWaveShift(millis: Float): Pair<Float, Float> = when {
        millis < 560f -> 0f to 0f
        millis < BUTTON_SWAP_MILLIS -> FlickShift[flickStep(millis)] to 0f
        else -> 0f to 10f * Ease.transform(WaveformMotion.progress(millis, BUTTON_SWAP_MILLIS, 120f))
    }

    /** A bar of the button's waveform bursts out like the logo's, a little quicker. */
    fun buttonWaveBarScale(millis: Float, delay: Int): Float = WaveformMotion.riseScale((millis - delay) / 380f)

    /**
     * "Subscribed" glitches in from 800ms in four quick steps and settles by 1060ms: its red and
     * cyan copies split apart, cross over, then close up and fade.
     */
    fun subscribedLabel(millis: Float): SubscribedLabelFrame {
        val local = millis - 800f
        return when {
            local < 0f -> SubscribedLabelFrame(alpha = 0f, shift = -3f, split = -4f, splitAlpha = 1f)
            local < 104f -> {
                val step = steps(local / 104f)
                SubscribedLabelFrame(alpha = step, shift = lerp(-3f, 2f, step), split = lerp(-4f, 3f, step), splitAlpha = 1f)
            }
            local < 260f -> {
                val step = steps((local - 104f) / 156f)
                SubscribedLabelFrame(alpha = 1f, shift = lerp(2f, 0f, step), split = lerp(3f, 0f, step), splitAlpha = 1f - step)
            }
            else -> SubscribedLabelFrame(alpha = 1f, shift = 0f, split = 0f, splitAlpha = 0f)
        }
    }

    /** Four even jumps, as CSS steps(4). */
    private fun steps(fraction: Float): Float = (fraction * 4f).toInt().coerceIn(0, 3) / 4f

    private fun flickStep(millis: Float): Int = ((millis - 560f) / 55f).toInt().coerceIn(0, 3)

    private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    private val FlickAlpha = listOf(1f, 0.2f, 1f, 0.4f)
    private val FlickShift = listOf(0f, 0f, 2f, -2f)
}

/** One frame of "Subscribed" glitching in: its opacity, its shift and its colour split in dp. */
internal data class SubscribedLabelFrame(val alpha: Float, val shift: Float, val split: Float, val splitAlpha: Float)
