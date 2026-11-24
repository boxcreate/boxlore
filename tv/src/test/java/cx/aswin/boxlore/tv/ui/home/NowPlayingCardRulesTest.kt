package cx.aswin.boxlore.tv.ui.home

import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.TvEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NowPlayingCardRulesTest {
    private val episode = TvEpisode("1", "9", "Episode", "Show", null, durationMinutes = 60)
    private fun now(isPlaying: Boolean, loaded: Boolean = true, id: String = "1") =
        NowPlaying(episode.copy(id = id), isPlaying, progress = 0.5f, elapsedSeconds = 1800, minutesLeft = 30, loaded = loaded)

    @Test
    fun `the card is the remote only for the episode in the player`() {
        assertEquals(CardMode.Playing, NowPlayingCardRules.mode("1", now(isPlaying = true)))
        assertEquals(CardMode.Paused, NowPlayingCardRules.mode("1", now(isPlaying = false)))
    }

    @Test
    fun `the last episode at rest stays a Resume card`() {
        assertEquals(CardMode.Resting, NowPlayingCardRules.mode("1", null))
        assertEquals(CardMode.Resting, NowPlayingCardRules.mode("1", now(isPlaying = false, loaded = false)))
        assertEquals(CardMode.Resting, NowPlayingCardRules.mode("1", now(isPlaying = true, id = "2")))
    }
}
