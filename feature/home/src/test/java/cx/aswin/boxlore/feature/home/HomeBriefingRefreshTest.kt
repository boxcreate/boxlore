package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.prefs.BriefingEdition
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeBriefingRefreshTest {
    @Test
    fun `saved edition refreshes at date and daypart changes without following discovery country`() = runTest {
        val edition = MutableStateFlow("global")
        val region = MutableStateFlow("us")
        val clock = MutableStateFlow(HomeClockContext(ContentDaypart.MORNING, LocalDate.of(2026, 10, 11)))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            briefingLoadRegions(edition, region, clock).collect { requests.add(it) }
        }
        assertEquals(listOf("global"), requests)

        region.value = "in"
        clock.value = clock.value.copy()
        assertEquals(listOf("global"), requests)

        clock.value = clock.value.copy(daypart = ContentDaypart.EVENING)
        clock.value = clock.value.copy(date = clock.value.date.plusDays(1))
        assertEquals(listOf("global", "global", "global"), requests)

        edition.value = "gb"
        assertEquals(listOf("global", "global", "global", "gb"), requests)
    }

    @Test
    fun `following app region reloads only when its Brief market changes`() = runTest {
        val edition = MutableStateFlow(BriefingEdition.FOLLOW_APP)
        val region = MutableStateFlow("us")
        val clock = MutableStateFlow(HomeClockContext(ContentDaypart.MORNING, LocalDate.of(2026, 10, 11)))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            briefingLoadRegions(edition, region, clock).collect { requests.add(it) }
        }
        region.value = "uk"
        region.value = "gb"
        region.value = "de"
        region.value = "fr"
        assertEquals(listOf("us", "gb", "global"), requests)
    }
}
