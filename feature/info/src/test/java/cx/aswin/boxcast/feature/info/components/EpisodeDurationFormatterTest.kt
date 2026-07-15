package cx.aswin.boxcast.feature.info.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeDurationFormatterTest {
    @Test
    fun `zero duration is hidden`() {
        assertEquals("", formatEpisodeDuration(0))
    }

    @Test
    fun `positive sub-minute duration is hidden`() {
        assertEquals("", formatEpisodeDuration(59))
    }

    @Test
    fun `one minute duration is displayed`() {
        assertEquals("1m", formatEpisodeDuration(60))
    }

    @Test
    fun `hour duration includes remaining minutes`() {
        assertEquals("1h 1m", formatEpisodeDuration(3_660))
    }
}
