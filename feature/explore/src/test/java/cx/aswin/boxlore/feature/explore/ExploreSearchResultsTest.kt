package cx.aswin.boxlore.feature.explore

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreSearchResultsTest {
    @get:Rule val composeRule = createComposeRule()
    private val state = mutableStateOf(ExploreUiState.Success(searchQuery = "space", isSearching = true))
    private val showTaps = mutableListOf<List<Any?>>()
    private val episodeTaps = mutableListOf<Pair<Episode, Podcast>>()
    private val queries = mutableListOf<String>()
    private val shows = List(10) { Podcast("show_$it", "Show $it", "Host $it", "", genre = "Science") }
    private val episodes = List(3) {
        Episode("ep_$it", "Episode $it", "", "", podcastId = "parent_$it", podcastTitle = "Parent show $it", podcastGenre = "Science", duration = if (it == 1) 3240 else 0)
    }

    @Test fun `show search keeps ordered catalog and additional results with their original tap positions`() {
        state.value = state.value.copy(searchResults = shows.take(2), alsoFoundResults = listOf(shows[1], shows[2], shows[2]))
        show()
        composeRule.onNodeWithText("Matches").assertIsDisplayed()
        composeRule.onNodeWithText("Show 0").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Show 1").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Also found").assertIsDisplayed()
        composeRule.onNodeWithText("Show 2").assertIsDisplayed().performClick()
        assertEquals(1, composeRule.onAllNodesWithText("Show 1").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Host 0").assertDoesNotExist()
        assertEquals(
            listOf(listOf("show_0", "explore_search", "All", 0), listOf("show_1", "explore_search", "All", 1), listOf("show_2", "explore_search_also_found", "All", 2)),
            showTaps,
        )
        assertEquals("space", state.value.searchQuery)
        assertEquals(emptyList<String>(), queries)
    }

    @Test fun `show search hides generic podcast chips in both result sections while keeping genre and video badges`() {
        state.value = state.value.copy(
            searchResults = listOf(shows[0].copy(genre = "Podcast", medium = "video"), shows[1]),
            alsoFoundResults = listOf(shows[2].copy(genre = " podcast ")),
        )
        show()
        composeRule.onNodeWithText("Podcast").assertDoesNotExist()
        composeRule.onNodeWithText(" podcast ").assertDoesNotExist()
        composeRule.onNodeWithText("Science").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Video podcast").assertIsDisplayed()
        composeRule.onNodeWithText("Show 0").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Show 2").assertIsDisplayed().performClick()
        assertEquals(listOf("show_0", "show_2"), showTaps.map { it.first() })
    }

    @Test fun `topic search keeps compact shows distinct from featured and poster episodes`() {
        semantic()
        show()
        composeRule.onNodeWithText("Related shows").assertIsDisplayed()
        composeRule.onNodeWithText("Show 0").assertIsDisplayed().performClick()
        val showBounds = composeRule.onNodeWithText("Show 0").getUnclippedBoundsInRoot()
        val featured = composeRule.onNodeWithText("Episode 0").getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("Episode 0").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Episode 1").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("54 min").assertIsDisplayed()
        composeRule.onNodeWithText("Parent show 1").assertDoesNotExist()
        assertTrue((showBounds.right - showBounds.left).value <= 104f)
        assertTrue((featured.right - featured.left).value > 300f)
        assertEquals(listOf(listOf("show_0", "explore_search_concept", "All", 0)), showTaps)
        assertEquals(listOf("ep_0", "ep_1"), episodeTaps.map { it.first.id })
        assertEquals(listOf("parent_0", "parent_1"), episodeTaps.map { it.second.id })
        assertEquals(listOf("Parent show 0", "Parent show 1"), episodeTaps.map { it.second.title })
        assertEquals(emptyList<String>(), queries)
    }

    @Test fun `episode only topic results remain usable without a show rail`() {
        semantic()
        state.value = state.value.copy(semanticPodcastResults = emptyList())
        show()
        composeRule.onNodeWithText("Related shows").assertDoesNotExist()
        composeRule.onNodeWithText("Episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Episode 0").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Episode 2").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("0 min").assertDoesNotExist()
        assertEquals(listOf("ep_0", "ep_2"), episodeTaps.map { it.first.id })
        assertEquals(emptyList<List<Any?>>(), showTaps)
    }

    @Test fun `existing results stay visible during progressive and semantic refreshes`() {
        state.value = state.value.copy(searchResults = shows.take(2), isLoading = true)
        show()
        composeRule.onNodeWithText("Show 0").assertIsDisplayed()
        composeRule.onNodeWithText("No shows found").assertDoesNotExist()
        composeRule.onNodeWithText("Searching for shows").assertDoesNotExist()
        composeRule.runOnIdle {
            semantic()
            state.value = state.value.copy(isSemanticLoading = true)
        }
        composeRule.onNodeWithText("Episode 0").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing matched that idea").assertDoesNotExist()
        composeRule.onNodeWithText("Finding shows and episodes").assertDoesNotExist()
    }

    @Test fun `long episode titles remain reachable with enlarged RTL text`() {
        semantic()
        val longTitle = "A long episode about the history of astronomy and the people who changed how we see the universe"
        state.value = state.value.copy(semanticSearchResults = listOf(episodes[0].copy(title = longTitle), episodes[1].copy(title = "$longTitle — part two")))
        show(fontScale = 1.6f, direction = LayoutDirection.Rtl)
        composeRule.onNodeWithTag("explore_feed_1").performScrollToIndex(2)
        composeRule.onNodeWithText(longTitle).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("explore_feed_1").performScrollToIndex(3)
        composeRule.onNodeWithText("$longTitle — part two").assertIsDisplayed().performClick()
        val bounds = composeRule.onNodeWithText("$longTitle — part two").getUnclippedBoundsInRoot()
        assertTrue(bounds.left.value >= 0f && bounds.right.value <= 360f)
        assertEquals(listOf("ep_0", "ep_1"), episodeTaps.map { it.first.id })
        assertEquals(SearchTab.EPISODES, state.value.searchTab)
    }

    private fun semantic() {
        state.value = state.value.copy(searchTab = SearchTab.EPISODES, semanticPodcastResults = shows, semanticSearchResults = episodes, hasPerformedSemanticSearch = true)
    }

    private fun show(fontScale: Float = 1f, direction: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalLayoutDirection provides direction) {
                BoxLoreTheme(dynamicColor = false) {
                    ExploreContent(
                        uiState = state.value,
                        onSearchQueryChanged = { queries.add(it) },
                        onCategorySelected = {},
                        onPodcastClick = { id, entry, category, index -> showTaps.add(listOf(id, entry, category, index)) },
                        onEpisodeClick = { episode, podcast -> episodeTaps.add(episode to podcast) },
                        onTabSelected = { state.value = state.value.copy(selectedTab = it) },
                        onSearchTabSelected = { state.value = state.value.copy(searchTab = it) },
                        onVibeSelected = { _, _ -> },
                        onClearVibe = {},
                    )
                }
            }
        }
    }
}
