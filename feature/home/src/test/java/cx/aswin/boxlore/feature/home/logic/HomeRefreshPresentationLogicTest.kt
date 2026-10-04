package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRefreshPresentationLogicTest {
    private val categoryPodcasts = listOf(TestFixtures.podcast(id = "science"))
    private val basePodcasts = listOf(TestFixtures.podcast(id = "base"))

    @Test
    fun `base refresh cannot blank or replace loaded category cards`() {
        val previous = state("science", categoryPodcasts, loading = false)
        val refreshed = state("science", basePodcasts, loading = true)
            .withCurrentCategoryDiscovery(previous)
        assertEquals(categoryPodcasts, refreshed.discoverPodcasts)
        assertFalse(refreshed.isFilterLoading)
    }

    @Test
    fun `base refresh cannot prematurely finish a pending category request`() {
        val previous = state("science", emptyList(), loading = true)
        val refreshed = state("science", basePodcasts, loading = false)
            .withCurrentCategoryDiscovery(previous)
        assertTrue(refreshed.discoverPodcasts.isEmpty())
        assertTrue(refreshed.isFilterLoading)
    }

    @Test
    fun `category change cannot inherit a different category's cards`() {
        val previous = state("science", categoryPodcasts, loading = false)
        val refreshed = state("comedy", basePodcasts, loading = false)
            .withCurrentCategoryDiscovery(previous)
        assertTrue(refreshed.discoverPodcasts.isEmpty())
        assertTrue(refreshed.isFilterLoading)
    }

    @Test
    fun `for you accepts fresh payloads including valid empty results`() {
        val previous = state(null, categoryPodcasts, loading = false)
        val refreshed = state(null, basePodcasts, loading = false)
        assertEquals(refreshed, refreshed.withCurrentCategoryDiscovery(previous))
        val empty = state(null, emptyList(), loading = false)
        assertEquals(empty, empty.withCurrentCategoryDiscovery(refreshed))
    }

    @Test
    fun `usable cards remain visible during refresh but initial or empty states do not`() {
        assertTrue(showHomeDiscoveryContent(initialLoading = false, hasContent = true))
        assertFalse(showHomeDiscoveryContent(initialLoading = true, hasContent = true))
        assertFalse(showHomeDiscoveryContent(initialLoading = false, hasContent = false))
    }

    private fun state(category: String?, podcasts: List<cx.aswin.boxlore.core.model.Podcast>, loading: Boolean) = HomeUiState(
        heroItems = emptyList(),
        discoverPodcasts = podcasts,
        selectedCategory = category,
        isFilterLoading = loading,
    )
}
