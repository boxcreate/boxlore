package cx.aswin.boxlore.core.playback

import androidx.media3.common.TrackSelectionParameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackPowerPolicyTest {
    @Test
    fun `UI position ticker runs only for visible active playback`() {
        assertTrue(
            PlaybackPowerPolicy.shouldRunUiPositionTicker(
                isUiForeground = true,
                isPlaying = true,
                isLoading = false,
            ),
        )
        assertTrue(
            PlaybackPowerPolicy.shouldRunUiPositionTicker(
                isUiForeground = true,
                isPlaying = false,
                isLoading = true,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldRunUiPositionTicker(
                isUiForeground = false,
                isPlaying = true,
                isLoading = false,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldRunUiPositionTicker(
                isUiForeground = false,
                isPlaying = false,
                isLoading = true,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldRunUiPositionTicker(
                isUiForeground = true,
                isPlaying = false,
                isLoading = false,
            ),
        )
    }

    @Test
    fun `playback power intervals remain battery bounded`() {
        assertEquals(500L, PlaybackPowerPolicy.UI_POSITION_POLL_INTERVAL_MS)
        assertEquals(500L, PlaybackPowerPolicy.OUTRO_POLL_INTERVAL_MS)
        assertEquals(15 * 60 * 1_000L, PlaybackPowerPolicy.PAUSED_IDLE_TIMEOUT_MS)
    }

    @Test
    fun `paused teardown is limited to background local playback`() {
        assertTrue(
            PlaybackPowerPolicy.shouldSchedulePausedLocalTeardown(
                isUiForeground = false,
                isRemote = false,
                isPlaying = false,
                playWhenReady = false,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldSchedulePausedLocalTeardown(
                isUiForeground = true,
                isRemote = false,
                isPlaying = false,
                playWhenReady = false,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldSchedulePausedLocalTeardown(
                isUiForeground = false,
                isRemote = true,
                isPlaying = false,
                playWhenReady = false,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldSchedulePausedLocalTeardown(
                isUiForeground = false,
                isRemote = false,
                isPlaying = false,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun `outro polling only runs for an active ending trim`() {
        assertTrue(
            PlaybackPowerPolicy.shouldMonitorOutro(
                isPlaying = true,
                effectiveSkipEndingMs = 30_000L,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldMonitorOutro(
                isPlaying = true,
                effectiveSkipEndingMs = 0L,
            ),
        )
        assertFalse(
            PlaybackPowerPolicy.shouldMonitorOutro(
                isPlaying = false,
                effectiveSkipEndingMs = 30_000L,
            ),
        )
    }

    @Test
    fun `audio offload preserves playback speed support`() {
        val preferences = PlaybackPowerPolicy.audioOffloadPreferences()

        assertEquals(
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
            preferences.audioOffloadMode,
        )
        assertTrue(preferences.isSpeedChangeSupportRequired)
    }

    @Test
    fun `resumed playback is not torn down after suspended progress persistence`() = runTest {
        val persistenceStarted = CompletableDeferred<Unit>()
        val allowPersistenceToFinish = CompletableDeferred<Unit>()
        var isIdle = true
        var wasTornDown = false

        val teardown =
            launch {
                PlaybackPowerPolicy.persistThenTearDownIfStillIdle(
                    persistProgress = {
                        persistenceStarted.complete(Unit)
                        allowPersistenceToFinish.await()
                    },
                    isStillIdle = { isIdle },
                    tearDown = { wasTornDown = true },
                )
            }

        persistenceStarted.await()
        isIdle = false
        allowPersistenceToFinish.complete(Unit)
        teardown.join()

        assertFalse(wasTornDown)
    }
}
