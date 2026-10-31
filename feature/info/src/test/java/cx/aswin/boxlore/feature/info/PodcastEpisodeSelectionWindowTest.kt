package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastEpisodeSelectionWindowTest {
    private fun episodes(vararg ids: String) = ids.map { TestFixtures.episode(id = it) }

    private fun page(
        episodes: List<String>,
        sort: EpisodeSort = EpisodeSort.NEWEST,
    ) = PodcastInfoUiState.Success(
        podcast = TestFixtures.podcast(id = "p1"),
        episodes = episodes(*episodes.toTypedArray()),
        isSubscribed = false,
        currentSort = sort,
    )

    @Test
    fun `the first page comes first, then loaded episodes it doesn't list, without repeats`() = runTest {
        val requests = mutableListOf<Pair<Int, String>>()
        val window =
            loadPodcastEpisodeSelectionWindow(page(listOf("e2", "e9")), sort = null) { limit, sort ->
                requests += limit to sort
                PodcastRepository.EpisodePage(episodes("e1", "e2", "e3"), hasMore = false)
            }

        assertEquals(listOf(MAX_SELECTION_EPISODES to "newest"), requests)
        assertEquals(listOf("e1", "e2", "e3", "e9"), window.episodes.map { it.id })
        assertTrue(window.newestFirst)
        assertFalse(window.isTruncated)
    }

    @Test
    fun `the window stops at the cap and says when the show has more`() = runTest {
        val many = (1..MAX_SELECTION_EPISODES).map { "e$it" }.toTypedArray()
        val window =
            loadPodcastEpisodeSelectionWindow(page(listOf("loaded")), sort = null) { _, _ ->
                PodcastRepository.EpisodePage(episodes(*many), hasMore = true)
            }

        assertEquals(MAX_SELECTION_EPISODES, window.episodes.size)
        assertFalse(window.episodes.any { it.id == "loaded" })
        assertTrue(window.isTruncated)
    }

    @Test
    fun `a saved sort wins over the page's current sort`() = runTest {
        val sorts = mutableListOf<String>()
        val loadPage: suspend (Int, String) -> PodcastRepository.EpisodePage = { _, sort ->
            sorts += sort
            PodcastRepository.EpisodePage(emptyList(), hasMore = false)
        }

        val saved = loadPodcastEpisodeSelectionWindow(page(emptyList(), EpisodeSort.NEWEST), EpisodeSort.OLDEST, loadPage)
        val current = loadPodcastEpisodeSelectionWindow(page(emptyList(), EpisodeSort.OLDEST), sort = null, loadPage)

        assertFalse(saved.newestFirst)
        assertFalse(current.newestFirst)
        assertEquals(listOf("oldest", "oldest"), sorts)
    }

    @Test
    fun `without a loaded page the window is empty and nothing is read`() = runTest {
        var reads = 0
        val window =
            loadPodcastEpisodeSelectionWindow(state = null, sort = EpisodeSort.OLDEST) { _, _ ->
                reads++
                PodcastRepository.EpisodePage(emptyList(), hasMore = true)
            }

        assertEquals(PodcastEpisodeSelectionWindow(emptyList(), newestFirst = false, isTruncated = false), window)
        assertEquals(0, reads)
    }
}
