package cx.aswin.boxlore.feature.briefing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `playback leaves the story list alone while the listener scrolls and for the hold after the scroll ends`() {
        val hold = StoryFollowHold()
        assertTrue(hold.mayFollow(0L), "Nothing scrolled yet")
        // A long scroll: started at 0, still going at 6s, ended at 7s.
        hold.onScrolling(true, 0L)
        assertFalse(hold.mayFollow(6_000L), "Never while the listener scrolls")
        hold.onScrolling(false, 7_000L)
        assertFalse(hold.mayFollow(7_000L + LISTENER_SCROLL_HOLD_MS - 1), "The hold runs from the end of the scroll")
        assertTrue(hold.mayFollow(7_000L + LISTENER_SCROLL_HOLD_MS))
        // The list's own move to the playing story starts no hold.
        hold.following = true
        hold.onScrolling(true, 20_000L)
        hold.following = false
        hold.onScrolling(false, 20_500L)
        assertTrue(hold.mayFollow(20_600L))
    }
}
