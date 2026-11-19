package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeTitleRowLogicTest {
    private val now = 1_700_000_000L

    @Test
    fun `details give season and episode, age and length`() {
        val episode = TestFixtures.episode(duration = 3_900, publishedDate = now - 2 * 86_400L).copy(seasonNumber = 2, episodeNumber = 5)
        assertEquals(listOf("S2 E5", "2d ago", "1h 5m"), episodeTitleRowDetailParts(episode, timeLeft = null, nowSeconds = now))
        assertEquals("S2 E5 · 2d ago · 1h 5m", episodeDetailsLine(episodeTitleRowDetailParts(episode, null, now)).text)
    }

    @Test
    fun `time left replaces the length while the episode is underway`() {
        val episode = TestFixtures.episode(duration = 2_700, publishedDate = now - 3_600L)
        assertEquals(listOf("", "1h ago", "12m left"), episodeTitleRowDetailParts(episode, timeLeft = "12m left", nowSeconds = now))
        assertEquals(listOf("", "1h ago", "45m"), episodeTitleRowDetailParts(episode, timeLeft = " ", nowSeconds = now))
    }

    @Test
    fun `unknown dates and lengths drop out of the line`() {
        val episode = TestFixtures.episode(duration = 0, publishedDate = 0L).copy(episodeNumber = 12)
        assertEquals("E12", episodeDetailsLine(episodeTitleRowDetailParts(episode, null, now)).text)
        assertEquals("", formatEpisodeListDuration(-1))
        assertEquals("59m", formatEpisodeListDuration(3_599))
        assertEquals("1h 0m", formatEpisodeListDuration(3_600))
    }
}
