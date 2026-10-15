package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A card's lean in degrees: [x] tips the top away, [y] turns the right edge away. */
internal data class EchoCardLean(val x: Float, val y: Float)

/**
 * The show card behaves like a real card held in front of you. It drifts very slightly while
 * idle; pressing on it pushes that spot away, following the finger; letting go springs it back
 * with a small wobble. Its foil catches the light as it turns. Purely decorative: the buttons
 * answer the card, and a system with animations off keeps it still.
 */
internal object EchoCardPhysics {
    const val MaxLeanDegrees = 12f
    const val DriftMillis = 7_000f

    /** Lean toward a press at ([x], [y]) on a card [width] by [height]: that point sinks away. */
    fun lean(x: Float, y: Float, width: Float, height: Float): EchoCardLean {
        if (width <= 0f || height <= 0f) return EchoCardLean(0f, 0f)
        val across = ((x / width) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
        val down = ((y / height) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
        return EchoCardLean(x = -down * MaxLeanDegrees, y = across * MaxLeanDegrees)
    }

    /** Idle drift at [phase] (0-1): a slow figure of eight of a couple of degrees. */
    fun drift(phase: Float): EchoCardLean {
        val angle = phase * 2f * PI.toFloat()
        return EchoCardLean(x = sin(angle * 2f) * 1.2f, y = sin(angle) * 2.5f)
    }

    /** Idle lift at [phase], -1 to 1. */
    fun lift(phase: Float): Float = sin(phase * 2f * PI.toFloat() + PI.toFloat() / 2f)

    /** Where the light falls (0-1 across and down) for a lean: turning a side away moves it the other way. */
    fun light(lean: EchoCardLean): Offset = Offset(
        (0.5f - lean.y / (MaxLeanDegrees * 1.6f)).coerceIn(-0.1f, 1.1f),
        (0.4f + lean.x / (MaxLeanDegrees * 1.6f)).coerceIn(-0.1f, 1.1f),
    )
}

/** How a card arrives: the first one is dealt off the deck, later ones flip over where they lie. */
internal enum class EchoCardReveal { Deal, Flip }

/** A reveal frame: [turn] degrees from face up (180 is face down), [rise] below the rest place, [scale], and the light [sweep] (0-1) once it lands. */
internal data class EchoRevealFrame(val turn: Float, val rise: Float, val scale: Float, val sweep: Float?)

/**
 * The reveal: the card lifts off the deck face down, turns over with a little overshoot and lands,
 * and a flash of light runs across the foil. Under a second for the first card, quicker after.
 */
internal object EchoCardRevealTiming {
    private val Settled = EchoRevealFrame(0f, 0f, 1f, null)

    fun totalMillis(reveal: EchoCardReveal): Float = when (reveal) {
        EchoCardReveal.Deal -> 760f
        EchoCardReveal.Flip -> 560f
    }

    /** When the card lands face up; the haptic tick plays here. */
    fun landMillis(reveal: EchoCardReveal): Float = when (reveal) {
        EchoCardReveal.Deal -> 560f
        EchoCardReveal.Flip -> 380f
    }

    fun frame(reveal: EchoCardReveal?, millis: Float): EchoRevealFrame {
        if (reveal == null || millis >= totalMillis(reveal)) return Settled
        val deal = reveal == EchoCardReveal.Deal
        val riseEnd = if (deal) 320f else 260f
        val flipStart = if (deal) 140f else 0f
        val flipEnd = landMillis(reveal)
        val startRise = if (deal) 1f else 0.3f
        val rise = startRise * (1f - easeOut(millis / riseEnd))
        val flip = ((millis - flipStart) / (flipEnd - flipStart)).coerceIn(0f, 1f)
        val turn = 180f * (1f - backOut(flip))
        // A small swell at the half-turn, as if the card were lifted to be turned.
        val scale = (if (reveal == EchoCardReveal.Deal) 0.9f + 0.1f * easeOut(millis / riseEnd) else 0.97f + 0.03f * flip) + 0.05f * sin(flip * PI.toFloat())
        val sweepStart = flipEnd - 60f
        val sweep = if (millis >= sweepStart) ((millis - sweepStart) / (totalMillis(reveal) - sweepStart)).coerceIn(0f, 1f) else null
        return EchoRevealFrame(turn, rise, scale, sweep)
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }

    /** Overshoots by about 6% before settling, so the card lands rather than stops. */
    private fun backOut(t: Float): Float {
        val c = 1.2f
        val x = t.coerceIn(0f, 1f) - 1f
        return 1f + (c + 1f) * x * x * x + c * x * x
    }
}

/** Shared by the card's tilt and its foil, so the light always agrees with the angle. */
@Stable
internal class EchoCardMotion(
    private val leanX: Animatable<Float, AnimationVector1D>,
    private val leanY: Animatable<Float, AnimationVector1D>,
    private val drift: State<Float>,
    private val scope: CoroutineScope,
) {
    /** The finger, as shares of the card's width and height; null when nothing touches it. */
    var touch by mutableStateOf<Offset?>(null)
        private set

    val lean: EchoCardLean
        get() = EchoCardPhysics.drift(drift.value).let { EchoCardLean(leanX.value + it.x, leanY.value + it.y) }

    val lift: Float get() = EchoCardPhysics.lift(drift.value)

    /** Where the glare sits: under the finger, or where the lean puts the light. */
    val light: Offset get() = touch ?: EchoCardPhysics.light(lean)

    fun press(position: Offset, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        touch = Offset(position.x / width, position.y / height)
        val lean = EchoCardPhysics.lean(position.x, position.y, width, height)
        val follow = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)
        scope.launch { leanX.animateTo(lean.x, follow) }
        scope.launch { leanY.animateTo(lean.y, follow) }
    }

    fun release() {
        touch = null
        val settle = spring<Float>(dampingRatio = 0.42f, stiffness = 180f)
        scope.launch { leanX.animateTo(0f, settle) }
        scope.launch { leanY.animateTo(0f, settle) }
    }
}

@Composable
internal fun rememberEchoCardMotion(): EchoCardMotion {
    val drift = rememberEchoDriftPhase()
    val scope = rememberCoroutineScope()
    return remember { EchoCardMotion(Animatable(0f), Animatable(0f), drift, scope) }
}

private val EchoCardLift = 3.dp
private val EchoCardShadow = 20.dp

/** Idle drift phase; it stays at 0 with animations off and pauses while the app is in the background. */
@Composable
private fun rememberEchoDriftPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        if ((currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (last != 0L) phase.floatValue = (phase.floatValue + (now - last) / 1_000_000f / EchoCardPhysics.DriftMillis) % 1f
                    last = now
                }
            }
        }
    }
    return phase
}

private val EchoCardDealRise = 48.dp

/** Floats and leans under the finger; [shape] is the card's outline for its shadow and clip. */
@Composable
internal fun Modifier.echoCardTilt(motion: EchoCardMotion, shape: Shape, reveal: () -> EchoRevealFrame): Modifier {
    val density = LocalDensity.current.density
    return this
        .pointerInput(motion) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                motion.press(down.position, size.width.toFloat(), size.height.toFloat())
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }?.takeIf { it.pressed } ?: break
                    motion.press(change.position, size.width.toFloat(), size.height.toFloat())
                }
                motion.release()
            }
        }
        .graphicsLayer {
            val lean = motion.lean
            val arrival = reveal()
            rotationX = lean.x
            rotationY = lean.y + arrival.turn
            scaleX = arrival.scale
            scaleY = arrival.scale
            translationY = motion.lift * EchoCardLift.toPx() + arrival.rise * EchoCardDealRise.toPx()
            cameraDistance = 16f * density
            shadowElevation = EchoCardShadow.toPx()
            this.shape = shape
            clip = true
        }
}
