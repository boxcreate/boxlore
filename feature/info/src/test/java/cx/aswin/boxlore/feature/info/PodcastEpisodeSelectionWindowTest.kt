package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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

    /** A show of [count] episodes, newest first, served [SELECTION_PAGE_SIZE] at a time. */
    private fun show(count: Int): List<Episode> = (count downTo 1).map { TestFixtures.episode(id = "e$it", publishedDate = it.toLong()) }

    private fun served(show: List<Episode>, requests: MutableList<Int>? = null): suspend (Int, Int, String) -> PodcastRepository.EpisodePage = { limit, offset, _ ->
        requests?.add(offset)
        val slice = show.drop(offset).take(limit)
        PodcastRepository.EpisodePage(slice, hasMore = offset + slice.size < show.size)
    }

    @Test
    fun `the pages come first, then loaded episodes they don't list, without repeats`() = runTest {
        val requests = mutableListOf<Triple<Int, Int, String>>()
        val window =
            loadPodcastEpisodeSelectionWindow(page(listOf("e2", "e9")), sort = null) { limit, offset, sort ->
                requests += Triple(limit, offset, sort)
                PodcastRepository.EpisodePage(episodes("e1", "e2", "e3"), hasMore = false)
            }

        assertEquals(listOf(Triple(SELECTION_PAGE_SIZE, 0, "newest")), requests)
        assertEquals(listOf("e1", "e2", "e3", "e9"), window.episodes.map { it.id })
        assertTrue(window.newestFirst)
    }

    @Test
    fun `select all reaches every episode of a long show, with no cap`() = runTest {
        // Reported: Select all stopped at the newest 100 (and said 1,000), so older episodes stayed unselected.
        val requests = mutableListOf<Int>()
        val window = loadPodcastEpisodeSelectionWindow(page(listOf("e1234")), sort = null, served(show(1_234), requests))

        assertEquals(1_234, window.episodes.size)
        assertEquals("e1234", window.episodes.first().id)
        assertEquals("e1", window.episodes.last().id)
        assertEquals((0 until 1_234 step SELECTION_PAGE_SIZE).toList(), requests)
    }

    @Test
    fun `episodes are in date order, oldest first when sorted so`() = runTest {
        val oldestFirst = show(250).reversed()
        val window = loadPodcastEpisodeSelectionWindow(page(emptyList(), EpisodeSort.OLDEST), sort = null, served(oldestFirst))

        assertFalse(window.newestFirst)
        assertEquals(oldestFirst.map(Episode::id), window.episodes.map(Episode::id))
    }

    @Test
    fun `a source that keeps saying there is more stops once it adds nothing new`() = runTest {
        var reads = 0
        val window =
            loadPodcastEpisodeSelectionWindow(page(emptyList()), sort = null) { _, _, _ ->
                reads++
                PodcastRepository.EpisodePage(episodes("e1", "e2"), hasMore = true)
            }

        assertEquals(listOf("e1", "e2"), window.episodes.map(Episode::id))
        assertEquals(2, reads)
    }

    @Test
    fun `a page that fails fails the whole window, so a part is never taken for all`() = runTest {
        val show = show(250)
        val failing: suspend (Int, Int, String) -> PodcastRepository.EpisodePage = { limit, offset, sort ->
            if (offset >= SELECTION_PAGE_SIZE) throw IOException("offline")
            served(show)(limit, offset, sort)
        }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { loadPodcastEpisodeSelectionWindow(page(emptyList()), sort = null, failing) }
        }
    }

    @Test
    fun `a saved sort wins over the page's current sort`() = runTest {
        val sorts = mutableListOf<String>()
        val loadPage: suspend (Int, Int, String) -> PodcastRepository.EpisodePage = { _, _, sort ->
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
            loadPodcastEpisodeSelectionWindow(state = null, sort = EpisodeSort.OLDEST) { _, _, _ ->
                reads++
                PodcastRepository.EpisodePage(emptyList(), hasMore = true)
            }

        assertEquals(PodcastEpisodeSelectionWindow(emptyList(), newestFirst = false), window)
        assertEquals(0, reads)
    }
}
