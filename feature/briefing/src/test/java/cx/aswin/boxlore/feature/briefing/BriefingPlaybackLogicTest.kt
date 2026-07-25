package cx.aswin.boxlore.feature.briefing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BriefingPlaybackLogicTest {
    @Test
    fun `selecting another story seeks without pausing active briefing`() {
        val action =
            resolveBriefingPlaybackAction(
                isCurrentBriefing = true,
                isPlaying = true,
                requestedPositionMs = 120_000L,
            )

        assertEquals(
            BriefingPlaybackAction.SeekToStory(
                positionMs = 120_000L,
                resumeAfterSeek = false,
            ),
            action,
        )
    }

    @Test
    fun `selecting a story resumes its paused briefing after seeking`() {
        val action =
            resolveBriefingPlaybackAction(
                isCurrentBriefing = true,
                isPlaying = false,
                requestedPositionMs = 120_000L,
            )

        assertEquals(
            BriefingPlaybackAction.SeekToStory(
                positionMs = 120_000L,
                resumeAfterSeek = true,
            ),
            action,
        )
    }

    @Test
    fun `main play button pauses only the active briefing`() {
        assertEquals(
            BriefingPlaybackAction.Pause,
            resolveBriefingPlaybackAction(
                isCurrentBriefing = true,
                isPlaying = true,
                requestedPositionMs = null,
            ),
        )
    }
}
