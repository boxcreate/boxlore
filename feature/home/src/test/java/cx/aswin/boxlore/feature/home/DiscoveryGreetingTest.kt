package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Narrow Home greeting path extracted from [HomeViewModel] (pure helper; no Application).
 * Full Home VM bootstrap/category still needs Robolectric + heavy repository fakes (deferred).
 */
class DiscoveryGreetingTest {
    private val weekday = LocalDate.of(2026, 7, 15) // Wednesday
    private val friday = LocalDate.of(2026, 7, 17)
    private val saturday = LocalDate.of(2026, 7, 18)

    @Test
    fun `morning weekday uses start-your-day subtitle`() {
        val greeting = discoveryGreetingFor(ContentDaypart.MORNING, weekday)
        assertEquals(R.string.home_greeting_morning_title, greeting.title)
        assertEquals(R.string.home_greeting_morning_weekday, greeting.subtitle)
        assertEquals(ContentDaypart.MORNING, greeting.daypart)
    }

    @Test
    fun `morning weekend uses catch-up subtitle`() {
        val greeting = discoveryGreetingFor(ContentDaypart.MORNING, saturday)
        assertEquals(R.string.home_greeting_morning_weekend, greeting.subtitle)
    }

    @Test
    fun `evening friday kicks off the weekend`() {
        val greeting = discoveryGreetingFor(ContentDaypart.EVENING, friday)
        assertEquals(R.string.home_greeting_evening_title, greeting.title)
        assertEquals(R.string.home_greeting_evening_friday, greeting.subtitle)
    }

    @Test
    fun `evening non-friday uses relax subtitle`() {
        val greeting = discoveryGreetingFor(ContentDaypart.EVENING, weekday)
        assertEquals(R.string.home_greeting_evening_other, greeting.subtitle)
    }

    @Test
    fun `afternoon and late night copy is stable`() {
        assertEquals(
            R.string.home_greeting_afternoon_subtitle,
            discoveryGreetingFor(ContentDaypart.AFTERNOON, weekday).subtitle,
        )
        assertEquals(
            R.string.home_greeting_late_night_subtitle,
            discoveryGreetingFor(ContentDaypart.LATE_NIGHT, weekday).subtitle,
        )
    }

    @Test
    fun `the greeting shows while its picks load or once some arrive, never over an empty set`() {
        assertEquals(true, showsDiscoveryGreeting(settingOn = true, rowsLoading = true, rowCount = 0))
        assertEquals(true, showsDiscoveryGreeting(settingOn = true, rowsLoading = false, rowCount = 2))
        assertEquals(false, showsDiscoveryGreeting(settingOn = true, rowsLoading = false, rowCount = 0))
        assertEquals(false, showsDiscoveryGreeting(settingOn = false, rowsLoading = false, rowCount = 2))
    }
}
