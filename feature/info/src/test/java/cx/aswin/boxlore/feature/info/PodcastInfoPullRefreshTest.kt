package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.logic.PostSubscribeRefreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions

/** Pull to refresh's guards: a pull that must not run reads nothing, and a refresh never lands on another show. */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastInfoPullRefreshTest {
    private val repository = mock(PodcastRepository::class.java)
    private val rssRepository = mock(RssPodcastRepository::class.java)
    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val localCatalog = mock(LocalCatalogPort::class.java)
    private val supplementSupport = mock(PodcastInfoSupplementSupport::class.java)
    private val feedFirst = mock(PodcastInfoFeedFirst::class.java)
    private val postSubscribeRefreshes = PostSubscribeRefreshes()
    private val pageState = PodcastInfoPageState()

    private fun pullRefresh(scope: CoroutineScope) = PodcastInfoPullRefresh(
        pageState = pageState,
        repository = repository,
        rssRepository = rssRepository,
        subscriptionRepository = subscriptionRepository,
        localCatalog = localCatalog,
        supplementSupport = supplementSupport,
        feedFirst = feedFirst,
        postSubscribeRefreshes = postSubscribeRefreshes,
        scope = scope,
    )

    private fun show(
        id: String,
        refreshing: Boolean = false,
    ) = PodcastInfoUiState.Success(
        podcast = TestFixtures.podcast(id = id),
        episodes = listOf(TestFixtures.episode(id = "$id-e1", podcastId = id)),
        isSubscribed = false,
        isRssRefreshing = refreshing,
    )

    private fun everyDependency() = arrayOf(repository, rssRepository, subscriptionRepository, localCatalog, supplementSupport)

    @Test
    fun `a pull while a refresh already runs changes and reads nothing`() = runTest {
        pageState.currentPodcastId = "p1"
        val refreshing = show("p1", refreshing = true)
        pageState.uiState.value = refreshing

        pullRefresh(this).refresh()
        advanceUntilIdle()

        assertSame(refreshing, pageState.uiState.value)
        verifyNoInteractions(*everyDependency(), feedFirst)
    }

    @Test
    fun `a pull waits for the catalog refresh after following`() = runTest {
        pageState.currentPodcastId = "p1"
        val page = show("p1")
        pageState.uiState.value = page
        postSubscribeRefreshes.begin("p1", takesLine = false)

        pullRefresh(this).refresh()
        advanceUntilIdle()

        assertSame(page, pageState.uiState.value)
        verifyNoInteractions(*everyDependency())
    }

    @Test
    fun `a refresh for a show the page left doesn't touch the new show's page`() = runTest {
        pageState.currentPodcastId = "p1"
        pageState.uiState.value = show("p1")

        pullRefresh(this).refresh()
        assertTrue((pageState.uiState.value as PodcastInfoUiState.Success).isRssRefreshing)

        // The listener opens another show before the refresh runs.
        pageState.currentPodcastId = "p2"
        val otherShow = show("p2")
        pageState.uiState.value = otherShow
        advanceUntilIdle()

        verify(repository).invalidateEpisodesCache("p1")
        verifyNoMoreInteractions(repository)
        verifyNoInteractions(supplementSupport, localCatalog)
        assertSame(otherShow, pageState.uiState.value)
    }
}
