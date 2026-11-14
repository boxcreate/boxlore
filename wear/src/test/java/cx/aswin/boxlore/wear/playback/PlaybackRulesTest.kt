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

    // Regression: taking an episode back from the phone restarted it where the watch had stopped, not where the phone was.
    @Test
    fun `a still player does not save its old place when the same episode is started again`() {
        assertFalse(PlaybackRules.savesPlaceBeforeStart(loadedId = "a", startingId = "a", isPlaying = false))
        assertTrue(PlaybackRules.savesPlaceBeforeStart(loadedId = "a", startingId = "a", isPlaying = true))
        assertTrue(PlaybackRules.savesPlaceBeforeStart(loadedId = "a", startingId = "b", isPlaying = false))
        assertTrue(PlaybackRules.savesPlaceBeforeStart(loadedId = null, startingId = "a", isPlaying = false))
    }

    // Regression: pause, seek, start the same episode again. The still player skips saving at the start, so the seek must be saved when made.
    @Test
    fun `a seek on a still player is saved at once, any other jump or a playing player is not`() {
        assertTrue(PlaybackRules.savesPlaceOnJump(isSeek = true, isPlaying = false))
        assertFalse(PlaybackRules.savesPlaceOnJump(isSeek = true, isPlaying = true))
        // A new episode being loaded also moves the position; that is not the listener's place in it.
        assertFalse(PlaybackRules.savesPlaceOnJump(isSeek = false, isPlaying = false))
    }

    /**
     * The player and the saved place as the saving rules move them: the player saves when it stops,
     * on a jump and before a start only where the rules say so, and a start reads the saved place.
     */
    private class Places(var player: Long, var saved: Long, var isPlaying: Boolean = true) {
        fun pause() {
            isPlaying = false
            saved = player
        }

        fun seek(to: Long) {
            player = to
            if (PlaybackRules.savesPlaceOnJump(isSeek = true, isPlaying = isPlaying)) saved = player
        }

        /** Starts the loaded episode again and returns where it starts. */
        fun startSame(): Long {
            if (PlaybackRules.savesPlaceBeforeStart(loadedId = "a", startingId = "a", isPlaying = isPlaying)) saved = player
            player = saved
            isPlaying = true
            return player
        }
    }

    // Regression: pause, seek, then start the same episode again must keep the seek.
    @Test
    fun `pause, seek, start the same episode again starts at the seeked place`() {
        val places = Places(player = 10 * minute, saved = 9 * minute)

        places.pause()
        places.seek(to = 12 * minute)

        assertEquals(12 * minute, places.startSame())
    }

    // Regression: the phone played the paused episode on; the watch must start there, not where it had stopped.
    @Test
    fun `a place the phone moved on while the watch stood still is where the same episode starts`() {
        val places = Places(player = 10 * minute, saved = 9 * minute)

        places.pause()
        places.saved = 14 * minute

        assertEquals(14 * minute, places.startSame())
    }

    @Test
    fun `starting the episode that is playing keeps the player's own place`() {
        val places = Places(player = 10 * minute, saved = 9 * minute)

        assertEquals(10 * minute, places.startSame())
    }
}
