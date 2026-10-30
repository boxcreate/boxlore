package cx.aswin.boxlore.feature.history.story

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest

/**
 * One story card's clock: milliseconds since the card showed, held while [paused] or off screen, and
 * stopped at [stopAtMillis] once nothing moves any more, so a finished card draws no frames. With
 * animations turned off it still counts (cards still move on), but every entrance shows its end state.
 */
@Stable
internal class StoryClock(private val elapsedState: State<Long>, private val motionOffState: State<Boolean>) {
    val elapsed: Long get() = elapsedState.value
    val motionOff: Boolean get() = motionOffState.value

    /** An entrance's eased progress, 0 before [delay] to 1 after [delay] + [duration]; 1 with motion off. Read it in a draw phase. */
    fun at(delay: Int, duration: Int, easing: Easing): Float {
        if (motionOff) return 1f
        val t = ((elapsed - delay) / duration.toFloat()).coerceIn(0f, 1f)
        return easing.transform(t)
    }

    /** Linear progress of [duration] after [delay]. */
    fun linear(delay: Int, duration: Int): Float = ((elapsed - delay) / duration.toFloat()).coerceIn(0f, 1f)
}

@Composable
internal fun rememberStoryClock(key: Any, paused: Boolean, stopAtMillis: Long): StoryClock {
    val elapsed = remember(key) { mutableLongStateOf(0L) }
    val motionOff = remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key, paused, lifecycle) {
        val scale = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { scale?.scaleFactor ?: 1f }.collectLatest { factor -> motionOff.value = factor <= 0f }
    }
    LaunchedEffect(key, paused, lifecycle, stopAtMillis) {
        if (paused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var nanos = elapsed.longValue * NANOS_PER_MILLI
            var last = 0L
            while (elapsed.longValue < stopAtMillis) {
                withFrameNanos { now ->
                    // A long gap (the screen was away) never fast-forwards the card.
                    if (last != 0L) nanos += (now - last).coerceAtMost(MAX_FRAME_NANOS)
                    elapsed.longValue = (nanos / NANOS_PER_MILLI).toLong()
                    last = now
                }
            }
        }
    }
    return remember(key) { StoryClock(elapsed, motionOff) }
}

private const val MAX_FRAME_NANOS = 100_000_000L
private const val NANOS_PER_MILLI = 1_000_000.0

/** The motion board's (A14) curves and timings. */
internal object StoryMotion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val Drop = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
    val Hop = CubicBezierEasing(0.34f, 1.25f, 0.64f, 1f)

    /** Entrance delays d1–d6. */
    const val D1 = 80
    const val D2 = 160
    const val D3 = 260
    const val D4 = 400
    const val D5 = 600
    const val D6 = 1100

    const val RISE_MS = 550
    const val POP_MS = 500
    const val BLOB_MS = 700
    const val GROW_MS = 550
    const val DROP_MS = 700
    const val SPARK_DELAY_MS = 950
    const val SPARK_MS = 700
    const val COUNT_UP_MS = 1100
    const val HOP_MS = 700

    /** How long each card shows before the next. */
    const val CARD_MS = 8_000L

    /** Echo hops for this long before its card's act starts. */
    const val HOP_PHASE_MS = 1_000L

    /** Bars grow from 250ms, 22ms apart; record pills pop from 1.1s, 90ms apart. */
    fun barDelay(index: Int) = 250 + index * 22

    fun pillDelay(index: Int) = 1_100 + index * 90

    /** Everything on a card has arrived by now (the last pills, sparks and bubbles). */
    const val SETTLED_MS = 4_000L
}

/** Rises 28dp into place as it fades in. */
internal fun Modifier.storyRise(clock: StoryClock, delay: Int = 0): Modifier = graphicsLayer {
    val p = clock.at(delay, StoryMotion.RISE_MS, StoryMotion.Standard)
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * 28.dp.toPx()
}

/** Pops in from half size with a little overshoot. */
internal fun Modifier.storyPop(clock: StoryClock, delay: Int = 0, origin: TransformOrigin = TransformOrigin.Center): Modifier = graphicsLayer {
    val p = clock.at(delay, StoryMotion.POP_MS, StoryMotion.Pop)
    alpha = p.coerceIn(0f, 1f)
    val scale = 0.5f + 0.5f * p
    scaleX = scale
    scaleY = scale
    transformOrigin = origin
}

/** Grows in from 60% while turning 25°. */
internal fun Modifier.storyBlob(clock: StoryClock, delay: Int = 0): Modifier = graphicsLayer {
    val p = clock.at(delay, StoryMotion.BLOB_MS, StoryMotion.Standard)
    alpha = p.coerceIn(0f, 1f)
    val scale = 0.6f + 0.4f * p
    scaleX = scale
    scaleY = scale
    rotationZ = -25f * (1f - p)
}

/** Grows up from its bottom edge. */
internal fun Modifier.storyGrow(clock: StoryClock, delay: Int): Modifier = graphicsLayer {
    scaleY = clock.at(delay, StoryMotion.GROW_MS, StoryMotion.Standard).coerceAtLeast(0f)
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/** Drops in from 60dp above, swinging from -18° to its resting -6°. */
internal fun Modifier.storyDrop(clock: StoryClock, delay: Int): Modifier = graphicsLayer {
    val p = clock.at(delay, StoryMotion.DROP_MS, StoryMotion.Drop)
    alpha = p.coerceIn(0f, 1f)
    translationY = -60.dp.toPx() * (1f - p)
    rotationZ = -18f + 12f * p
}

/** The four sparks that fly out of a spot (each card has one), 7dp squares in [color]; none with motion off. */
@Composable
internal fun StorySpark(x: Float, y: Float, color: Color, offsets: List<Offset>, clock: StoryClock) {
    if (clock.motionOff) return
    Canvas(Modifier.offset(x.dp, y.dp).size(1.dp)) {
        val t = clock.linear(StoryMotion.SPARK_DELAY_MS, StoryMotion.SPARK_MS)
        if (t <= 0f || t >= 1f) return@Canvas
        val alpha = if (t < 0.3f) StoryMotion.Standard.transform(t / 0.3f) else 1f - StoryMotion.Standard.transform((t - 0.3f) / 0.7f)
        val e = StoryMotion.Standard.transform(t)
        val side = 7.dp.toPx()
        offsets.forEach { offset ->
            val at = Offset(offset.x.dp.toPx() * e, offset.y.dp.toPx() * e)
            scale(0.3f + 0.7f * e, pivot = at + Offset(side / 2, side / 2)) {
                drawRoundRect(color.copy(alpha = alpha.coerceIn(0f, 1f)), at, Size(side, side), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

/** The boards' spark spreads. */
internal object StorySparks {
    val Hello = listOf(Offset(-40f, -30f), Offset(34f, -38f), Offset(48f, 4f), Offset(-46f, 10f))
    val Time = listOf(Offset(-40f, -30f), Offset(30f, -40f), Offset(44f, 6f), Offset(-48f, 12f))
    val Wide = listOf(Offset(-40f, -30f), Offset(34f, -38f), Offset(46f, 6f), Offset(-48f, 12f))
    val Type = listOf(Offset(-40f, -30f), Offset(34f, -38f), Offset(46f, 6f), Offset(-40f, 12f))
}
