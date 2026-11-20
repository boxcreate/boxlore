package cx.aswin.boxlore.feature.history.timeline

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Timeline's and story's dates and times, written in the listener's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimelineTimeFormatTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun englishKeepsItsDayMonthAndTimeWording() {
        assertEquals("Wednesday, Oct 28", plain(TimelineTime.dayTitleText(LocalDate.of(2026, 10, 28), false, Locale.US)))
        assertEquals("Tuesday, Oct 28, 2025", plain(TimelineTime.dayTitleText(LocalDate.of(2025, 10, 28), true, Locale.US)))
        assertEquals("October", TimelineTime.monthTitle(YearMonth.of(2026, 10), today, Locale.US))
        assertEquals("December 2025", plain(TimelineTime.monthTitle(YearMonth.of(2025, 12), today, Locale.US)))
        assertEquals("October 2026", plain(TimelineTime.monthYearTitle(YearMonth.of(2026, 10), Locale.US)))
        assertEquals("Oct 27", plain(TimelineTime.shortDate(LocalDate.of(2026, 10, 27), Locale.US)))
        assertEquals("Mon, Sep 14", plain(TimelineTime.shortWeekdayDate(LocalDate.of(2026, 9, 14), Locale.US)))
        val india = ZoneOffset.ofHoursMinutes(5, 30)
        val evening = LocalDateTime.of(2026, 10, 30, 20, 12).toInstant(india).toEpochMilli()
        assertEquals("8:12 pm", plain(TimelineTime.clockTime(evening, india, false, Locale.US)))
        assertEquals("20:12", TimelineTime.clockTime(evening, india, true, Locale.US))
    }

    @Test
    fun germanUsesItsOwnOrderAndNames() {
        assertEquals("27. Okt.", plain(TimelineTime.shortDate(LocalDate.of(2026, 10, 27), Locale.GERMANY)))
        assertEquals("Dezember 2025", plain(TimelineTime.monthTitle(YearMonth.of(2025, 12), today, Locale.GERMANY)))
        assertEquals("Mittwoch, 28. Okt.", plain(TimelineTime.dayTitleText(LocalDate.of(2026, 10, 28), false, Locale.GERMANY)))
    }

    @Test
    fun aDateNeverShiftsWithTheDevicesTimeZone() {
        val default = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("Oct 27", plain(TimelineTime.shortDate(LocalDate.of(2026, 10, 27), Locale.US)))
        } finally {
            java.util.TimeZone.setDefault(default)
        }
    }

    /** ICU puts narrow no-break spaces in some patterns; compare the words. */
    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')
}
