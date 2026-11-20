package cx.aswin.boxlore.feature.info

import android.app.Application
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.PodcastInfoSupplementSupportTest.FakePort
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPullRefreshLogic
import cx.aswin.boxlore.feature.info.logic.PostSubscribeRefreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A feed check that fails, on pull to refresh or on open, never shows "Up to date": the chip says
 * the check failed until a check succeeds, and a pull says why.
 */
// Robolectric so the pull's error log runs as on a phone.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastInfoFeedCheckFailureTest {
    private val port = FakePort(optedIn = mutableSetOf("123"))
    private var piList: () -> List<cx.aswin.boxlore.core.model.Episode> = { listOf(TestFixtures.episode(id = "pi-1")) }
    private val support =
        PodcastInfoSupplementSupport(
            episodeSupplementPort = port,
            loadPage = { _, _, _, _ -> PodcastRepository.EpisodePage(listOf(TestFixtures.episode(id = "pi-1")), hasMore = false, sourceCount = 1) },
            loadPiList = { piList() },
        )
    private val pageState = PodcastInfoPageState()

    private fun show(chip: DirectFeedChipState = DirectFeedChipState.Updated) = PodcastInfoUiState.Success(
        podcast = TestFixtures.podcast(id = "123").copy(feedUrl = "https://feeds.example/show.xml"),
        episodes = emptyList(),
        isSubscribed = false,
        directFeedChip = chip,
    )

    private fun pullRefresh(scope: CoroutineScope) = PodcastInfoPullRefresh(
        pageState = pageState,
        repository = mock(PodcastRepository::class.java),
        rssRepository = mock(RssPodcastRepository::class.java),
        subscriptionRepository = mock(SubscriptionRepository::class.java),
        localCatalog = mock(LocalCatalogPort::class.java),
        supplementSupport = support,
        feedFirst = mock(PodcastInfoFeedFirst::class.java),
        postSubscribeRefreshes = PostSubscribeRefreshes(),
        scope = scope,
    )

    @Test
    fun `a silent check whose feed read fails shows the failure, not Up to date`() = runTest {
        port.refreshOutcome = EpisodeSupplementOutcome.Failure("Couldn't update episodes from the feed")

        val refresh = support.refreshMissingEpisodes(show(DirectFeedChipState.Fetching), announceResult = false)

        assertEquals(DirectFeedChipState.CheckFailed, refresh.state.directFeedChip)
        assertNull("the check on open stays silent", refresh.state.userMessage)
        assertEquals("Couldn't update episodes from the feed", refresh.failureMessage)
    }

    @Test
    fun `a failed check stays failed through later reloads until a check succeeds`() = runTest {
        val failed = show(DirectFeedChipState.CheckFailed)
        assertEquals(DirectFeedChipState.CheckFailed, support.remountWithSupplements(failed, piEpisodes = emptyList()).directFeedChip)

        port.refreshOutcome = EpisodeSupplementOutcome.Success(addedCount = 0, totalSupplementCount = 0, newestFeedEpisode = null)
        val refresh = support.refreshMissingEpisodes(failed, announceResult = false)
        assertEquals(DirectFeedChipState.Updated, refresh.state.directFeedChip)
        assertNull(refresh.failureMessage)
    }

    @Test
    fun `a failed check is pulled again from the feed`() {
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.DIRECT_FEED,
            PodcastInfoPullRefreshLogic.target(isRss = false, chip = DirectFeedChipState.CheckFailed),
        )
    }

    @Test
    fun `a pull whose feed read fails says so and never shows Up to date`() = runTest {
        port.refreshOutcome = EpisodeSupplementOutcome.Failure("Couldn't update episodes from the feed")
        pageState.currentPodcastId = "123"
        pageState.uiState.value = show()

        pullRefresh(this).refresh()
        advanceUntilIdle()

        val after = pageState.uiState.value as PodcastInfoUiState.Success
        assertEquals(DirectFeedChipState.CheckFailed, after.directFeedChip)
        assertEquals(UiText.raw("Couldn't update episodes from the feed"), after.userMessage)
        assertEquals(false, after.isRssRefreshing)
    }

    @Test
    fun `a pull whose check throws says so and never shows Up to date`() = runTest {
        piList = { error("PI list HTTP 503") }
        pageState.currentPodcastId = "123"
        pageState.uiState.value = show()

        pullRefresh(this).refresh()
        advanceUntilIdle()

        val after = pageState.uiState.value as PodcastInfoUiState.Success
        assertEquals(DirectFeedChipState.CheckFailed, after.directFeedChip)
        assertEquals(PodcastInfoSupplementSupport.FEED_CHECK_FAILED_MESSAGE, after.userMessage)
        assertEquals(false, after.isRssRefreshing)
    }
}
