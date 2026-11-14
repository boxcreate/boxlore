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
    fun `a skip inside the episode moves the position even with no ticker running`() {
        val skipped = PlaybackControllerStatePolicy.afterSeek(restoredState, isSeek = true, sameItem = true, positionMs = 3_630_000L)

        assertEquals(3_630_000L, skipped.position)
        assertEquals(restoredState.duration, skipped.duration)
    }

    @Test
    fun `a move to another item, or a change that is not a seek, leaves the position alone`() {
        assertEquals(restoredState, PlaybackControllerStatePolicy.afterSeek(restoredState, isSeek = true, sameItem = false, positionMs = 0L))
        assertEquals(restoredState, PlaybackControllerStatePolicy.afterSeek(restoredState, isSeek = false, sameItem = true, positionMs = 0L))
    }

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

    @Test
    fun `progress read once is published for the episode the state names`() {
        val state = restoredState.copy(currentEpisode = episode("ep-1"))

        val merged =
            PlaybackControllerStatePolicy.mergeProgressForCurrentItem(
                previous = state,
                snapshot = PlaybackControllerStatePolicy.Snapshot(hasMedia = true, positionMs = 90_000L, bufferedPositionMs = 120_000L, durationMs = 1_800_000L),
                playerEpisodeId = "ep-1",
            )

        assertEquals(90_000L, merged.position)
        assertEquals(1_800_000L, merged.duration)
    }

    @Test
    fun `progress of the player's next item is not paired with the previous episode`() {
        val state = restoredState.copy(currentEpisode = episode("ep-1"))
        val nextItemProgress = PlaybackControllerStatePolicy.Snapshot(hasMedia = true, positionMs = 4_000L, bufferedPositionMs = 9_000L, durationMs = 2_400_000L)

        // The player moved on while the state still names the previous episode.
        assertEquals(state, PlaybackControllerStatePolicy.mergeProgressForCurrentItem(state, nextItemProgress, playerEpisodeId = "ep-2"))
        // Without a known item, or without an episode in the state, there is nothing to pair it with.
        assertEquals(state, PlaybackControllerStatePolicy.mergeProgressForCurrentItem(state, nextItemProgress, playerEpisodeId = null))
        assertEquals(restoredState, PlaybackControllerStatePolicy.mergeProgressForCurrentItem(restoredState, nextItemProgress, playerEpisodeId = "ep-2"))
    }

    private fun episode(id: String) = cx.aswin.boxlore.core.model.Episode(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        publishedDate = 1_700_000_000L,
    )
}
