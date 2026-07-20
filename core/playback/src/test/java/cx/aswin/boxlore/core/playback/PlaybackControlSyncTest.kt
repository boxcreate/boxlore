package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackControlSyncTest {
    private val sampleEpisode =
        Episode(
            id = "ep-1",
            title = "Sample",
            description = "",
            audioUrl = "https://example.com/a.mp3",
            duration = 60,
            publishedDate = 0L,
        )

    @Test
    fun resolvePlaybackSpeedPrefersController() {
        assertEquals(
            2.0f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = 2.0f,
                stateSpeed = 1.0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolvePlaybackSpeedFallsBackToStateWhenControllerMissing() {
        assertEquals(
            1.75f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = null,
                stateSpeed = 1.75f,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolvePlaybackSpeedIgnoresNonPositiveControllerSpeed() {
        assertEquals(
            1.5f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = 0f,
                stateSpeed = 1.5f,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolvePlaybackSpeedIgnoresNaNControllerSpeed() {
        assertEquals(
            1.25f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = Float.NaN,
                stateSpeed = 1.25f,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolvePlaybackSpeedFallsBackWhenStateSpeedInvalid() {
        assertEquals(
            1.0f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = null,
                stateSpeed = Float.NaN,
            ),
            0.0001f,
        )
        assertEquals(
            1.0f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = -1f,
                stateSpeed = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun resolvePlaybackSpeedAllowsPersistedOneX() {
        assertEquals(
            1.0f,
            PlaybackControlSync.resolvePlaybackSpeed(
                controllerSpeed = 1.0f,
                stateSpeed = 2.0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun clearedStatePreservesSpeedAndSeekControlsAndClearsPlayback() {
        val previous =
            PlayerState(
                isPlaying = true,
                playbackSpeed = 1.0f,
                seekBackwardMs = 15_000L,
                seekForwardMs = 45_000L,
                currentEpisode = sampleEpisode,
                sleepTimerEnd = 99L,
                sleepAtEndOfEpisode = true,
            )

        val cleared =
            PlaybackControlSync.clearedStatePreservingControls(
                previous = previous,
                controllerSpeed = 2.0f,
            )

        assertEquals(2.0f, cleared.playbackSpeed, 0.0001f)
        assertEquals(15_000L, cleared.seekBackwardMs)
        assertEquals(45_000L, cleared.seekForwardMs)
        assertNull(cleared.currentEpisode)
        assertFalse(cleared.isPlaying)
        assertNull(cleared.sleepTimerEnd)
        assertFalse(cleared.sleepAtEndOfEpisode)
    }

    @Test
    fun clearedStateUsesPreviousSpeedWhenControllerUnavailable() {
        val previous =
            PlayerState(
                isPlaying = true,
                playbackSpeed = 1.25f,
                currentEpisode = sampleEpisode,
            )

        val cleared =
            PlaybackControlSync.clearedStatePreservingControls(
                previous = previous,
                controllerSpeed = null,
            )

        assertEquals(1.25f, cleared.playbackSpeed, 0.0001f)
        assertNull(cleared.currentEpisode)
        assertFalse(cleared.isPlaying)
    }

    @Test
    fun applyPlaybackParametersSpeedUpdatesWhenDifferent() {
        val state = PlayerState(playbackSpeed = 1.0f, isPlaying = true)
        val updated = PlaybackControlSync.applyPlaybackParametersSpeed(state, 1.5f)
        assertEquals(1.5f, updated.playbackSpeed, 0.0001f)
        assertTrue(updated.isPlaying)
    }

    @Test
    fun applyPlaybackParametersSpeedNoopsWhenUnchanged() {
        val state = PlayerState(playbackSpeed = 2.0f)
        val updated = PlaybackControlSync.applyPlaybackParametersSpeed(state, 2.0f)
        assertSame(state, updated)
    }

    @Test
    fun withSyncedPlaybackSpeedMatchesPlayQueueOptimisticContract() {
        val state =
            PlayerState(
                playbackSpeed = 1.0f,
                currentEpisode = sampleEpisode,
                isPlaying = true,
            )
        val synced = PlaybackControlSync.withSyncedPlaybackSpeed(state, controllerSpeed = 2.0f)
        assertEquals(2.0f, synced.playbackSpeed, 0.0001f)
        assertEquals(sampleEpisode.id, synced.currentEpisode?.id)
        assertTrue(synced.isPlaying)
    }

    @Test
    fun withSyncedPlaybackSpeedMatchesRestoreLiveControllerWins() {
        val state = PlayerState(playbackSpeed = 1.0f)
        val restored = PlaybackControlSync.withSyncedPlaybackSpeed(state, controllerSpeed = 1.75f)
        assertEquals(1.75f, restored.playbackSpeed, 0.0001f)
    }

    @Test
    fun sanitizePlaybackSpeedRejectsNaNAndNonPositive() {
        assertEquals(1.0f, PlaybackControlSync.sanitizePlaybackSpeed(Float.NaN), 0.0001f)
        assertEquals(1.0f, PlaybackControlSync.sanitizePlaybackSpeed(0f), 0.0001f)
        assertEquals(1.0f, PlaybackControlSync.sanitizePlaybackSpeed(-2f), 0.0001f)
        assertEquals(1.0f, PlaybackControlSync.sanitizePlaybackSpeed(Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun sanitizePlaybackSpeedClampsToSupportedRange() {
        assertEquals(0.5f, PlaybackControlSync.sanitizePlaybackSpeed(0.1f), 0.0001f)
        assertEquals(3.0f, PlaybackControlSync.sanitizePlaybackSpeed(4.0f), 0.0001f)
        assertEquals(1.5f, PlaybackControlSync.sanitizePlaybackSpeed(1.5f), 0.0001f)
    }
}
