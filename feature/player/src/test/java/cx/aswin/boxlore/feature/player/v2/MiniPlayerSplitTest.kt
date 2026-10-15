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
}
