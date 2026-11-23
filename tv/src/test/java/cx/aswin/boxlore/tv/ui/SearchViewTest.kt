package cx.aswin.boxlore.tv.ui

import cx.aswin.boxlore.tv.model.TvSearchResult
import cx.aswin.boxlore.tv.model.TvShow
import cx.aswin.boxlore.tv.ui.search.SearchView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchViewTest {
    @Test
    fun `a search that cannot reach boxlore says so instead of showing what is trending`() {
        // Before, it fell back to Trending as if nothing had been typed, with no way to try again.
        assertEquals(SearchView.Failed, SearchView.of(TvSearchResult.NoConnection("tesla")))
        assertEquals(SearchView.Trending, SearchView.of(null))
    }

    @Test
    fun `a search that reached boxlore shows its results or says it found nothing`() {
        assertEquals(SearchView.Empty, SearchView.of(TvSearchResult.Found("zzz", emptyList(), emptyList())))
        assertEquals(SearchView.Results, SearchView.of(TvSearchResult.Found("tech", listOf(TvShow("1", "Tech", "", null)), emptyList())))
    }
}
