package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The typing layout, keyboard clearance and notification-ask rules, without composition. */
class FeedbackLayoutPolicyTest {
    @Test fun `the typing layout needs both focus and a keyboard`() {
        assertTrue(isTypingLayout(messageFocused = true, imeBottom = 300.dp))
        assertFalse(isTypingLayout(messageFocused = true, imeBottom = 0.dp))
        assertFalse(isTypingLayout(messageFocused = false, imeBottom = 300.dp))
    }

    @Test fun `the keyboard replaces the bottom chrome instead of stacking on it`() {
        assertEquals(300.dp, feedbackBottomPadding(imeBottom = 300.dp, chrome = 96.dp, systemBar = 24.dp))
        assertEquals(120.dp, feedbackBottomPadding(imeBottom = 0.dp, chrome = 96.dp, systemBar = 24.dp))
    }

    @Test fun `Android's prompt is asked for only while it will still show`() {
        assertTrue(canAskForNotifications(sdkInt = 33, granted = false, askedBefore = false, shouldShowRationale = false))
        assertTrue(canAskForNotifications(sdkInt = 33, granted = false, askedBefore = true, shouldShowRationale = true))
        assertFalse("Denied twice: open settings instead", canAskForNotifications(33, granted = false, askedBefore = true, shouldShowRationale = false))
        assertFalse(canAskForNotifications(sdkInt = 33, granted = true, askedBefore = false, shouldShowRationale = false))
        assertFalse("No runtime prompt before Android 13", canAskForNotifications(32, granted = false, askedBefore = false, shouldShowRationale = false))
    }

    @Test fun `only a conversation promises a reply in Feedback, and dropped photos are mentioned`() {
        assertTrue(feedbackSentBody(repliesInApp = true).contains("reply will show up in Feedback"))
        assertFalse(feedbackSentBody(repliesInApp = false).contains("reply"))
        assertTrue(feedbackSentBody(repliesInApp = false, photosDropped = true).endsWith("Your photos couldn't be sent this time."))
    }

    @Test fun `typing stops at the limit, and text already past it can only shrink`() {
        assertEquals("abc", limitedMessage(current = "ab", next = "abc", limit = 5))
        assertEquals("abcde", limitedMessage(current = "ab", next = "abcdefg", limit = 5))
        assertEquals("abcdefg", limitedMessage(current = "abcdefgh", next = "abcdefg", limit = 5))
        assertEquals("abcdefgh", limitedMessage(current = "abcdefgh", next = "abcdefghi", limit = 5))
    }
}
