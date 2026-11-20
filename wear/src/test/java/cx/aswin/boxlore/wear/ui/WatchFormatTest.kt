package cx.aswin.boxlore.wear.ui

import android.app.Application
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WatchFormatTest {
    private val sixthOfNovember = 1_793_923_200_000L

    @Test
    fun `short dates follow the listener's language, order and time zone`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("6 Nov", WatchFormat.shortDate(sixthOfNovember, utc, Locale.UK))
        assertEquals("5 Nov", WatchFormat.shortDate(sixthOfNovember, TimeZone.getTimeZone("America/Los_Angeles"), Locale.UK))
        assertEquals("6 nov", WatchFormat.shortDate(sixthOfNovember, utc, Locale.forLanguageTag("es")))
        // American English puts the month first; the old fixed pattern always wrote the day first.
        assertEquals("Nov 6", WatchFormat.shortDate(sixthOfNovember, utc, Locale.US))
        assertEquals("6. Nov.", WatchFormat.shortDate(sixthOfNovember, utc, Locale.GERMANY))
    }
}
