package cx.aswin.boxlore.wear.ui.show

import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.ui.components.PlayingEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeButtonTest {
    private val fresh = WatchEpisode(id = "e", showId = "1", title = "E", showTitle = "S", artworkUrl = null, durationMinutes = 40)
    private val started = fresh.copy(minutesLeft = 25)

    @Test
    fun `an episode never started offers Play, a started one Resume`() {
        assertEquals(EpisodeButton.Play, EpisodeButton.of(fresh, playing = null))
        assertEquals(EpisodeButton.Resume, EpisodeButton.of(started, playing = null))
    }

    @Test
    fun `the episode that is playing right now offers Pause, not Resume`() {
        assertEquals(EpisodeButton.Pause, EpisodeButton.of(started, PlayingEpisode("e", isPlaying = true)))
    }

    @Test
    fun `a paused player, or another episode playing, changes nothing`() {
        assertEquals(EpisodeButton.Resume, EpisodeButton.of(started, PlayingEpisode("e", isPlaying = false)))
        assertEquals(EpisodeButton.Resume, EpisodeButton.of(started, PlayingEpisode("other", isPlaying = true)))
        assertEquals(EpisodeButton.Play, EpisodeButton.of(fresh, PlayingEpisode("other", isPlaying = true)))
    }
}
