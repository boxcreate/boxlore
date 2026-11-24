package cx.aswin.boxlore.core.model.text

import cx.aswin.boxlore.core.model.text.LocaleFormats.DateStyle
import cx.aswin.boxlore.core.model.text.LocaleFormats.DurationWidth
import cx.aswin.boxlore.core.model.text.LocaleFormats.RelativeStyle
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** android.icu formatting in English and German (Robolectric runs the platform's ICU on the JVM). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocaleFormatsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val german = Locale.GERMANY
    private val english = Locale.US

    // Thursday, 5 March 2026, 14:05 UTC.
    private val moment = 1_772_719_500_000L

    @Test
    fun `dates follow each language's order and names`() {
        assertEquals("Mar 5, 2026", plain(LocaleFormats.date(moment, DateStyle.DAY_MONTH_YEAR, english, utc)))
        assertEquals("5. März 2026", plain(LocaleFormats.date(moment, DateStyle.DAY_MONTH_YEAR, german, utc)))
        assertEquals("Mar 5", plain(LocaleFormats.date(moment, DateStyle.DAY_MONTH, english, utc)))
        assertEquals("Thursday, March 5, 2026", plain(LocaleFormats.date(moment, DateStyle.WEEKDAY_DAY_MONTH_YEAR, english, utc)))
        assertEquals("Donnerstag, 5. März 2026", plain(LocaleFormats.date(moment, DateStyle.WEEKDAY_DAY_MONTH_YEAR, german, utc)))
        assertEquals("March 2026", plain(LocaleFormats.date(moment, DateStyle.MONTH_YEAR, english, utc)))
        assertEquals("März", plain(LocaleFormats.date(moment, DateStyle.MONTH, german, utc)))
        assertEquals("Mar", plain(LocaleFormats.date(moment, DateStyle.SHORT_MONTH, english, utc)))
        assertEquals("Mär", plain(LocaleFormats.date(moment, DateStyle.SHORT_MONTH, german, utc)))
        assertEquals("5", plain(LocaleFormats.date(moment, DateStyle.DAY, german, utc)))
        assertEquals("Donnerstag", plain(LocaleFormats.date(moment, DateStyle.WEEKDAY, german, utc)))
    }

    @Test
    fun `times follow the clock setting or the language's habit`() {
        assertEquals("2:05 PM", plain(LocaleFormats.time(moment, english, timeZone = utc)))
        assertEquals("14:05", plain(LocaleFormats.time(moment, english, use24Hour = true, timeZone = utc)))
        assertEquals("14:05", plain(LocaleFormats.time(moment, german, timeZone = utc)))
    }

    @Test
    fun `durations use the language's unit names`() {
        assertEquals("1 hr, 5 min", plain(LocaleFormats.duration(3_900, english)))
        assertEquals("1h 5m", plain(LocaleFormats.duration(3_900, english, DurationWidth.NARROW)))
        assertEquals("2 hours", plain(LocaleFormats.duration(7_200, english, DurationWidth.WIDE)))
        assertEquals("45 min", plain(LocaleFormats.duration(2_700, english)))
        assertEquals("30 sec", plain(LocaleFormats.duration(30, english)))
        assertEquals("0 sec", plain(LocaleFormats.duration(-5, english)))
        assertEquals("1 Std., 5 Min.", plain(LocaleFormats.duration(3_900, german)))
    }

    @Test
    fun `relative times pick the largest whole unit`() {
        val now = moment
        assertEquals("now", LocaleFormats.relativeTime(now - 30_000, now, english))
        assertEquals("5 minutes ago", LocaleFormats.relativeTime(now - 5 * 60_000, now, english))
        assertEquals("3 hours ago", LocaleFormats.relativeTime(now - 3 * HOUR, now, english))
        assertEquals("2 days ago", LocaleFormats.relativeTime(now - 2 * DAY, now, english))
        assertEquals("1 week ago", LocaleFormats.relativeTime(now - 8 * DAY, now, english))
        assertEquals("2 months ago", LocaleFormats.relativeTime(now - 65 * DAY, now, english))
        assertEquals("1 year ago", LocaleFormats.relativeTime(now - 400 * DAY, now, english))
        assertEquals("in 3 hours", LocaleFormats.relativeTime(now + 3 * HOUR, now, english))
        assertEquals("vor 3 Stunden", LocaleFormats.relativeTime(now - 3 * HOUR, now, german))
        assertEquals("jetzt", LocaleFormats.relativeTime(now, now, german))
        assertEquals("Now", LocaleFormats.relativeTime(now, now, english, capitalized = true))
        assertEquals("3 hr. ago", LocaleFormats.relativeTime(now - 3 * HOUR, now, english, RelativeStyle.SHORT))
    }

    @Test
    fun compactAgeKeepsEnglishWordingAndUsesTheLanguageElsewhere() {
        val now = 1_700_000_000_000L
        val english = listOf(5 * 60_000L, 3 * HOUR, 2 * DAY, 8 * DAY, 65 * DAY, 400 * DAY).map { LocaleFormats.compactAgo(now - it, now, Locale.UK) }
        assertEquals(listOf("5m ago", "3h ago", "2d ago", "1w ago", "2mo ago", "1y ago"), english)
        assertTrue(LocaleFormats.compactAgo(now - 3 * HOUR, now, Locale.GERMAN).startsWith("vor 3"))
    }

    @Test
    fun `numbers group and compact the language's way`() {
        assertEquals("12,345", LocaleFormats.number(12_345, english))
        assertEquals("12.345", LocaleFormats.number(12_345, german))
        assertEquals("1.5M", LocaleFormats.compactNumber(1_500_000, english))
        assertEquals("1,5 Mio.", plain(LocaleFormats.compactNumber(1_500_000, german)))
        assertEquals("42%", LocaleFormats.percent(0.42, english))
        assertEquals("42 %", plain(LocaleFormats.percent(0.42, german)))
    }

    /** ICU separates some parts with no-break spaces; the tests compare the words. */
    private fun plain(text: String): String = text.replace(' ', ' ').replace(' ', ' ')

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
