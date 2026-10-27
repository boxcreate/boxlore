package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Report times survive a new process, so a show is reported once a day however often the app starts (P4 PR 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesFeedReportStampsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a report sent in one process holds in the next, and old times are dropped`() {
        val day = FeedReportLimiter.PER_SHOW_INTERVAL_MS
        val prefs = context.getSharedPreferences(SharedPreferencesFeedReportStamps.FILE_NAME, Context.MODE_PRIVATE)
        FeedReportLimiter(SharedPreferencesFeedReportStamps(prefs)).record("920666", 10 * day)

        val nextProcess = SharedPreferencesFeedReportStamps(context.getSharedPreferences(SharedPreferencesFeedReportStamps.FILE_NAME, Context.MODE_PRIVATE))
        assertFalse(FeedReportLimiter(nextProcess).allows("920666", 10 * day + 1))

        FeedReportLimiter(nextProcess).record("1", 12 * day)
        assertEquals(mapOf("1" to 12 * day), nextProcess.all())
        assertEquals("boxlore_feed_reports", SharedPreferencesFeedReportStamps.FILE_NAME)
    }
}
