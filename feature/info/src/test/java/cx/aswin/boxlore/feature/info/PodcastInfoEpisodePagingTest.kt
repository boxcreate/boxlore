package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.ApiRouteBudget
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

/**
 * A show page whose next page (or a new order's first page) keeps failing: after a few tries the
 * list says so with Try again instead of loading forever or ending, and Try again loads the page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastInfoEpisodePagingTest {
    private val pageState = PodcastInfoPageState()
    private var answer: () -> PodcastRepository.EpisodePage = { failedPage() }
    private var asked = 0

    /** The list memory each read was given (the `list` argument). */
    private val lists = mutableListOf<Any?>()

    private val repository =
        mock(
            PodcastRepository::class.java,
            Answer { invocation ->
                when (invocation.method.name) {
                    "getShowPageEpisodes" -> {
                        asked++
                        lists += invocation.arguments[LIST_ARGUMENT]
                        answer()
                    }
                    "getRouteBudget" -> ApiRouteBudget()
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            },
        )

    /** Remounts as the real support does, without feed extras. */
    private val supplementSupport =
        mock(
            PodcastInfoSupplementSupport::class.java,
            Answer { invocation ->
                if (invocation.method.name == "remountWithSupplements") {
                    val state = invocation.arguments[0] as PodcastInfoUiState.Success

                    @Suppress("UNCHECKED_CAST")
                    val episodes = invocation.arguments[1] as List<Episode>
                    state.copy(
                        episodes = episodes,
                        piEpisodes = episodes,
                        hasMoreEpisodes = invocation.arguments[2] as Boolean,
                        isLoadingMore = invocation.arguments[3] as Boolean,
                        pageLoadFailed = false,
                    )
                } else {
                    RETURNS_DEFAULTS.answer(invocation)
                }
            },
        )

    private fun TestScope.paging() = PodcastInfoEpisodePaging(
        pageState = pageState,
        repository = repository,
        subscriptionRepository = mock(SubscriptionRepository::class.java),
        supplementSupport = supplementSupport,
        feedFirst = mock(PodcastInfoFeedFirst::class.java),
        scope = this,
    )

    private fun showWithFirstPage() {
        val first = listOf(TestFixtures.episode(id = "e1", podcastId = "p1"))
        pageState.currentPodcastId = "p1"
        pageState.currentOffset = 20
        pageState.uiState.value =
            PodcastInfoUiState.Success(
                podcast = TestFixtures.podcast(id = "p1"),
                episodes = first,
                piEpisodes = first,
                isSubscribed = false,
                hasMoreEpisodes = true,
            )
    }

    private fun state() = pageState.uiState.value as PodcastInfoUiState.Success

    @Test
    fun `a next page that keeps failing ends with Try again, and the list has not ended`() = runTest {
        showWithFirstPage()
        val paging = paging()

        paging.loadMore()
        advanceUntilIdle()

        assertEquals(ShowPageRetry.MAX_ATTEMPTS, asked)
        assertFalse(state().isLoadingMore)
        assertTrue(state().hasMoreEpisodes, "the list doesn't end at a page that failed")
        assertTrue(state().pageLoadFailed)
        assertEquals(20, pageState.currentOffset)

        // Scrolling to the end again doesn't ask by itself: the listener taps Try again.
        paging.loadMore()
        advanceUntilIdle()
        assertEquals(ShowPageRetry.MAX_ATTEMPTS, asked)
    }

    @Test
    fun `Try again loads the page that failed and clears the failure`() = runTest {
        showWithFirstPage()
        val paging = paging()
        paging.loadMore()
        advanceUntilIdle()

        answer = { PodcastRepository.EpisodePage(listOf(TestFixtures.episode(id = "e2", podcastId = "p1")), hasMore = true) }
        paging.retryFailedPage()
        advanceUntilIdle()

        assertFalse(state().pageLoadFailed)
        assertEquals(listOf("e1", "e2"), state().piEpisodes.map { it.id })
        assertEquals(21, pageState.currentOffset)
    }

    @Test
    fun `a new order whose first page keeps failing shows Try again instead of loading forever`() = runTest {
        showWithFirstPage()
        val paging = paging()

        paging.toggleSort()
        advanceUntilIdle()

        assertEquals(EpisodeSort.OLDEST, state().currentSort)
        assertTrue(state().episodes.isEmpty())
        assertFalse(state().isLoadingMore)
        assertTrue(state().pageLoadFailed && state().hasMoreEpisodes)

        answer = { PodcastRepository.EpisodePage(listOf(TestFixtures.episode(id = "old1", podcastId = "p1")), hasMore = true) }
        paging.retryFailedPage()
        advanceUntilIdle()

        assertFalse(state().pageLoadFailed)
        assertEquals(listOf("old1"), state().piEpisodes.map { it.id })
    }

    @Test
    fun `every read of the list, next pages and a new order's first page, keeps to this page's own scheme memory`() = runTest {
        showWithFirstPage()
        val paging = paging()
        answer = { PodcastRepository.EpisodePage(listOf(TestFixtures.episode(id = "e2", podcastId = "p1")), hasMore = true) }

        paging.loadMore()
        advanceUntilIdle()
        paging.toggleSort()
        advanceUntilIdle()

        assertEquals(2, lists.size)
        // Another show page for the same show holds its own memory, so its first page can't switch this list's route.
        assertTrue(lists.all { it === pageState.listSchemes }, "each read passes this page's memory, never the shared one")
    }

    private fun failedPage() = PodcastRepository.EpisodePage(emptyList(), hasMore = false, failed = true)

    private companion object {
        /** `getShowPageEpisodes(feedId, limit, offset, sort, strict, scheme, list, continuation)`. */
        const val LIST_ARGUMENT = 6
    }
}
