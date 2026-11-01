package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.runtime.saveable.SaverScope
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which pane Feedback opens on, where Back goes, when it splits, and how times read. */
class FeedbackPanesTest {
    private val id = "0123456789abcdef01234567"

    @Test fun `a notification's conversation comes first, then the list once there is one`() {
        assertEquals(FeedbackPane.Thread(id), initialFeedbackPane(id, hasConversations = false))
        assertEquals(FeedbackPane.List, initialFeedbackPane("not-an-id", hasConversations = true))
        assertEquals(FeedbackPane.Compose, initialFeedbackPane(null, hasConversations = false))
    }

    @Test fun `Back walks to the list one pane at a time, and leaves side by side`() {
        assertEquals(FeedbackPane.List, feedbackBack(FeedbackPane.Thread(id), hasConversations = true, split = false))
        assertEquals(FeedbackPane.List, feedbackBack(FeedbackPane.Compose, hasConversations = true, split = false))
        assertNull(feedbackBack(FeedbackPane.Compose, hasConversations = false, split = false))
        assertNull(feedbackBack(FeedbackPane.List, hasConversations = true, split = false))
        assertNull(feedbackBack(FeedbackPane.Thread(id), hasConversations = true, split = true))
    }

    @Test fun `side by side needs a landscape tablet with 840dp for Feedback`() {
        assertTrue(feedbackUsesSplit(0f, BoxloreWindowLayout(1280f, 800f)))
        assertTrue(feedbackUsesSplit(840f, BoxloreWindowLayout(1280f, 800f)))
        assertFalse(feedbackUsesSplit(839f, BoxloreWindowLayout(1280f, 800f)))
        assertFalse(feedbackUsesSplit(0f, BoxloreWindowLayout(839f, 600f)))
        assertFalse(feedbackUsesSplit(0f, BoxloreWindowLayout(900f, 1200f)))
        assertFalse(feedbackUsesSplit(0f, BoxloreWindowLayout(900f, 479f)))
    }

    @Test fun `the open pane survives recreation`() {
        val scope = SaverScope { true }
        for (pane in listOf(FeedbackPane.List, FeedbackPane.Compose, FeedbackPane.Thread(id))) {
            val saved = with(FeedbackPaneSaver) { scope.save(pane) }!!
            assertEquals(pane, FeedbackPaneSaver.restore(saved))
        }
    }

    @Test fun `times read as the clock today, Yesterday, then the date`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(month: Int, day: Int, hour: Int, minute: Int, year: Int = 2026) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("11:02", feedbackListTime(at(11, 2, 11, 2), now, zone, Locale.UK, is24Hour = true))
        assertEquals("Yesterday", feedbackListTime(at(11, 1, 23, 0), now, zone, Locale.UK, is24Hour = true))
        assertEquals("28 Oct", feedbackListTime(at(10, 28, 10, 5), now, zone, Locale.UK, is24Hour = true))
        assertEquals("28 Oct 2025", feedbackListTime(at(10, 28, 10, 5, year = 2025), now, zone, Locale.UK, is24Hour = true))
        assertEquals("Today, 11:02", feedbackMessageTime(at(11, 2, 11, 2), now, zone, Locale.UK, is24Hour = true))
        assertEquals("28 Oct, 10:05 AM", feedbackMessageTime(at(10, 28, 10, 5), now, zone, Locale.US, is24Hour = false))
        assertEquals("Yesterday, 23:00", feedbackMessageTime(at(11, 1, 23, 0), now, zone, Locale.UK, is24Hour = true))
        assertEquals("Sent · Waiting for a reply", feedbackStatusLine("Sent · Waiting for a reply", showTime = false, time = "11:02"))
        assertEquals("New reply · 11:02", feedbackStatusLine("New reply", showTime = true, time = "11:02"))
    }
}
