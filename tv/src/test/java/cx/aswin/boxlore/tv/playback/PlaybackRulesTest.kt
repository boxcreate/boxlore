package cx.aswin.boxlore.tv.playback

import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackRulesTest {
    private val episode = TvEpisode("1", "9", "Episode", "Show", null, durationMinutes = 60)

    @Test
    fun `the player shows time played, time left rounded up and progress`() {
        val now = PlaybackRules.nowPlaying(episode, PlayerSnapshot(positionMs = 90_500, durationMs = 600_000, isPlaying = true, speed = 1.5f), SleepTimerChoice.Off)
        assertEquals(90, now.elapsedSeconds)
        assertEquals(9, now.minutesLeft)
        assertEquals(0.15f, now.progress, 0.001f)
        assertEquals(1.5f, now.speed)
        // Until the player knows the length, the episode's own length stands in.
        assertEquals(60, PlaybackRules.resting(episode, positionMs = 0).minutesLeft)
        // The resting card is not in the player, so Home keeps it a Resume card.
        assertEquals(false, PlaybackRules.resting(episode, positionMs = 0).loaded)
    }

    @Test
    fun `sleep timer choices end playback as on the phone`() {
        assertEquals(15 * 60_000L, PlaybackRules.sleepDelayMs(SleepTimerChoice.Minutes15))
        assertNull(PlaybackRules.sleepDelayMs(SleepTimerChoice.EndOfEpisode))
        assertFalse(PlaybackRules.continuesAfterEnd(SleepTimerChoice.EndOfEpisode))
        assertTrue(PlaybackRules.continuesAfterEnd(SleepTimerChoice.Off))
    }

    @Test
    fun `a still player keeps the place it saved, and a seek while still is saved at once`() {
        assertFalse(PlaybackRules.savesPlaceBeforeStart(loadedId = "1", startingId = "1", isPlaying = false))
        assertTrue(PlaybackRules.savesPlaceBeforeStart(loadedId = "1", startingId = "2", isPlaying = false))
        assertTrue(PlaybackRules.savesPlaceOnJump(isSeek = true, isPlaying = false))
        assertFalse(PlaybackRules.savesPlaceOnJump(isSeek = true, isPlaying = true))
    }
}
