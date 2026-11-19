package cx.aswin.boxlore.feature.explore.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreLoadFailuresTest {
    private val text = MutableStateFlow("")
    private var failures = ExploreLoadFailures()
    private val searched = mutableListOf<String>()

    /** Stands in for Explore's show search: the same debounced text and failure steps, answering [fails]. */
    private fun TestScope.searchShows(fails: (String) -> Boolean) {
        text.debouncedShowSearchText().onEach { query ->
            if (query.isBlank()) return@onEach
            searched += query
            failures = failures.showSearchStarted().showSearchAnswered(fails(query))
        }.launchIn(backgroundScope)
        runCurrent()
    }

    private fun TestScope.type(query: String, thenWaitMs: Long) {
        text.value = query
        advanceTimeBy(thenWaitMs)
    }

    @Test
    fun `going back to the failed text within the debounce keeps Try again`() = runTest {
        // Regression: editing the text cleared the failure, but text that returned to the failed
        // query ran no new search, so the screen said "No shows found" for a show that exists.
        searchShows { true }
        type("darknet", SHOW_SEARCH_DEBOUNCE_MS + 1)
        assertEquals(listOf("darknet"), searched)
        assertTrue(failures.showSearch)

        type("darkne", 100)
        type("darknet", SHOW_SEARCH_DEBOUNCE_MS + 1)

        assertEquals(listOf("darknet"), searched, "the same text starts no new search")
        assertTrue(failures.showSearch, "the failed search still offers Try again")
    }

    @Test
    fun `a new search that is read clears the failure`() = runTest {
        searchShows { it == "darknet" }
        type("darknet", SHOW_SEARCH_DEBOUNCE_MS + 1)
        assertTrue(failures.showSearch)

        type("darknet diaries", SHOW_SEARCH_DEBOUNCE_MS + 1)

        assertEquals(listOf("darknet", "darknet diaries"), searched)
        assertFalse(failures.showSearch)
    }

    @Test
    fun `scrolling never loads a page into a failed chart or after a failed page`() {
        // Regression: a page loaded by scrolling filled a failed chart that stayed flagged, so coming
        // back to Explore reloaded it from the first page and lost the shows scrolled to.
        assertTrue(ExploreLoadFailures().scrollMayLoadNextPage)
        assertFalse(ExploreLoadFailures(topChart = true).scrollMayLoadNextPage)
        assertFalse(ExploreLoadFailures(nextTopChartPage = true).scrollMayLoadNextPage)
        assertTrue(ExploreLoadFailures(showSearch = true).scrollMayLoadNextPage, "a failed search doesn't stop the chart")
    }
}
