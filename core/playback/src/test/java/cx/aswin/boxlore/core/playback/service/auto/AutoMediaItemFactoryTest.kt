package cx.aswin.boxlore.core.playback.service.auto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The words on a car row's second line. */
class AutoMediaItemFactoryTest {
    private val minute = 60_000L

    @Test
    fun `time left is in minutes up to an hour, then hours and minutes`() {
        assertEquals("35 min left", AutoMediaItemFactory.formatTimeLeft(progressMs = 25 * minute, durationMs = 60 * minute))
        assertEquals("60 min left", AutoMediaItemFactory.formatTimeLeft(progressMs = 0L, durationMs = 60 * minute))
        assertEquals("1h 1m left", AutoMediaItemFactory.formatTimeLeft(progressMs = 0L, durationMs = 61 * minute))
        assertEquals("2h 0m left", AutoMediaItemFactory.formatTimeLeft(progressMs = 30 * minute, durationMs = 150 * minute))
    }

    @Test
    fun `under a minute left, or progress past the end, reads Almost done`() {
        assertEquals("Almost done", AutoMediaItemFactory.formatTimeLeft(progressMs = 59 * minute + 30_000L, durationMs = 60 * minute))
        assertEquals("Almost done", AutoMediaItemFactory.formatTimeLeft(progressMs = 70 * minute, durationMs = 60 * minute))
    }

    @Test
    fun `a subtitle joins the parts it has and skips blank ones`() {
        assertEquals("Show · Today · 28 min", AutoMediaItemFactory.joinSubtitle("Show", "Today", "28 min"))
        assertEquals("Show · 28 min", AutoMediaItemFactory.joinSubtitle("Show", null, "28 min"))
        assertEquals("28 min", AutoMediaItemFactory.joinSubtitle("", "  ", "28 min"))
        assertEquals("", AutoMediaItemFactory.joinSubtitle(null, ""))
    }
}
