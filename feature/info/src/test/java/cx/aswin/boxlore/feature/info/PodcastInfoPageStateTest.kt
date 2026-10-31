package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoPageStateTest {
    private fun success(
        id: String = "p1",
        subscribed: Boolean = false,
        loadingMore: Boolean = true,
        refreshing: Boolean = true,
        episodes: List<String> = emptyList(),
    ) = PodcastInfoUiState.Success(
        podcast = TestFixtures.podcast(id = id),
        episodes = episodes.map { TestFixtures.episode(id = it, podcastId = id) },
        isSubscribed = subscribed,
        isLoadingMore = loadingMore,
        isRssRefreshing = refreshing,
    )

    private fun PodcastInfoPageState.shown() = uiState.value as PodcastInfoUiState.Success

    @Test
    fun `a refresh ends only on the show it started on`() {
        val page = PodcastInfoPageState().apply { currentPodcastId = "p2" }
        page.uiState.value = success("p2")

        page.clearPullRefreshing("p1")
        assertTrue(page.shown().isRssRefreshing, "the page moved to p2 while p1 refreshed")

        page.clearPullRefreshing("p2")
        assertFalse(page.shown().isRssRefreshing)
        assertFalse(page.shown().isLoadingMore)
    }

    @Test
    fun `a feed-extras result for another show is dropped`() {
        val page = PodcastInfoPageState().apply { currentPodcastId = "p2" }
        val shown = success("p2")
        page.uiState.value = shown

        assertNull(page.applySupplementRefreshState("p1", success("p1", episodes = listOf("e1"))))
        assertSame(shown, page.uiState.value)
    }

    @Test
    fun `a feed-extras result keeps the follow state the page shows now`() {
        val page = PodcastInfoPageState().apply { currentPodcastId = "p1" }
        page.uiState.value = success(subscribed = true)

        // The refresh started before following, so its result still says not followed.
        val applied = page.applySupplementRefreshState("p1", success(subscribed = false, loadingMore = false, episodes = listOf("e1")))

        assertTrue(applied!!.isSubscribed)
        assertEquals(listOf("e1"), page.shown().episodes.map { it.id })
        assertSame(applied, page.uiState.value)
    }

    @Test
    fun `the loading row clears without touching anything else`() {
        val page = PodcastInfoPageState()
        page.uiState.value = success(loadingMore = true, refreshing = true)

        page.stopLoadingMore()

        assertFalse(page.shown().isLoadingMore)
        assertTrue(page.shown().isRssRefreshing)
        val unchanged = page.uiState.value
        page.stopLoadingMore()
        assertSame(unchanged, page.uiState.value)
    }

    @Test
    fun `the resolved show is recorded only for the route still requested`() {
        val page = PodcastInfoPageState().apply { requestedPodcastId = "route-2" }

        page.setShownPodcastId("route-1", "show-1")
        assertEquals(PodcastInfoPageShow(), page.pageShow.value)

        page.setShownPodcastId("route-2", "show-2")
        assertEquals(PodcastInfoPageShow("route-2", "show-2"), page.pageShow.value)
    }

    @Test
    fun `oldest-first pages are longer and every sort maps to its parameter`() {
        assertEquals(20, PodcastInfoPageState.pageSizeFor(EpisodeSort.NEWEST))
        assertEquals(50, PodcastInfoPageState.pageSizeFor(EpisodeSort.OLDEST))
        assertEquals("newest", PodcastInfoPageState.sortParamFor(EpisodeSort.NEWEST))
        assertEquals("oldest", PodcastInfoPageState.sortParamFor(EpisodeSort.OLDEST))
    }
}
