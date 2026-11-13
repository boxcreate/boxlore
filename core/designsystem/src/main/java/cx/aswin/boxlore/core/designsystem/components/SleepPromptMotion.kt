package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.cos

/**
 * The sleep timer prompt's motion, from the approved design board, in milliseconds: the entrance
 * from the moment it shows, the confirmation from the moment a time is picked. Each part is a pure
 * function of its clock, read while drawing.
 */
internal object SleepPromptMotion {
    const val ENTER_MILLIS = 1_000f
    const val CONFIRM_MILLIS = 1_700f

    /** How long the confirmation stays after a pick before the prompt leaves. */
    const val CONFIRM_HOLD_MILLIS = 3_000L
    const val EXIT_MILLIS = 320

    /** A prompt that also offers the watch back asks two things, so it waits this much longer. */
    const val SECOND_CHOICE_MILLIS = 5_000L

    /** How long the prompt waits untouched before it leaves. */
    fun autoHideMillis(base: Long, offersWatch: Boolean): Long = if (offersWatch) base + SECOND_CHOICE_MILLIS else base
    const val SPIN_MILLIS = 24_000

    data class Pose(val alpha: Float = 1f, val scale: Float = 1f, val rotation: Float = 0f, val offsetDp: Float = 0f)

    data class Glow(val alpha: Float, val scale: Float)

    /** A star: how far in from the band's end, how far down it (as a fraction of its height), and its size. */
    data class Star(val fromEnd: Float, val down: Float, val radius: Float)

    val Stars = listOf(Star(26f, 0.45f, 7f), Star(38f, 0.72f, 5f), Star(14f, 0.86f, 4f))

    private val Drop = CubicBezierEasing(0.2f, 1.2f, 0.3f, 1f)
    private val Bounce = CubicBezierEasing(0.2f, 1.3f, 0.3f, 1f)
    private val Pop = CubicBezierEasing(0.3f, 1.5f, 0.4f, 1f)
    private val Settle = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
    private val Fold = CubicBezierEasing(0.3f, 1f, 0.4f, 1f)
    private val Sink = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)

    /** The card drops in from just above its place, scaling up from 92%. */
    fun card(t: Float): Pose {
        val e = Drop.transform(progress(t, 0f, 640f))
        return Pose(alpha = progress(t, 0f, 190f), scale = 0.92f + 0.08f * e, offsetDp = -28f + 28f * e)
    }

    /** The moon turns and grows into place. */
    fun moon(t: Float): Pose {
        val e = Bounce.transform(progress(t, 120f, 700f))
        return Pose(alpha = e.coerceIn(0f, 1f), scale = 0.4f + 0.6f * e, rotation = -90f + 90f * e)
    }

    /** Each time pops in after the one before it. */
    fun buttonPop(t: Float, index: Int): Pose {
        val e = Pop.transform(progress(t, 320f + index * 45f, 380f))
        return Pose(alpha = e.coerceIn(0f, 1f), scale = 0.82f + 0.18f * e)
    }

    /** End of episode rises in last. */
    fun endPop(t: Float): Pose {
        val e = Settle.transform(progress(t, 520f, 320f))
        return Pose(alpha = e.coerceIn(0f, 1f), offsetDp = 8f * (1f - e))
    }

    /** The question fades as the answer takes its place. */
    fun questionAlpha(c: Float): Float = 1f - progress(c, 300f, 180f)

    /** How much of the choices' height is still laid out, 1 to 0, as the card folds up. */
    fun choicesHeight(c: Float): Float = 1f - Fold.transform(progress(c, 300f, 460f))

    fun choicesAlpha(c: Float): Float = 1f - progress(c, 300f, 200f)

    /** How far the moon has set below its place, in dp: out of the band by the end. */
    fun moonSet(c: Float): Float = 110f * Sink.transform(progress(c, 480f, 760f))

    /** The time badge rising into the moon's place as it sets, turning as it comes. */
    fun badge(c: Float): Pose {
        val e = Bounce.transform(progress(c, 860f, 760f))
        return Pose(offsetDp = 110f * (1f - e), rotation = 40f * (1f - e))
    }

    /** "Pausing in … Good night." fades up once the badge is on its way. */
    fun answer(c: Float): Pose {
        val e = Settle.transform(progress(c, 980f, 320f))
        return Pose(alpha = e.coerceIn(0f, 1f), offsetDp = 6f * (1f - e))
    }

    /** Star [index]'s brightness and size at [phase] (0 to 1) of the twinkle cycle; the three take turns. */
    fun twinkle(phase: Float, index: Int): Glow {
        val local = (phase + index / 3f) % 1f
        val v = 0.5f - 0.5f * cos(2f * PI.toFloat() * local)
        return Glow(alpha = 0.35f + 0.65f * v, scale = 0.75f + 0.25f * v)
    }

    private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)
}
