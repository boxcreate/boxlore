package cx.aswin.boxlore.feature.player.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MiniPlayerSplitTest {
    @Test
    fun `time left rounds up to whole minutes and shows hours past an hour`() {
        assertEquals("23m left", miniPlayerTimeLeft(positionMs = 7 * 60_000L, durationMs = 30 * 60_000L))
        assertEquals("1m left", miniPlayerTimeLeft(positionMs = 29 * 60_000L + 59_000L, durationMs = 30 * 60_000L))
        assertEquals("1h 5m left", miniPlayerTimeLeft(positionMs = 0L, durationMs = 65 * 60_000L))
        assertEquals("30m left", miniPlayerTimeLeft(positionMs = -5L, durationMs = 30 * 60_000L))
        assertNull(miniPlayerTimeLeft(positionMs = 0L, durationMs = 0L))
    }

    @Test
    fun `the split merges into one surface early in the shrink to the cookie`() {
        assertEquals(1f, miniSplitAmount(0f))
        assertEquals(0f, miniSplitAmount(0.35f))
        assertEquals(0f, miniSplitAmount(1f))
        assertEquals(0.5f, miniSplitAmount(0.175f), 0.001f)
    }

    @Test
    fun `the sheet backing is clear behind the resting bar and filled as soon as the sheet opens`() {
        // Regression: the backing waited for the mini player to fade, leaving the growing sheet see-through.
        assertEquals(0f, miniPlayerBackingFill(0f))
        assertEquals(1f, miniPlayerBackingFill(0.02f))
        assertEquals(1f, miniPlayerBackingFill(0.5f))
        assertEquals(0.5f, miniPlayerBackingFill(0.01f), 0.0001f)
        assertEquals(0f, miniPlayerBackingFill(Float.NaN))
    }

    @Test
    fun `the sheet's shadow moves as far as the swiped bar`() {
        // Regression: the still sheet cast the shadow, so swiping the bar to Dismiss left the shadow behind.
        // Casting it from the bar instead clipped it to a flat dark patch in the split's gap.
        assertEquals(-120f, miniPlayerShadowOffset(swipeOffsetPx = -120f, compactFraction = 0f))
        assertEquals(-60f, miniPlayerShadowOffset(swipeOffsetPx = -120f, compactFraction = 0.5f))
        assertEquals(0f, miniPlayerShadowOffset(swipeOffsetPx = 80f, compactFraction = 1f))
    }

    @Test
    fun `the shadow covers only the part of the swiped bar still inside the sheet`() {
        // Regression: past the sheet's edge the bar is cut off, but its shadow ran on as a grey block.
        assertEquals(MiniPlayerShadowSpan(start = 0, width = 1000), miniPlayerShadowSpan(0f, 1000))
        assertEquals(MiniPlayerShadowSpan(start = 300, width = 700), miniPlayerShadowSpan(300f, 1000))
        assertEquals(MiniPlayerShadowSpan(start = 0, width = 700), miniPlayerShadowSpan(-300f, 1000))
        assertEquals(MiniPlayerShadowSpan(start = 1000, width = 0), miniPlayerShadowSpan(5000f, 1000))
    }
}
