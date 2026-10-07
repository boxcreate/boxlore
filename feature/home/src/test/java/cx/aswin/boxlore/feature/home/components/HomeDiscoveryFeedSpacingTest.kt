package cx.aswin.boxlore.feature.home.components

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.HomeFeedCallbacks
import cx.aswin.boxlore.feature.home.HomePlaybackUi
import cx.aswin.boxlore.feature.home.PodcastFeed
import cx.aswin.boxlore.feature.home.PodcastFeedContent
import cx.aswin.boxlore.feature.home.PodcastFeedDerivedState
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
import cx.aswin.boxlore.feature.home.curatedForYouItems
import cx.aswin.boxlore.feature.home.discoverFeedItems
import cx.aswin.boxlore.feature.home.discoveryGreetingFor
import cx.aswin.boxlore.feature.home.discoveryGreetingItem
import cx.aswin.boxlore.feature.home.logic.editorialRowDefinitionsFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h2600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDiscoveryFeedSpacingTest {
    @get:Rule val composeRule = createComposeRule()

    private val loading = mutableStateOf(false)
    private val showRecommendations = mutableStateOf(false)
    private val showRelated = mutableStateOf(true)
    private val fallback = mutableStateOf(false)
    private val browseEvents = mutableListOf<Triple<String?, String?, String?>>()
    private lateinit var gridState: LazyStaggeredGridState
    private val definitions = editorialRowDefinitionsFor(ContentDaypart.MORNING)
    private val rows = definitions.mapIndexed { index, definition ->
        HomeEditorialRow(
            definition.providerId,
            definition.title,
            definition.subtitle,
            definition.icon,
            listOf(podcast("show$index", "Show $index").copy(latestEpisode = Episode("ep$index", "Episode $index", "", ""))),
        )
    }
    private val feedState = PodcastFeedUiState(
        discoveryGreetingFor(ContentDaypart.MORNING),
        null,
        null,
        null,
        emptyList(),
        podcast("seed", "Seed show"),
        false,
    )
    private val derivedState = PodcastFeedDerivedState(true, false, true, false, emptyList(), false, false, false)
    private val content = PodcastFeedContent(
        StableHeroList(emptyList()),
        StablePodcastList(emptyList()),
        StablePodcastList(emptyList()),
        StableEditorialRowList(rows),
        StablePodcastList(emptyList()),
        StableEpisodeList(listOf(Episode("recommendation", "Recommended episode", "", ""))),
        StableEpisodeList(emptyList()),
    )
    private val callbacks = HomeFeedCallbacks(
        onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = null,
        onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null,
        onNavigateToExplore = { category, source, tab -> browseEvents.add(Triple(category, source, tab)) },
        onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
        onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {}, onAiOnboardingClick = {},
        onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {}, onDismissBriefingForever = {}, onFeedbackClick = {},
    )

    @Test fun `related time rails keep 20dp gaps and Explore follows directly without Video Spotlight`() {
        renderEditorial()
        for (index in 0 until rows.lastIndex) {
            assertEquals(20f, itemTop("editorial_${rows[index + 1].providerId}") - cardBottom("Episode $index"), 1f)
        }
        val lastBottom = cardBottom("Episode ${rows.lastIndex}")
        assertEquals(32f, sectionOpeningTop("discover_header") - lastBottom, 1f)

        composeRule.onNodeWithText("Video Spotlight", ignoreCase = true).assertDoesNotExist()
        assertEquals("discover_header", gridState.layoutInfo.visibleItemsInfo.first { it.index > rows.size }.key)
    }

    @Test fun `time rail loading rows match loaded heights and following section position`() {
        renderEditorial()
        val heights = rows.map { itemHeight("editorial_${it.providerId}") }
        val exploreTop = actionTop(R.string.home_shows_see_all)
        loading.value = true
        composeRule.waitForIdle()
        rows.forEachIndexed { index, row -> assertEquals(heights[index], itemHeight("editorial_${row.providerId}")) }
        assertEquals(exploreTop, actionTop(R.string.home_shows_see_all), 1f)
    }

    @Test fun `seed and personal recommendations are separate chapters with major spacing`() {
        renderRecommendations()
        val greetingTop = sectionOpeningTop("discovery_greeting")
        assertEquals(32f, greetingTop - cardBottom("Similar show"), 1f)
        showRecommendations.value = true
        composeRule.waitForIdle()
        assertEquals(32f, itemTop("for_you_header") - cardBottom("Similar show"), 1f)
    }

    @Test fun `recommendation chapters retain accurate names when seed or personal results are absent`() {
        renderRecommendations()
        composeRule.onNodeWithText(label(R.string.home_because_you_like)).assertExists()
        composeRule.onNodeWithText("More like this").assertDoesNotExist()
        assertEquals(listOf("because_you_like", "discovery_greeting"), visibleKeys())
        composeRule.onNodeWithText(label(R.string.home_picked_for_you)).assertDoesNotExist()
        showRecommendations.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label(R.string.home_picked_for_you)).assertExists()
        fallback.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label(R.string.home_picked_for_you)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.home_popular_region)).assertExists()
        showRelated.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label(R.string.home_because_you_like)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.home_popular_region)).assertExists()
        assertEquals(listOf("curated_header", "for_you_hero", "discovery_greeting"), visibleKeys())
        showRecommendations.value = false
        composeRule.waitForIdle()
        assertEquals(listOf("discovery_greeting"), visibleKeys())
        composeRule.onNodeWithText("Curated for you").assertDoesNotExist()
        composeRule.onNodeWithText("Based on your taste").assertDoesNotExist()
    }

    @Test fun `chapter actions retain their existing destinations and invoke navigation once`() {
        showRecommendations.value = true
        renderRecommendations()
        composeRule.onNodeWithContentDescription(label(R.string.home_recommendations_see_all)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.home_discovery_see_all)).performClick()
        showRelated.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(label(R.string.home_recommendations_see_all)).performClick()
        assertEquals(
            listOf(
                Triple(null, "home_for_you_see_all", "foryou"),
                Triple(null, "home_discovery_greeting", "foryou"),
                Triple(null, "home_for_you_see_all", "foryou"),
            ),
            browseEvents,
        )
    }

    @Test fun `personal chapter keeps recommendation order counts and stable grid keys`() {
        showRecommendations.value = true
        showRelated.value = false
        val recommendations = (0 until 12).map { Episode("rec$it", "Recommendation $it", "", "") }
        renderRecommendations(content.copy(recommendations = StableEpisodeList(recommendations)))
        assertEquals(
            listOf("curated_header", "for_you_hero", "for_you_body_0", "for_you_body_1", "for_you_body_2", "for_you_body_3", "discovery_greeting"),
            visibleKeys(),
        )
        recommendations.take(9).forEach { composeRule.onNodeWithText(it.title).assertExists() }
        recommendations.drop(9).forEach { composeRule.onNodeWithText(it.title).assertDoesNotExist() }
        val positions = recommendations.take(9).map { composeRule.onNodeWithText(it.title).fetchSemanticsNode().boundsInRoot }
        assertEquals(positions[1].top, positions[2].top, 1f)
        assertEquals(positions[3].top, positions[4].top, 1f)
        org.junit.Assert.assertTrue(positions[0].bottom < positions[1].top && positions[1].left < positions[2].left)
    }

    @Test fun `Explore browse and view more keep all and genre destinations with existing show tap context`() {
        val selected = mutableStateOf<String?>(null)
        val showEvents = mutableListOf<Pair<String?, Int?>>()
        val shows = listOf(podcast("explore1", "Explore show one"), podcast("explore2", "Explore show two"))
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.width(328.dp).height(1600.dp),
                    verticalItemSpacing = 16.dp,
                ) {
                    discoverFeedItems(
                        feedState.copy(selectedCategory = selected.value),
                        derivedState.copy(discoverItems = shows, showDiscoverContent = true),
                        callbacks.copy(onPodcastClick = { _, source, category, position ->
                            assertEquals("home_discover_grid", source)
                            showEvents.add(category to position)
                        }),
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription(label(R.string.home_shows_see_all)).performClick()
        composeRule.onNodeWithText(label(R.string.home_view_more_shows)).performClick()
        selected.value = "News"
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(label(R.string.home_shows_see_all)).performClick()
        composeRule.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(R.string.home_view_more_in, "News")).performClick()
        composeRule.onNodeWithText(shows[1].title).performClick()
        assertEquals(
            listOf(
                Triple("All", "home_discover_header", null),
                Triple("All", "home_discover_view_all_button", null),
                Triple("News", "home_discover_header", null),
                Triple("News", "home_discover_view_all_button", null),
            ),
            browseEvents,
        )
        assertEquals(listOf("News" to 1), showEvents)
    }

    private fun renderRecommendations(recommendationContent: PodcastFeedContent = content) {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                gridState = rememberLazyStaggeredGridState()
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.width(328.dp).height(2200.dp).testTag("feed"),
                    verticalItemSpacing = 16.dp,
                ) {
                    curatedForYouItems(
                        recommendationContent,
                        feedState,
                        PodcastFeedRecommendationState(
                            StableEpisodeList(emptyList()),
                            StablePodcastList(listOf(podcast("similar", "Similar show"))),
                            isRecommendationsLoading = false,
                            isRecommendationsFallback = fallback.value,
                        ),
                        PodcastFeedPlayback(HomePlaybackUi(null, null, false), StablePlaybackStateMap(emptyMap())),
                        callbacks,
                        derivedState.copy(hasBecauseYouLike = showRelated.value, hasRecommendations = showRecommendations.value),
                    )
                    discoveryGreetingItem(feedState, callbacks)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderEditorial() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                gridState = rememberLazyStaggeredGridState()
                PodcastFeed(
                    content = content.copy(
                        editorialRows = if (loading.value) StableEditorialRowList(emptyList()) else content.editorialRows,
                        recommendations = StableEpisodeList(emptyList()),
                    ),
                    feedState = feedState,
                    recommendationState = PodcastFeedRecommendationState(
                        StableEpisodeList(emptyList()),
                        StablePodcastList(emptyList()),
                        isRecommendationsLoading = false,
                    ),
                    loadingState = PodcastFeedLoadingState(isEditorialRowsLoading = loading.value, isFilterLoading = false, isLoading = false),
                    playback = PodcastFeedPlayback(HomePlaybackUi(null, null, false), StablePlaybackStateMap(emptyMap())),
                    callbacks = callbacks,
                    layout = PodcastFeedLayout(gridState, Modifier.width(328.dp).height(2200.dp).testTag("feed")),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun itemTop(key: String): Float = gridTop() + gridState.layoutInfo.visibleItemsInfo.single { it.key == key }.offset.y
    private fun itemHeight(key: String): Int = gridState.layoutInfo.visibleItemsInfo.single { it.key == key }.size.height
    private fun sectionOpeningTop(key: String): Float = itemTop(key) + (HomeFeedSpacing.SectionGap - HomeFeedSpacing.GridGap).value
    private fun visibleKeys(): List<Any> = gridState.layoutInfo.visibleItemsInfo.sortedBy { it.index }.map { it.key }
    private fun gridTop(): Float = composeRule.onNodeWithTag("feed").fetchSemanticsNode().boundsInRoot.top
    private fun cardBottom(title: String): Float = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.bottom
    private fun actionTop(id: Int): Float = composeRule.onNodeWithContentDescription(label(id)).fetchSemanticsNode().boundsInRoot.top
    private fun label(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun podcast(id: String, title: String) = Podcast(id = id, title = title, artist = "", imageUrl = "")
}
