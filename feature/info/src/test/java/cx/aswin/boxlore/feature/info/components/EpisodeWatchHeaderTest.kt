package cx.aswin.boxlore.feature.info.components

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.WatchEpisodeCopy
import cx.aswin.boxlore.feature.info.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeWatchHeaderTest {
    @Test
    fun `the title leaves room for the watch button only when it is there`() {
        assertEquals(0.dp, EpisodeWatchHeader.titleEndExtra(hasWatchButton = false))
        assertEquals(48.dp, EpisodeWatchHeader.titleEndExtra(hasWatchButton = true))
    }

    @Test
    fun `sending is one tap, removing or stopping a download is asked first`() {
        assertFalse(EpisodeWatchHeader.asksFirst(WatchEpisodeCopy.None))
        assertTrue(EpisodeWatchHeader.asksFirst(WatchEpisodeCopy.OnWatch))
        assertTrue(EpisodeWatchHeader.asksFirst(WatchEpisodeCopy.Downloading))
    }

    @Test
    fun `the button names what a tap will do`() {
        assertEquals(R.string.episode_info_send_to_watch, EpisodeWatchHeader.label(WatchEpisodeCopy.None))
        assertEquals(R.string.episode_info_downloading_on_watch, EpisodeWatchHeader.label(WatchEpisodeCopy.Downloading))
        assertEquals(R.string.episode_info_on_watch, EpisodeWatchHeader.label(WatchEpisodeCopy.OnWatch))
    }

    @Test
    fun `the page says what happened, or that the watch could not be told`() {
        assertEquals(R.string.episode_info_watch_sending, EpisodeWatchHeader.messageAfter(WatchEpisodeCopy.None, handedOver = true))
        assertEquals(R.string.episode_info_watch_removed, EpisodeWatchHeader.messageAfter(WatchEpisodeCopy.OnWatch, handedOver = true))
        assertEquals(R.string.episode_info_watch_removed, EpisodeWatchHeader.messageAfter(WatchEpisodeCopy.Downloading, handedOver = true))
        assertEquals(R.string.episode_info_watch_out_of_reach, EpisodeWatchHeader.messageAfter(WatchEpisodeCopy.None, handedOver = false))
    }
}
