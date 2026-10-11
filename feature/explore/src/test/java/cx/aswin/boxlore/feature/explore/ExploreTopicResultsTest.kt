package cx.aswin.boxlore.feature.explore

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [33], qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreTopicResultsTest {
    @get:Rule val composeRule = createComposeRule()
    private val topicShows = List(20) { Podcast("topic_$it", "Topic show $it", "Topic host $it", "", genre = "News") }
    private val state = mutableStateOf(
        ExploreUiState.Success(
            selectedTab = 1,
            recommendations = List(30) { Episode("episode_$it", "Recommendation $it", "", "", podcastTitle = "Show") },
            suggestedVibes = listOf("morning_news" to "What's happening"),
        ),
    )
    private val topicSelections = mutableListOf<Pair<String, String>>()
    private val taps = mutableListOf<List<Any?>>()
    private var backCalls = 0
    private var parentBackCalls = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test fun `topic shortcut keeps its selection and show tap contracts with title-only cards`() {
        show()
        composeRule.onNodeWithText("What's happening").performClick()
        composeRule.onNodeWithText("The stories shaping today").assertIsDisplayed()
        composeRule.onNodeWithText("Topic show 0").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Topic show 1").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Topic host 0").assertDoesNotExist()
        composeRule.onNodeWithText("News").assertDoesNotExist()
        assertEquals(listOf("morning_news" to "What's happening"), topicSelections)
        assertEquals(listOf(listOf("topic_0", "explore_vibe", "All", 0), listOf("topic_1", "explore_vibe", "All", 1)), taps)
    }

    @Test fun `topic renders even after Ask anything and search keeps that chosen mode`() {
        openTopic()
        state.value = state.value.copy(searchTab = SearchTab.EPISODES)
        show()
        composeRule.onNodeWithText("Topic show 0").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_browse_pager").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithTag("explore_topic_toolbar").assertDoesNotExist()
        composeRule.onNode(hasText("Ask anything") and isSelectable()).assertIsDisplayed()
        composeRule.onNodeWithText("Try a question").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(SearchTab.EPISODES, state.value.searchTab)
        assertEquals(1, backCalls)
    }

    @Test fun `topic loading uses poster placeholders and transitions to actual cards`() {
        openTopic()
        state.value = state.value.copy(isLoading = true, searchResults = emptyList())
        show()
        composeRule.onNodeWithTag("topic_loading_0").assertIsDisplayed()
        composeRule.onNodeWithTag("topic_loading_1").assertIsDisplayed()
        composeRule.onNodeWithText("No podcasts to show right now").assertDoesNotExist()
        val skeleton = composeRule.onNodeWithTag("topic_loading_0").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { state.value = state.value.copy(isLoading = false, searchResults = topicShows) }
        composeRule.onNodeWithTag("topic_loading_0").assertDoesNotExist()
        val title = composeRule.onNodeWithText("Topic show 0").fetchSemanticsNode().boundsInRoot
        assertTrue(title.left >= skeleton.left && title.right <= skeleton.right)
        assertTrue(title.top >= skeleton.top && title.bottom <= skeleton.bottom)
    }

    @Test fun `empty topic returns to browse through its recovery action`() {
        openTopic()
        state.value = state.value.copy(searchResults = emptyList())
        show()
        composeRule.onNodeWithText("No podcasts to show right now").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Back to Explore"))[1].performClick()
        composeRule.onNodeWithText("Recommendation 0").assertIsDisplayed()
        assertEquals(1, backCalls)
    }

    @Test fun `topic has a fresh grid and back retains browse scroll position`() {
        show()
        composeRule.onNodeWithTag("explore_feed_1").performScrollToIndex(12)
        val original = scrollValue()
        assertTrue(original > 0f)
        composeRule.runOnIdle { openTopic() }
        composeRule.onNodeWithText("What's happening").assertIsDisplayed()
        composeRule.onNodeWithText("Topic show 0").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_topic_back").performClick()
        assertEquals(original, scrollValue(), 0.002f)
        assertEquals(1, backCalls)
    }

    @Test fun `system back leaves the topic before delegating to parent navigation`() {
        show()
        composeRule.onNodeWithTag("explore_feed_1").performScrollToIndex(12)
        val original = scrollValue()
        composeRule.runOnIdle { openTopic() }
        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("explore_topic_toolbar").assertDoesNotExist()
        composeRule.onNodeWithTag("explore_browse_pager").assertIsDisplayed()
        assertEquals(original, scrollValue(), 0.002f)
        assertEquals(1, backCalls)
        assertEquals(0, parentBackCalls)
        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, parentBackCalls)
        assertEquals(1, backCalls)
    }

    @Test fun `predictive back reveals Explore and cancellation restores the same topic`() {
        openTopic()
        show()
        composeRule.mainClock.autoAdvance = false
        startBackGesture(BackEventCompat.EDGE_LEFT, 0.55f)
        val topic = composeRule.onNodeWithTag("explore_topic_layer").getUnclippedBoundsInRoot()
        val base = composeRule.onNodeWithTag("explore_base_layer").getUnclippedBoundsInRoot()
        assertTrue(topic.left.value > 150f)
        assertTrue(base.left.value < 0f && base.left.value > -65f)
        assertEquals("What's happening", state.value.currentVibe)
        assertEquals(0, backCalls)
        assertEquals(0, parentBackCalls)
        composeRule.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        assertEquals(0f, composeRule.onNodeWithTag("explore_topic_layer").getUnclippedBoundsInRoot().left.value, 0.1f)
        composeRule.onNodeWithText("Topic show 0").assertIsDisplayed()
        assertEquals(0, backCalls)
    }

    @Test fun `right-edge predictive commit returns once to the saved Explore feed`() {
        show()
        composeRule.onNodeWithTag("explore_feed_1").performScrollToIndex(12)
        val original = scrollValue()
        composeRule.runOnIdle { openTopic() }
        composeRule.onNodeWithText("Topic show 0").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        startBackGesture(BackEventCompat.EDGE_RIGHT, 0.6f)
        assertTrue(composeRule.onNodeWithTag("explore_topic_layer").getUnclippedBoundsInRoot().left.value < -150f)
        composeRule.runOnUiThread { backDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(500)
        applyFrame()
        composeRule.onNodeWithTag("explore_topic_layer").assertDoesNotExist()
        assertEquals(original, scrollValue(), 0.002f)
        assertEquals(1, backCalls)
        assertEquals(0, parentBackCalls)
    }

    @Test fun `toolbar and repeated system back finish the slide before leaving the topic`() {
        openTopic()
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("explore_topic_back").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.waitForIdle()
        val topic = composeRule.onNodeWithTag("explore_topic_layer").getUnclippedBoundsInRoot()
        assertTrue(topic.left.value > 0f && topic.left.value < 360f)
        assertEquals(0, backCalls)
        composeRule.runOnUiThread { backDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(400)
        applyFrame()
        assertEquals(1, backCalls)
        assertEquals(0, parentBackCalls)
        composeRule.onNodeWithText("Recommendation 0").assertIsDisplayed()
    }

    @Test fun `long topic and enlarged RTL text do not overlap the cards or hide back`() {
        openTopic()
        val title = "Ideas about science, history and the stories changing the world"
        state.value = state.value.copy(currentVibe = title)
        show(rtl = true, fontScale = 1.6f)
        composeRule.onNodeWithTag("explore_topic_back").assertIsDisplayed()
        val heading = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val cardTitle = composeRule.onNodeWithText("Topic show 0").fetchSemanticsNode().boundsInRoot
        assertTrue(heading.bottom < cardTitle.top)
        assertTrue(heading.left >= 16f && heading.right <= 344f)
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    private fun openTopic(title: String = "What's happening") {
        state.value = state.value.copy(currentVibe = title, isSearching = true, searchResults = topicShows)
    }

    private fun startBackGesture(edge: Int, progress: Float) {
        composeRule.runOnUiThread {
            backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, edge))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnUiThread {
            backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, edge))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun applyFrame() {
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun show(rtl: Boolean = false, fontScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(1f, fontScale),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    BackHandler { parentBackCalls++ }
                    ExploreContent(
                        uiState = state.value,
                        onSearchQueryChanged = { state.value = state.value.copy(searchQuery = it) },
                        onCategorySelected = {},
                        onPodcastClick = { id, entry, category, index -> taps.add(listOf(id, entry, category, index)) },
                        onEpisodeClick = { _, _ -> },
                        onTabSelected = { state.value = state.value.copy(selectedTab = it) },
                        onVibeSelected = { id, title ->
                            topicSelections.add(id to title)
                            openTopic(title)
                        },
                        onClearVibe = {
                            backCalls++
                            state.value = state.value.copy(currentVibe = null, isSearching = false, searchResults = emptyList())
                        },
                    )
                }
            }
        }
    }

    private fun scrollValue(): Float = composeRule.onNodeWithTag("explore_feed_1")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
}
