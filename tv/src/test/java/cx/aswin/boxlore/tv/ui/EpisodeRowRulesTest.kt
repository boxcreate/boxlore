package cx.aswin.boxlore.tv.ui

import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.components.EpisodeRowRules
import cx.aswin.boxlore.tv.ui.components.RowProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeRowRulesTest {
    private val episode = TvEpisode("1", "9", "Episode", "Show", null, durationMinutes = 60)

    @Test
    fun `a row shows nothing extra before the episode starts`() {
        assertEquals(RowProgress.Unstarted, EpisodeRowRules.progress(episode))
    }

    @Test
    fun `a started episode shows its share and the time left`() {
        assertEquals(RowProgress.Started(0.75f, 15), EpisodeRowRules.progress(episode.copy(minutesLeft = 15)))
    }

    @Test
    fun `a played episode reads Played even with a saved position`() {
        assertEquals(RowProgress.Played, EpisodeRowRules.progress(episode.copy(minutesLeft = 15, played = true)))
    }
}
