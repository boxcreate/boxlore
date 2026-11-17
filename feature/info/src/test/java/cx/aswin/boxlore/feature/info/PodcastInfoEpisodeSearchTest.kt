package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowEpisodeSearch
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

/** In-show search on the page: a search that can't read the show's episodes says so instead of "no matches". */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastInfoEpisodeSearchTest {
    private val pageState = PodcastInfoPageState()
    private val found = listOf(TestFixtures.episode(id = "moon", title = "Moon landing", podcastId = "rss:p1"))
    private var searchFails = true
    private var searches = 0
    private var searchedNewest: Int? = null

    /** The repository's search: throws while [searchFails], as it does when the server's list can't be read. */
    private val repository =
        mock(
            PodcastRepository::class.java,
            Answer { invocation ->
                if (invocation.method.name == "searchShowEpisodes") {
                    searches++
                    if (searchFails) throw IOException("HTTP 503") else ShowEpisodeSearch(found, searchedNewest)
                } else {
                    RETURNS_DEFAULTS.answer(invocation)
                }
            },
        )

    private fun paging(scope: kotlinx.coroutines.CoroutineScope) = PodcastInfoEpisodePaging(
        pageState = pageState,
        repository = repository,
        subscriptionRepository = mock(SubscriptionRepository::class.java),
        // An RSS show's results need no feed extras, so the support passes them through.
        supplementSupport = PodcastInfoSupplementSupport(mock(EpisodeSupplementPort::class.java)) { _, _, _, _ ->
            PodcastRepository.EpisodePage(emptyList<Episode>(), hasMore = false)
        },
        feedFirst = mock(PodcastInfoFeedFirst::class.java),
        scope = scope,
    )

    private fun state() = pageState.uiState.value as PodcastInfoUiState.Success

    @Test
    fun `a failed search shows the failure, not no matches, and Try again finds the episode`() = runTest {
        // Reported: a failed read of the show's list showed "No matching episodes" for episodes that exist.
        pageState.currentPodcastId = "rss:p1"
        pageState.uiState.value =
            PodcastInfoUiState.Success(
                podcast = TestFixtures.podcast(id = "rss:p1", sourceType = Podcast.SOURCE_RSS),
                episodes = listOf(TestFixtures.episode(id = "e1", podcastId = "rss:p1")),
                isSubscribed = false,
            )
        val paging = paging(this)

        paging.search("moon")
        advanceUntilIdle()

        assertTrue(state().searchFailed)
        assertFalse(state().isSearching)
        assertEquals(emptyList<Episode>(), state().searchResults)

        // Try again runs the same search.
        searchFails = false
        paging.search(state().searchQuery)
        runCurrent()
        assertFalse(state().searchFailed, "the failure clears while the search runs again")
        assertTrue(state().isSearching)
        advanceUntilIdle()

        assertFalse(state().searchFailed)
        assertEquals(listOf("moon"), state().searchResults?.map { it.id })
        assertEquals(2, searches)
    }

    @Test
    fun `closing search clears a failure`() = runTest {
        pageState.currentPodcastId = "rss:p1"
        pageState.uiState.value =
            PodcastInfoUiState.Success(
                podcast = TestFixtures.podcast(id = "rss:p1", sourceType = Podcast.SOURCE_RSS),
                episodes = emptyList(),
                isSubscribed = false,
            )
        val paging = paging(this)
        paging.search("moon")
        advanceUntilIdle()

        paging.search("")

        assertFalse(state().searchFailed)
        assertEquals(null, state().searchResults)
    }

    @Test
    fun `a search of a show longer than the server lists says how far it searched, and the next search clears it`() = runTest {
        // Reviewed: a search cut at the route's last offset read as if it covered the whole show.
        pageState.currentPodcastId = "rss:p1"
        pageState.uiState.value =
            PodcastInfoUiState.Success(
                podcast = TestFixtures.podcast(id = "rss:p1", sourceType = Podcast.SOURCE_RSS),
                episodes = emptyList(),
                isSubscribed = false,
            )
        searchFails = false
        searchedNewest = 11_000
        val paging = paging(this)

        paging.search("moon")
        advanceUntilIdle()
        assertEquals(11_000, state().searchedNewest)

        searchedNewest = null
        paging.search("moon landing")
        advanceUntilIdle()
        assertEquals(null, state().searchedNewest)

        searchedNewest = 11_000
        paging.search("moon")
        advanceUntilIdle()
        paging.search("")
        assertEquals(null, state().searchedNewest, "closing search clears it")
    }
}
