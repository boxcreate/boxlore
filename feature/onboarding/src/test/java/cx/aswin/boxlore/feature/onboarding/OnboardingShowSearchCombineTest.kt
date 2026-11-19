package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.catalog.logic.GroupedShowSearchResult
import cx.aswin.boxlore.core.model.Podcast
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingShowSearchCombineTest {
    @Test
    fun prefersCatalogThenLocalThenAlsoFoundWithoutDupes() {
        val catalog = Podcast(id = "1", title = "A", artist = "x", imageUrl = "")
        val also = Podcast(id = "2", title = "B", artist = "y", imageUrl = "")
        val localDup = Podcast(id = "1", title = "A local", artist = "x", imageUrl = "")
        val localExtra = Podcast(id = "3", title = "C", artist = "z", imageUrl = "")
        val alsoDupLocal = Podcast(id = "3", title = "C also", artist = "z", imageUrl = "")

        val (mergedCatalog, mergedAlso) =
            combineOnboardingShowSearch(
                GroupedShowSearchResult(
                    catalog = listOf(catalog),
                    alsoFound = listOf(also, alsoDupLocal),
                ),
                localMatches = listOf(localDup, localExtra),
            )

        assertEquals(listOf("1", "3"), mergedCatalog.map { it.id })
        assertEquals(listOf("2"), mergedAlso.map { it.id })
    }

    @Test
    fun aSearchThatCouldNotBeReadIsFailedNotNoMatches() {
        // Regression: offline or a 5xx/429 answer showed "No Podcasts Found" for a show that exists.
        val searching = OnboardingUiState(searchQuery = "darknet diaries", isSearching = true)
        val failed = searching.withShowSearch(GroupedShowSearchResult(emptyList(), emptyList(), failed = true), localMatches = emptyList())
        assertTrue(failed.searchFailed)
        assertFalse(failed.isSearching)

        val empty = searching.withShowSearch(GroupedShowSearchResult(emptyList(), emptyList()), localMatches = emptyList())
        assertFalse(empty.searchFailed, "an answer with no rows is no matches")
    }

    @Test
    fun aFailedSearchStillShowsLocalMatches() {
        val local = Podcast(id = "7", title = "Darknet Diaries", artist = "Jack", imageUrl = "")
        val failed = OnboardingUiState().withShowSearch(GroupedShowSearchResult(emptyList(), emptyList(), failed = true), localMatches = listOf(local))
        assertEquals(listOf("7"), failed.searchResults.map { it.id })
    }

    @Test
    fun aThrownSearchIsFailedButANewerQueryCancellingItIsNot() {
        val searching = OnboardingUiState(isSearching = true)
        val thrown = searching.withShowSearchError(IOException("timeout"))
        assertTrue(thrown.searchFailed)
        assertFalse(thrown.isSearching)

        // Regression: the cancelled search ended the newer search's loader, so "No Podcasts Found"
        // showed while that search was still running.
        val cancelled = searching.withShowSearchError(CancellationException("newer query"))
        assertTrue(cancelled.isSearching, "the newer search is still running")
        assertFalse(cancelled.searchFailed)
        assertEquals(searching, cancelled)
    }
}
