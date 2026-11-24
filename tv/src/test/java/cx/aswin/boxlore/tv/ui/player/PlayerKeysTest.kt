package cx.aswin.boxlore.tv.ui.player

import androidx.compose.ui.input.key.Key
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlayerKeysTest {
    private class Recorder(playing: Boolean) : TvPlayback {
        val calls = mutableListOf<String>()
        private val episode = TvEpisode("1", "9", "Episode", "Show", null, durationMinutes = 60)
        override val nowPlaying = MutableStateFlow<NowPlaying?>(NowPlaying(episode, playing, progress = 0.5f, elapsedSeconds = 1800, minutesLeft = 30))

        override fun play(episodeId: String) {
            calls += "play $episodeId"
        }

        override fun playDailyMix() {
            calls += "mix"
        }

        override fun togglePlayPause() {
            calls += "toggle"
        }

        override fun seekBack() {
            calls += "back"
        }

        override fun seekForward() {
            calls += "forward"
        }

        override fun setSpeed(speed: Float) = Unit

        override fun setSleepTimer(choice: SleepTimerChoice) = Unit
    }

    @Test
    fun `Play and Pause keys only do what they say, Play-Pause toggles`() {
        val playing = Recorder(playing = true)
        handleMediaKey(Key.MediaPlay, playing)
        handleMediaKey(Key.MediaPause, playing)
        handleMediaKey(Key.MediaPlayPause, playing)
        assertEquals(listOf("toggle", "toggle"), playing.calls)

        val paused = Recorder(playing = false)
        handleMediaKey(Key.MediaPause, paused)
        handleMediaKey(Key.MediaPlay, paused)
        assertEquals(listOf("toggle"), paused.calls)
    }

    @Test
    fun `rewind and fast forward skip, and other keys are left alone`() {
        val player = Recorder(playing = true)
        handleMediaKey(Key.MediaRewind, player)
        handleMediaKey(Key.MediaFastForward, player)
        assertFalse(handleMediaKey(Key.DirectionUp, player))
        assertEquals(listOf("back", "forward"), player.calls)
    }
}
