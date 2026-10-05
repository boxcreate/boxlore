package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoreMotionTest {
    private val blue = Color(0xFF3676DB)
    private val yellow = Color(0xFFE8BE40)

    private fun TestScope.motionScope(): CoroutineScope = CoroutineScope(
        coroutineContext + object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000)
            }
        }
    )

    @Test
    fun `control press and release stay subtle without overshooting resting size`() = runTest {
        val scale = Animatable(1f)
        val frames = mutableListOf<Float>()
        motionScope().launch {
            scale.animateTo(LoreControlPressedScale, LoreControlPressAnimation) { frames += value }
            scale.animateTo(1f, LoreControlPressAnimation) { frames += value }
        }
        advanceUntilIdle()
        assertTrue(frames.any { it < 1f })
        assertTrue(frames.all { it >= 0.96f && it <= 1f })
        assertEquals(1f, scale.value)
    }

    @Test
    fun `a press cancelled as a swipe starts returns without a bounce`() = runTest {
        val scale = Animatable(1f)
        val scope = motionScope()
        val pressing = scope.launch { scale.animateTo(LoreControlPressedScale, LoreControlPressAnimation) }
        advanceTimeBy(64)
        runCurrent()
        val interruptedScale = scale.value
        assertTrue(interruptedScale < 1f && interruptedScale > LoreControlPressedScale)
        pressing.cancel()
        val returning = mutableListOf<Float>()
        scope.launch { scale.animateTo(1f, LoreControlPressAnimation) { returning += value } }
        advanceUntilIdle()
        assertTrue(returning.all { it >= interruptedScale && it <= 1f })
        assertTrue(returning.zipWithNext().all { (previous, next) -> next >= previous })
        assertEquals(1f, scale.value)
    }

    @Test
    fun `both swipe directions blend toward the same next card and cancellation reverses`() {
        val offset = mutableStateOf(Offset.Zero)
        val transition = LoreAccentTransition(blue)
        transition.beginSwipe(yellow, offset, 88f)
        offset.value = Offset(-44f, 0f)
        assertEquals(lerp(blue, yellow, 0.5f), transition.color.value)
        offset.value = Offset(44f, 0f)
        assertEquals(lerp(blue, yellow, 0.5f), transition.color.value)
        offset.value = Offset.Zero
        assertEquals(blue, transition.color.value)
    }

    @Test
    fun `return interruption retains frozen palettes and promotion never rolls back`() = runTest {
        val offset = mutableStateOf(Offset.Zero)
        val transition = LoreAccentTransition(blue)
        transition.beginSwipe(yellow, offset, 88f)
        offset.value = Offset(30f, 0f)
        val displayed = transition.color.value
        transition.beginSwipe(Color.Red, offset, 88f)
        assertEquals(displayed, transition.color.value)
        offset.value = Offset(1500f, 0f)
        assertEquals(yellow, transition.color.value)
        motionScope().launch { transition.settle(yellow) }
        runCurrent()
        assertEquals(yellow, transition.color.value)
        advanceUntilIdle()
        assertEquals(yellow, transition.color.value)
    }

    @Test
    fun `late palettes and interrupted button changes start from the displayed color`() = runTest {
        val transition = LoreAccentTransition(blue)
        val scope = motionScope()
        val first = scope.launch { transition.settle(yellow) }
        advanceTimeBy(144)
        runCurrent()
        val displayed = transition.color.value
        assertTrue(displayed != blue && displayed != yellow)
        first.cancel()
        scope.launch { transition.settle(Color.Red) }
        runCurrent()
        assertEquals(displayed, transition.color.value)
        advanceUntilIdle()
        assertEquals(Color.Red, transition.color.value)
    }

    @Test
    fun `drag responds synchronously and swipe action occurs only once without endpoint reset`() = runTest {
        val actions = mutableListOf<SwipeDirection>()
        val state = SwipeableCardState(motionScope(), onSwiped = actions::add)
        repeat(4) { state.drag(Offset(-22f, 0f)) }
        assertEquals(-88f, state.offset.value.x)
        state.swipe(SwipeDirection.Left)
        state.swipe(SwipeDirection.Left)
        advanceUntilIdle()
        assertEquals(listOf(SwipeDirection.Left), actions)
        assertEquals(-1500f, state.offset.value.x)
    }

    @Test
    fun `cancelled and obsolete card motions cannot fire a swipe action`() = runTest {
        val actions = mutableListOf<SwipeDirection>()
        val scope = motionScope()
        val cancelled = SwipeableCardState(scope, onSwiped = actions::add)
        cancelled.drag(Offset(40f, 0f))
        cancelled.reset()
        advanceUntilIdle()
        assertEquals(0f, cancelled.offset.value.x)
        assertEquals(LoreSwipePhase.Idle, cancelled.phase)
        val obsolete = SwipeableCardState(scope, onSwiped = actions::add)
        obsolete.swipe(SwipeDirection.Right)
        obsolete.dispose()
        advanceUntilIdle()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `invalid motion is bounded and only floating player space is reclaimed`() {
        assertEquals(0f, loreSwipeProgress(Float.NaN, 88f))
        assertEquals(0f, loreSwipeProgress(30f, 0f))
        assertEquals(1f, loreSwipeProgress(-1500f, 88f))
        assertEquals(156.dp, lorePlayerClearance(156.dp, NavigationStyle.Floating, true, 0f))
        assertEquals(84.dp, lorePlayerClearance(156.dp, NavigationStyle.Floating, true, 1f))
        assertEquals(156.dp, lorePlayerClearance(156.dp, NavigationStyle.Classic, true, 1f))
        assertEquals(84.dp, lorePlayerClearance(84.dp, NavigationStyle.Floating, false, 1f))
    }
}
