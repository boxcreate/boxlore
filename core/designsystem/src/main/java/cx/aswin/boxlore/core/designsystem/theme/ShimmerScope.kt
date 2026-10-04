package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalShimmerPhase = staticCompositionLocalOf<State<Float>?> { null }

/** Loading blocks share a drawing-only clock; the window lifecycle pauses its frames. */
@Composable
fun ShimmerScope(active: Boolean, content: @Composable () -> Unit) {
    // A NavBackStackEntry can remain CREATED while its visible enter transition
    // is drawing. Do not gate visible skeletons on that entry's ON_START event.
    val phase = if (active) rememberShimmerPhase() else remember { mutableStateOf(0f) }
    // Cached draw modifiers must keep the same State reference when lifecycle/loading
    // switches between the stopped value and a newly created animation clock.
    val phaseSource = remember { mutableStateOf<State<Float>>(phase) }
    val sharedPhase = remember {
        object : State<Float> {
            override val value: Float get() = phaseSource.value.value
        }
    }
    SideEffect { phaseSource.value = phase }
    CompositionLocalProvider(LocalShimmerPhase provides sharedPhase, content = content)
}

@Composable
internal fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
        label = "ShimmerProgress",
    )
}
