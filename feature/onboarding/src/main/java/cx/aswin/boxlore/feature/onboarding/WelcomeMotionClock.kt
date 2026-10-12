package cx.aswin.boxlore.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

internal class WelcomeMotionClock(
    val elapsedMillis: State<Long>,
    val revealComplete: State<Boolean>,
    val animationsDisabled: State<Boolean>,
)

/** Pure timing rules shared by welcome artwork and copy; callers supply a positive scale. */
internal object WelcomeFrameTiming {
    private const val MaxFrameDeltaNanos = 100_000_000L
    private const val NanosPerMillisecond = 1_000_000.0

    /** Caps long gaps before scaling so returning to the screen never fast-forwards motion. */
    fun advanceElapsedNanos(elapsedNanos: Double, frameDeltaNanos: Long, scale: Float): Double =
        elapsedNanos + frameDeltaNanos.coerceIn(0L, MaxFrameDeltaNanos) / scale.toDouble()

    /** Publishes whole milliseconds while the clock retains fractional nanoseconds. */
    fun elapsedMillis(elapsedNanos: Double): Long = (elapsedNanos / NanosPerMillisecond).toLong()
}

/** A single clock for the reel and text entry, paused while this route is not visible. */
@Composable
internal fun rememberWelcomeMotionClock(): WelcomeMotionClock {
    val elapsed = remember { mutableLongStateOf(0L) }
    val revealComplete = remember { mutableStateOf(false) }
    val animationsDisabled = remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        val durationScale = currentCoroutineContext()[MotionDurationScale]
        var elapsedNanos = elapsed.longValue * 1_000_000.0
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
                animationsDisabled.value = scale <= 0f
                revealComplete.value = WelcomeReelMotion.isRevealComplete(elapsed.longValue, scale <= 0f, revealComplete.value)
                if (scale <= 0f) return@collectLatest
                var lastFrame = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        if (lastFrame != 0L) {
                            // Resuming does not fast-forward by the time spent in the background.
                            elapsedNanos = WelcomeFrameTiming.advanceElapsedNanos(elapsedNanos, now - lastFrame, scale)
                            elapsed.longValue = WelcomeFrameTiming.elapsedMillis(elapsedNanos)
                            if (!revealComplete.value) {
                                revealComplete.value = WelcomeReelMotion.isRevealComplete(elapsed.longValue, animationsDisabled = false, alreadyComplete = false)
                            }
                        }
                        lastFrame = now
                    }
                }
            }
        }
    }
    return remember { WelcomeMotionClock(elapsed, revealComplete, animationsDisabled) }
}
