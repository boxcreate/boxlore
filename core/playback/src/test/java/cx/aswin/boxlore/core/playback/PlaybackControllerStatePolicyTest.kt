package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackControllerStatePolicyTest {
    private val restoredState =
        PlayerState(
            position = 3_600_000L,
            bufferedPosition = 3_700_000L,
            duration = 7_200_000L,
        )

    @Test
    fun `empty controller cannot erase Room restored progress`() {
        val merged =
            PlaybackControllerStatePolicy.mergeProgress(
                previous = restoredState,
                snapshot =
                PlaybackControllerStatePolicy.Snapshot(
                    hasMedia = false,
                    positionMs = 0L,
                    bufferedPositionMs = 0L,
                    durationMs = -1L,
                ),
            )

        assertEquals(restoredState, merged)
    }

    @Test
    fun `unprepared controller with media cannot erase restored progress`() {
        val merged =
            PlaybackControllerStatePolicy.mergeProgress(
                previous = restoredState,
                snapshot =
                PlaybackControllerStatePolicy.Snapshot(
                    hasMedia = true,
                    positionMs = 0L,
                    bufferedPositionMs = 0L,
                    durationMs = -1L,
                ),
            )

        assertEquals(3_600_000L, merged.position)
        assertEquals(3_700_000L, merged.bufferedPosition)
        assertEquals(7_200_000L, merged.duration)
    }

    @Test
    fun `prepared controller replaces restored progress including a real zero position`() {
        val merged =
            PlaybackControllerStatePolicy.mergeProgress(
                previous = restoredState,
                snapshot =
                PlaybackControllerStatePolicy.Snapshot(
                    hasMedia = true,
                    positionMs = 0L,
                    bufferedPositionMs = 250_000L,
                    durationMs = 7_300_000L,
                ),
            )

        assertEquals(0L, merged.position)
        assertEquals(250_000L, merged.bufferedPosition)
        assertEquals(7_300_000L, merged.duration)
    }

    @Test
    fun `controller position can advance while duration is still unknown`() {
        val merged =
            PlaybackControllerStatePolicy.mergeProgress(
                previous = restoredState,
                snapshot =
                PlaybackControllerStatePolicy.Snapshot(
                    hasMedia = true,
                    positionMs = 3_610_000L,
                    bufferedPositionMs = 3_720_000L,
                    durationMs = -1L,
                ),
            )

        assertEquals(3_610_000L, merged.position)
        assertEquals(3_720_000L, merged.bufferedPosition)
        assertEquals(7_200_000L, merged.duration)
    }

    @Test
    fun `empty player resume prefers Room over frozen UI state`() {
        assertEquals(
            3_600_000L,
            PlaybackControllerStatePolicy.resolveResumePositionMs(
                persistedPositionMs = 3_600_000L,
                restoredStatePositionMs = 60_000L,
            ),
        )
        assertEquals(
            60_000L,
            PlaybackControllerStatePolicy.resolveResumePositionMs(
                persistedPositionMs = null,
                restoredStatePositionMs = 60_000L,
            ),
        )
    }

    @Test
    fun `policy-selected restart takes precedence over stale restored progress`() {
        assertEquals(
            0L,
            PlaybackControllerStatePolicy.resolveResumePositionMs(
                persistedPositionMs = 0L,
                restoredStatePositionMs = 3_600_000L,
            ),
        )
    }
}
