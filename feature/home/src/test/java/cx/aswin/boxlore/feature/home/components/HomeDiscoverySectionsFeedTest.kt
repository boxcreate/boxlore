package cx.aswin.boxlore.feature.home.components

import android.content.Context
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.HomeFeedCallbacks
import cx.aswin.boxlore.feature.home.HomePlaybackUi
import cx.aswin.boxlore.feature.home.PodcastFeed
import cx.aswin.boxlore.feature.home.PodcastFeedContent
import cx.aswin.boxlore.feature.home.PodcastFeedLayout
import cx.aswin.boxlore.feature.home.PodcastFeedLoadingState
import cx.aswin.boxlore.feature.home.PodcastFeedPlayback
import cx.aswin.boxlore.feature.home.PodcastFeedRecommendationState
import cx.aswin.boxlore.feature.home.PodcastFeedUiState
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.StableEditorialRowList
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StableHeroList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.discoveryGreetingFor
import cx.aswin.boxlore.feature.home.logic.editorialRowDefinitionsFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home with its optional sections switched off, on the phone and tablet layouts. The ViewModel
 * clears an off section's data; the feed must then show nothing for it at every window size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h2600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDiscoverySectionsFeedTest {
    @get:Rule val composeRule = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 800f))
    private val sectionsOn = mutableStateOf(false)
    private val exploreHasForYou = mutableStateOf(true)
    private val browseEvents = mutableListOf<Pair<String, String?>>()
    private val greeting = discoveryGreetingFor(ContentDaypart.MORNING)
    private val rows = editorialRowDefinitionsFor(ContentDaypart.MORNING).take(1).map { definition ->
        HomeEditorialRow(
            definition.providerId,
            definition.title,
            definition.subtitle,
            definition.icon,
            listOf(podcast("curated", "Curated show").copy(latestEpisode = Episode("curated_ep", "Curated episode", "", ""))),
        )
    }

    @Test
    fun `switched-off sections stay hidden across the tablet width and height gates in both directions`() {
        render()
        val windows = listOf(
            BoxloreWindowLayout(360f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(1_000f, 800f),
            BoxloreWindowLayout(1_000f, 479f),
            BoxloreWindowLayout(1_000f, 480f),
            BoxloreWindowLayout(1_280f, 800f),
            BoxloreWindowLayout(360f, 800f),
        )
        windows.forEach { size ->
            composeRule.runOnIdle { window.value = size }
            composeRule.waitForIdle()
            assertSectionsShown(false, size)
        }
    }

    @Test
    fun `sections switched on or off follow the switch live on both layouts`() {
        render()
        composeRule.runOnIdle {
            window.value = BoxloreWindowLayout(1_000f, 800f)
            sectionsOn.value = true
        }
        composeRule.waitForIdle()
        assertSectionsShown(true, window.value)

        composeRule.runOnIdle { window.value = BoxloreWindowLayout(360f, 800f) }
        composeRule.waitForIdle()
        assertSectionsShown(true, window.value)

        composeRule.runOnIdle { sectionsOn.value = false }
        composeRule.waitForIdle()
        assertSectionsShown(false, window.value)

        composeRule.runOnIdle { window.value = BoxloreWindowLayout(1_000f, 800f) }
        composeRule.waitForIdle()
        assertSectionsShown(false, window.value)
    }

    @Test
    fun `the greeting browses Explore's charts once For you is gone`() {
        sectionsOn.value = true
        render()
        browseGreeting()
        composeRule.runOnIdle { exploreHasForYou.value = false }
        browseGreeting()

        assertEquals(listOf("home_discovery_greeting" to "foryou", "home_discovery_greeting" to null), browseEvents)
    }

    private fun browseGreeting() {
        composeRule.onNodeWithContentDescription(label(R.string.home_discovery_see_all)).performClick()
        composeRule.waitForIdle()
    }

    private fun assertSectionsShown(shown: Boolean, size: BoxloreWindowLayout) {
        val message = "window ${size.widthDp}x${size.heightDp}"
        listOf(greeting.title, rows.single().title, label(R.string.home_picked_for_you), label(R.string.home_because_you_like)).forEach { text ->
            val nodes = composeRule.onAllNodesWithTextFetched(text)
            assertEquals("$text, $message", shown, nodes > 0)
        }
        // Charts always stay.
        assertEquals("Explore shows, $message", true, composeRule.onAllNodesWithTextFetched(label(R.string.home_explore_shows)) > 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFetched(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun render() {
        composeRule.setContent {
            val on = sectionsOn.value
            val gridState = rememberLazyStaggeredGridState()
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    PodcastFeed(
                        content = PodcastFeedContent(
                            heroItems = StableHeroList(emptyList()),
                            latestItems = StablePodcastList(emptyList()),
                            subscribedItems = StablePodcastList(emptyList()),
                            editorialRows = StableEditorialRowList(if (on) rows else emptyList()),
                            gridItems = StablePodcastList(List(4) { podcast("chart$it", "Chart show $it") }),
                            recommendations = StableEpisodeList(if (on) listOf(Episode("rec", "Recommended episode", "", "", podcastTitle = "Show")) else emptyList()),
                            selectedPodcastEpisodes = StableEpisodeList(emptyList()),
                        ),
                        feedState = PodcastFeedUiState(
                            discoveryGreeting = greeting,
                            selectedCategory = null,
                            selectedPodcastId = null,
                            briefing = null,
                            briefingChapters = emptyList(),
                            seemsToLikePodcast = if (on) podcast("seed", "Seed show") else null,
                            showImportBanner = false,
                            showDiscoveryGreeting = on,
                            exploreHasForYou = exploreHasForYou.value,
                        ),
                        recommendationState = PodcastFeedRecommendationState(
                            becauseYouLikeRecommendations = StableEpisodeList(emptyList()),
                            becauseYouLikePodcasts = StablePodcastList(if (on) listOf(podcast("similar", "Similar show")) else emptyList()),
                            isRecommendationsLoading = false,
                            isRecommendationsFallback = false,
                        ),
                        loadingState = PodcastFeedLoadingState(isEditorialRowsLoading = false, isFilterLoading = false, isLoading = false),
                        playback = PodcastFeedPlayback(HomePlaybackUi(null, null, false), StablePlaybackStateMap(emptyMap())),
                        callbacks = callbacks,
                        layout = PodcastFeedLayout(gridState),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private val callbacks = HomeFeedCallbacks(
        onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = null,
        onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null,
        onNavigateToExplore = { _, source, tab -> browseEvents.add(source to tab) },
        onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
        onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {}, onAiOnboardingClick = {},
        onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {}, onDismissBriefingForever = {}, onFeedbackClick = {},
    )

    private fun label(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun podcast(id: String, title: String) = Podcast(id, title, "Host", "")
}
