package cx.aswin.boxlore.feature.info.branded

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/** One keyframe of a design board's animation: at [at] (0 to 1) the value is [value]; [easing] runs the step after it. */
internal class Keyframe(val at: Float, val value: Float, val easing: Easing = LinearEasing)

/**
 * The value at [p] (0 to 1) along [frames], easing each step by the easing of the keyframe it
 * starts from, as a CSS keyframe's timing function does. Before the first keyframe it holds the
 * first value, after the last the last.
 */
internal fun keyframes(p: Float, vararg frames: Keyframe): Float {
    if (p <= frames.first().at) return frames.first().value
    for (i in 0 until frames.size - 1) {
        val from = frames[i]
        val to = frames[i + 1]
        if (p <= to.at) return from.value + (to.value - from.value) * from.easing.transform((p - from.at) / (to.at - from.at))
    }
    return frames.last().value
}
