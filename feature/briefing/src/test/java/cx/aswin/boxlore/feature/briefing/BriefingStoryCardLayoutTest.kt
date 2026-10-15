package cx.aswin.boxlore.feature.briefing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingStoryCardLayoutTest {
    @Test
    fun `short windows scroll the whole story card so the story stays reachable`() {
        assertTrue(storyCardScrollsAsOne(360))
        assertTrue(storyCardScrollsAsOne(STORY_CARD_PINNED_MIN_WINDOW_DP - 1))
    }

    @Test
    fun `tall windows keep the pinned actions and the scrolling text area`() {
        assertFalse(storyCardScrollsAsOne(STORY_CARD_PINNED_MIN_WINDOW_DP))
        assertFalse(storyCardScrollsAsOne(800))
    }
}
