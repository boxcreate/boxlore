package cx.aswin.boxlore.feature.explore

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
@Config(sdk = [33], qualifiers = "w412dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreTabPagerTest {
    @get:Rule val composeRule = createComposeRule()
    private val selections = mutableListOf<Int>()
    private val state = mutableStateOf(
        ExploreUiState.Success(
            selectedTab = 1,
            trending = List(30) { Podcast("show_$it", "Top show $it", "Host $it", "") },
            recommendations = List(30) { Episode("episode_$it", "Recommendation $it", "", "", podcastTitle = "Show") },
            suggestedVibes = listOf("morning_news" to "What's happening"),
        ),
    )

    @Test fun `swipe changes both feed and selected switcher with one callback per settle`() {
        show()
        composeRule.onNode(hasText("For you") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithTag("explore_browse_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Top").assertIsSelected()
        composeRule.onNodeWithText("Top show 0").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_browse_pager").performTouchInput { swipeRight() }
        composeRule.onNode(hasText("For you") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("Recommendation 0").assertIsDisplayed()
        assertEquals(listOf(0, 1), selections)
    }

    @Test fun `tab taps animate the same feed without duplicate selection callbacks`() {
        show()
        composeRule.onNodeWithText("Top").performClick()
        composeRule.onNodeWithText("Top show 0").assertIsDisplayed()
        composeRule.onNode(hasText("For you") and isSelectable()).performClick()
        composeRule.onNodeWithText("Recommendation 0").assertIsDisplayed()
        assertEquals(listOf(0, 1), selections)
    }

    @Test fun `Top initial selection and horizontal genre scrolling never switch browse tabs`() {
        state.value = state.value.copy(selectedTab = 0)
        show()
        composeRule.onNodeWithText("Top").assertIsSelected()
        composeRule.onNodeWithText("News").performTouchInput { swipeLeft(durationMillis = 400) }
        composeRule.onNodeWithText("Top").assertIsSelected()
        assertTrue(selections.isEmpty())
    }

    @Test fun `returning to a browse tab keeps its vertical feed position`() {
        show()
        composeRule.onNodeWithTag("explore_feed_1").performScrollToIndex(12)
        val before = scrollValue("explore_feed_1")
        assertTrue(before > 0f)
        composeRule.onNodeWithTag("explore_browse_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Top show 0").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_browse_pager").performTouchInput { swipeRight() }
        assertEquals(before, scrollValue("explore_feed_1"), 0.002f)
    }

    @Test fun `search and mood results use a single feed without browse paging`() {
        state.value = state.value.copy(searchQuery = "Example", isSearching = true)
        show()
        composeRule.onNodeWithTag("explore_browse_pager").assertDoesNotExist()
        composeRule.runOnIdle { state.value = state.value.copy(searchQuery = "", currentVibe = "morning_news") }
        composeRule.onNodeWithTag("explore_browse_pager").assertDoesNotExist()
        assertTrue(selections.isEmpty())
    }

    private fun show() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                ExploreContent(
                    uiState = state.value,
                    onSearchQueryChanged = {},
                    onCategorySelected = {},
                    onPodcastClick = { _, _, _, _ -> },
                    onEpisodeClick = { _, _ -> },
                    onTabSelected = {
                        selections.add(it)
                        state.value = state.value.copy(selectedTab = it)
                    },
                    onVibeSelected = { _, _ -> },
                    onClearVibe = {},
                )
            }
        }
    }

    private fun scrollValue(tag: String): Float =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
}
