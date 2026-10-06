package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SwipeDirection { Left, Right }

internal enum class LoreSwipePhase { Idle, Dragging, Returning, Exiting }

class SwipeableCardState(
    private val coroutineScope: CoroutineScope,
    private val onDragStarted: (SwipeableCardState) -> Unit = {},
    private val onSwiped: (SwipeDirection) -> Unit,
) {
    private val animatedX = Animatable(0f)
    private var dragX by mutableFloatStateOf(0f)
    private var motion: Job? = null
    private var disposed = false
    internal var phase by mutableStateOf(LoreSwipePhase.Idle)
        private set
    val offset: State<Offset> = derivedStateOf {
        Offset(if (phase == LoreSwipePhase.Dragging) dragX else animatedX.value, 0f)
    }

    fun beginDrag() {
        if (disposed || phase == LoreSwipePhase.Exiting) return
        dragX = offset.value.x
        motion?.cancel()
        phase = LoreSwipePhase.Dragging
        onDragStarted(this)
    }

    fun drag(dragAmount: Offset) {
        if (disposed || phase == LoreSwipePhase.Exiting || !dragAmount.x.isFinite()) return
        if (phase != LoreSwipePhase.Dragging) beginDrag()
        // Pointer movement is synchronous; no queued coroutine for every drag event.
        dragX += dragAmount.x
    }

    fun swipe(direction: SwipeDirection) {
        if (disposed || phase == LoreSwipePhase.Exiting) return
        val from = offset.value.x
        motion?.cancel()
        motion = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatedX.snapTo(from)
            phase = LoreSwipePhase.Exiting
            animatedX.animateTo(
                if (direction == SwipeDirection.Left) -1500f else 1500f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
            if (!disposed) onSwiped(direction)
            // Keep the outgoing endpoint until the deck replaces this state.
        }
    }

    fun reset() {
        if (disposed || phase == LoreSwipePhase.Exiting) return
        val from = offset.value.x
        motion?.cancel()
        motion = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatedX.snapTo(from)
            phase = LoreSwipePhase.Returning
            animatedX.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
            phase = LoreSwipePhase.Idle
        }
    }

    internal fun dispose() {
        disposed = true
        motion?.cancel()
    }
}

@Composable
fun rememberSwipeableCardState(
    key: Any?,
    onDragStarted: (SwipeableCardState) -> Unit = {},
    onSwiped: (SwipeDirection) -> Unit,
): SwipeableCardState {
    val scope = rememberCoroutineScope()
    val latestDrag = rememberUpdatedState(onDragStarted)
    val latestSwipe = rememberUpdatedState(onSwiped)
    val state = remember(key) { SwipeableCardState(scope, { latestDrag.value(it) }, { latestSwipe.value(it) }) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}
