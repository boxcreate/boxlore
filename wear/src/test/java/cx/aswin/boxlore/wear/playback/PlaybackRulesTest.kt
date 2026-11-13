package cx.aswin.boxlore.wear.playback

import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.model.WatchEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackRulesTest {
    private val minute = 60_000L
    private val episode =
        WatchEpisode(id = "a", showId = "1", title = "Blackshades", showTitle = "Darknet Diaries", artworkUrl = null, durationMinutes = 39)

    @Test
    fun `progress and time left come from the player's position`() {
        val snapshot = PlayerSnapshot(positionMs = 13 * minute, durationMs = 39 * minute, isPlaying = true, speed = 1.5f)

        val now = PlaybackRules.nowPlaying(episode, snapshot, SleepTimerChoice.Minutes30, upNextCount = 2)

        assertEquals(13f / 39f, now.progress, 0.0001f)
        assertEquals(26, now.minutesLeft)
        assertTrue(now.isPlaying)
        assertEquals(PlaybackPlace.Watch, now.place)
        assertEquals(1.5f, now.speed)
        assertEquals(SleepTimerChoice.Minutes30, now.sleepTimer)
        assertEquals(2, now.upNextCount)
    }

    @Test
    fun `the listed length stands in until the player knows the real one`() {
        val snapshot = PlayerSnapshot(positionMs = 0, durationMs = 0, isPlaying = false, speed = 1f)

        val now = PlaybackRules.nowPlaying(episode, snapshot, SleepTimerChoice.Off, upNextCount = 0)

        assertEquals(0f, now.progress)
        assertEquals(39, now.minutesLeft)
    }

    @Test
    fun `the last seconds still read one minute left, and the end reads none`() {
        val nearEnd = PlayerSnapshot(positionMs = 39 * minute - 2_000, durationMs = 39 * minute, isPlaying = true, speed = 1f)
        val pastEnd = PlayerSnapshot(positionMs = 45 * minute, durationMs = 39 * minute, isPlaying = false, speed = 1f)

        assertEquals(1, PlaybackRules.nowPlaying(episode, nearEnd, SleepTimerChoice.Off, 0).minutesLeft)
        assertEquals(0, PlaybackRules.nowPlaying(episode, pastEnd, SleepTimerChoice.Off, 0).minutesLeft)
        assertEquals(1f, PlaybackRules.nowPlaying(episode, pastEnd, SleepTimerChoice.Off, 0).progress)
    }

    @Test
    fun `an episode of unknown length shows no progress instead of failing`() {
        val unknown = episode.copy(durationMinutes = 0)
        val snapshot = PlayerSnapshot(positionMs = 5 * minute, durationMs = 0, isPlaying = true, speed = 1f)

        val now = PlaybackRules.nowPlaying(unknown, snapshot, SleepTimerChoice.Off, 0)

        assertEquals(0f, now.progress)
        assertEquals(0, now.minutesLeft)
    }

    @Test
    fun `the resting card is paused at the saved place`() {
        val now = PlaybackRules.resting(episode, positionMs = 13 * minute, upNextCount = 1)

        assertFalse(now.isPlaying)
        assertEquals(26, now.minutesLeft)
        assertEquals(SleepTimerChoice.Off, now.sleepTimer)
        assertEquals(1, now.upNextCount)
    }

    @Test
    fun `timed sleep choices run for their minutes, the others have no clock`() {
        assertEquals(15 * minute, PlaybackRules.sleepDelayMs(SleepTimerChoice.Minutes15))
        assertEquals(120 * minute, PlaybackRules.sleepDelayMs(SleepTimerChoice.Hours2))
        assertNull(PlaybackRules.sleepDelayMs(SleepTimerChoice.Off))
        assertNull(PlaybackRules.sleepDelayMs(SleepTimerChoice.EndOfEpisode))
    }

    @Test
    fun `only End of episode stops the queue from carrying on`() {
        assertFalse(PlaybackRules.continuesAfterEnd(SleepTimerChoice.EndOfEpisode))
        assertTrue(PlaybackRules.continuesAfterEnd(SleepTimerChoice.Off))
        assertTrue(PlaybackRules.continuesAfterEnd(SleepTimerChoice.Minutes30))
    }
}
