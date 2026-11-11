package cx.aswin.boxlore.core.playback.service.auto

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoDayLabelsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 10, 9, 0, 0, 0, zone)

    private fun daysAgo(published: ZonedDateTime) = AutoDayLabels.daysAgo(published.toEpochSecond(), now.toInstant().toEpochMilli(), zone)

    @Test
    fun `an episode from earlier today is zero days old`() {
        assertEquals(0L, daysAgo(now.minusHours(8)))
    }

    @Test
    fun `an episode from late last night counts as yesterday, by the calendar and not by hours`() {
        assertEquals(1L, daysAgo(now.minusHours(10)))
    }

    @Test
    fun `older episodes count whole calendar days`() {
        assertEquals(27L, daysAgo(now.minusDays(27)))
    }

    @Test
    fun `a missing or future date has no age`() {
        assertNull(AutoDayLabels.daysAgo(0L, now.toInstant().toEpochMilli(), zone))
        assertNull(daysAgo(now.plusDays(1)))
    }

    @Test
    fun `the short date drops the year only within the same year`() {
        val published = ZonedDateTime.of(2025, 10, 14, 12, 0, 0, 0, zone).toEpochSecond()

        assertEquals("14 Oct", AutoDayLabels.shortDate(published, sameYear = true, zone = zone, locale = Locale.UK))
        assertEquals("14 Oct 2025", AutoDayLabels.shortDate(published, sameYear = false, zone = zone, locale = Locale.UK))
    }
}
