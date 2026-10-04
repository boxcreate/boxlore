package cx.aswin.boxlore.ui.logic

import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptivePlayerScrollLogicTest {
    private val logic = AdaptivePlayerScrollLogic(48f)

    private fun scroll(y: Float, x: Float = 0f, userInput: Boolean = true, enabled: Boolean = true, busy: Boolean = false) =
        logic.onScroll(x, y, userInput, enabled, busy)

    @Test
    fun `downward browsing compacts after deliberate travel`() {
        assertFalse(scroll(-24f))
        assertFalse(scroll(-23f))
        assertTrue(scroll(-1f))
    }

    @Test
    fun `upward browsing restores after the same threshold`() {
        assertTrue(scroll(-48f))
        assertTrue(scroll(47f))
        assertFalse(scroll(1f))
    }

    @Test
    fun `direction reversal starts travel over rather than flickering`() {
        assertFalse(scroll(-40f))
        assertFalse(scroll(20f))
        assertFalse(scroll(-20f))
        assertFalse(scroll(-27f))
        assertTrue(scroll(-1f))
    }

    @Test
    fun `horizontal shelves and unconsumed boundary overscroll do not compact`() {
        assertFalse(scroll(-60f, x = 80f))
        assertFalse(scroll(0f))
        assertFalse(scroll(-47f))
        assertTrue(scroll(-1f))
    }

    @Test
    fun `fling or programmatic scrolling cannot change presentation`() {
        assertFalse(scroll(-100f, userInput = false))
        assertTrue(scroll(-48f))
        assertTrue(scroll(100f, userInput = false))
    }

    @Test
    fun `separate short gestures do not accumulate into a transition`() {
        assertFalse(scroll(-30f))
        logic.endGesture()
        assertFalse(scroll(-30f))
    }

    @Test
    fun `player interaction freezes presentation and clears partial travel`() {
        assertFalse(scroll(-40f))
        assertFalse(scroll(-100f, busy = true))
        assertFalse(scroll(-40f))
        assertTrue(scroll(-8f))
        assertTrue(scroll(100f, busy = true))
    }

    @Test
    fun `disabling adaptive mode restores normal chrome`() {
        assertTrue(scroll(-48f))
        assertFalse(scroll(-100f, enabled = false))
        assertFalse(scroll(-47f))
    }

    @Test
    fun `new episode reset discards the compact state and prior travel`() {
        assertTrue(scroll(-48f))
        scroll(30f)
        logic.reset()
        assertFalse(logic.isCompact)
        assertFalse(scroll(-47f))
    }

    @Test
    fun `invalid deltas cannot poison direction accumulation`() {
        assertFalse(scroll(Float.NaN))
        assertFalse(scroll(Float.NEGATIVE_INFINITY))
        assertFalse(scroll(-100f, x = Float.NaN))
        assertTrue(scroll(-48f))
    }

    @Test
    fun `threshold must be finite and positive`() {
        for (threshold in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { AdaptivePlayerScrollLogic(threshold) }
        }
    }

    @Test
    fun `header only pre-consumption counts as actual browsing`() {
        assertTrue(scroll(browsingConsumedDelta(-48f, 0f, 0f)))
    }

    @Test
    fun `mixed header and list movement includes only consumed travel`() {
        assertEquals(-40f, browsingConsumedDelta(-60f, -25f, -20f))
        assertEquals(-25f, browsingConsumedDelta(null, -25f, -20f))
        assertFalse(scroll(browsingConsumedDelta(-100f, 0f, -100f)))
        assertFalse(scroll(browsingConsumedDelta(100f, 0f, 100f)))
    }

    @Test
    fun `adaptive mode requires visible Floating chrome and a current episode`() {
        assertTrue(canUseAdaptivePlayer(NavigationStyle.Floating, true, true, false, 320f))
        assertFalse(canUseAdaptivePlayer(NavigationStyle.Classic, true, true, false, 400f))
        assertFalse(canUseAdaptivePlayer(NavigationStyle.Floating, false, true, false, 400f))
        assertFalse(canUseAdaptivePlayer(NavigationStyle.Floating, true, false, false, 400f))
        assertFalse(canUseAdaptivePlayer(NavigationStyle.Floating, true, true, true, 400f))
        assertFalse(canUseAdaptivePlayer(NavigationStyle.Floating, true, true, false, 319f))
    }
}
