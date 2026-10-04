package cx.aswin.boxlore.feature.player.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlinx.coroutines.isActive

internal val CompactPlayerRotationPeriod = (2.0 * PI / 9.0).toFloat()
private const val LobeCycleSeconds = 2.4
private const val FrameIntervalNanos = 33_333_333L

internal fun canAnimateCompactPlayer(isPlaying: Boolean, isLoading: Boolean, cookieFraction: Float, expansionFraction: Float): Boolean =
    isPlaying && !isLoading && cookieFraction >= 0.999f && expansionFraction <= 0f

internal fun advanceCompactPlayerRotation(rotation: Float, elapsedNanos: Long): Float {
    val initial = rotation.takeIf { it.isFinite() } ?: 0f
    val advance = elapsedNanos.coerceAtLeast(0L).toDouble() / 1_000_000_000.0 * CompactPlayerRotationPeriod / LobeCycleSeconds
    return ((initial.toDouble() + advance) % CompactPlayerRotationPeriod).toFloat()
}

/** One shared, lifecycle-aware edge phase; consumers read it in drawing/layer blocks. */
@Composable
internal fun rememberCompactPlayerRotation(episodeId: String, running: Boolean): State<Float> {
    val phase = remember(episodeId) { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(phase, running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastUpdate = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (lastUpdate == 0L) {
                        lastUpdate = now
                    } else if (now - lastUpdate >= FrameIntervalNanos) {
                        phase.floatValue = advanceCompactPlayerRotation(phase.floatValue, (now - lastUpdate).coerceAtMost(100_000_000L))
                        lastUpdate = now
                    }
                }
            }
        }
    }
    return phase
}
