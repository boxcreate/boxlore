package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.core.prefs.HiddenShowsPreferences
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A mood collection whose shows couldn't be read is reported as failed (Try again), not as an
 * empty collection; Try again reads the same collection without counting another pick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ExploreTopicFailureTest {
    /** The next answer for a collection read: null is a read that failed. */
    private var curated: List<Podcast>? = null
    private val curatedReads = mutableListOf<String>()

    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a collection that couldn't be read offers Try again, and Try again reads it again`() {
        val viewModel = viewModel()

        curated = null
        viewModel.onVibeSelected("sleep", "Wind down")
        main.scheduler.runCurrent()
        assertTrue(state(viewModel).topicFailed)
        assertTrue(state(viewModel).searchResults.isEmpty())

        val shows = listOf(TestFixtures.podcast(id = "1", title = "Show 1"))
        curated = shows
        viewModel.retry(ExploreRetry.TOPIC)
        main.scheduler.runCurrent()

        assertFalse(state(viewModel).topicFailed)
        assertEquals(listOf("1"), state(viewModel).searchResults.map { it.id })
        assertEquals("Wind down", state(viewModel).currentVibe)
        assertEquals(listOf("sleep", "sleep"), curatedReads)
        assertEquals(1, viewModel.vibesClickedCount)
    }

    @Test
    fun `a collection that was read and has nothing is not a failure`() {
        val viewModel = viewModel()
        curated = emptyList()
        viewModel.onVibeSelected("sleep", "Wind down")
        main.scheduler.runCurrent()
        assertFalse(state(viewModel).topicFailed)
    }

    private fun state(viewModel: ExploreViewModel) = viewModel.uiState.value as ExploreUiState.Success

    private fun viewModel(): ExploreViewModel {
        val podcasts =
            mock(PodcastRepository::class.java) { invocation ->
                if (invocation.method.name == "getCuratedPodcastsOrNull") {
                    curatedReads += invocation.arguments[0] as String
                    curated
                } else {
                    RETURNS_DEFAULTS.answer(invocation)
                }
            }
        val subscriptions = mock(SubscriptionRepository::class.java)
        `when`(subscriptions.subscribedPodcasts).thenReturn(flowOf(emptyList()))
        `when`(subscriptions.subscribedPodcastIds).thenReturn(flowOf(emptySet()))
        val hidden = mock(HiddenShowsPreferences::class.java)
        `when`(hidden.matchIdsStream).thenReturn(flowOf(emptySet()))
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.cachedExploreDefaultTab).thenReturn("")
        `when`(prefs.regionStream).thenReturn(flowOf("us"))
        `when`(prefs.contentLanguagesStream).thenReturn(flowOf(listOf("en")))
        `when`(prefs.discoverySectionsStream).thenReturn(flowOf(DiscoverySections(pickedForYou = false)))
        `when`(prefs.hiddenShows).thenReturn(hidden)
        return ExploreViewModel(
            application = ApplicationProvider.getApplicationContext(),
            podcastRepository = podcasts,
            subscriptionRepository = subscriptions,
            userPrefs = prefs,
            playbackRepository = mock(PlaybackRepository::class.java),
            adaptiveScorer = mock(AdaptiveCandidateScorer::class.java),
        ).also { main.scheduler.runCurrent() }
    }
}
