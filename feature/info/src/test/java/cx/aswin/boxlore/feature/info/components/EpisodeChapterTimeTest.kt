package cx.aswin.boxlore.feature.info.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeChapterTimeTest {
    @Test
    fun `chapter timestamp rolls minutes into hours and ignores fractional seconds`() {
        assertEquals("00:00", chapterTime(0.0))
        assertEquals("59:59", chapterTime(3599.9))
        assertEquals("1:00:00", chapterTime(3600.0))
        assertEquals("2:01:05", chapterTime(7265.0))
    }
}
