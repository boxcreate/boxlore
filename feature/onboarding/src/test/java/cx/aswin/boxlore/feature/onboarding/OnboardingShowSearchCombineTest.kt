package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.catalog.logic.GroupedShowSearchResult
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
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
}
