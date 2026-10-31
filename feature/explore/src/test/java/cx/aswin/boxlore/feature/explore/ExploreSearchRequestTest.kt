package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tapping the Explore tab again on the Explore page opens search, on every window size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w1280dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreSearchRequestTest {
    @get:Rule val composeRule = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 800f))
    private val state = mutableStateOf(
        ExploreUiState.Success(
            selectedTab = 1,
            suggestedVibes = listOf("morning_news" to "What's happening"),
            trending = List(12) { Podcast("show_$it", "Show $it", "Host $it", "") },
        ),
    )
    private val searchRequested = mutableStateOf(false)
    private var handled = 0
    private var clearedTopics = 0

    @Test fun `request on browse opens focused search once`() {
        show()
        composeRule.onNodeWithTag("explore_browse_pager").assertIsDisplayed()
        request()
        composeRule.onNode(hasText("Find a show") and isSelectable()).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNodeWithTag("explore_browse_pager").assertDoesNotExist()
        assertEquals(1, handled)
        assertFalse(searchRequested.value)
        assertEquals(0, clearedTopics)
    }

    @Test fun `request from a topic leaves it for focused search`() {
        state.value = state.value.copy(currentVibe = "What's happening", isSearching = true)
        show()
        composeRule.onNodeWithTag("explore_topic_toolbar").assertIsDisplayed()
        request()
        composeRule.onNodeWithTag("explore_topic_toolbar").assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(1, clearedTopics)
        assertEquals(1, handled)
    }

    @Test fun `request while searching keeps the query and refocuses the field`() {
        state.value = state.value.copy(searchQuery = "space", isSearching = true)
        show()
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
        request()
        composeRule.onNode(hasSetTextAction()).assertIsFocused().assertTextContains("space")
        assertEquals(1, handled)
    }

    @Test fun `opened search survives live resizing and a later request still focuses it`() {
        show()
        request()
        composeRule.runOnIdle { state.value = state.value.copy(searchQuery = "space", isSearching = true) }
        for (size in listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(1_280f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
        )) {
            composeRule.runOnIdle { window.value = size }
            composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
            composeRule.waitForIdle()
            composeRule.onNode(hasText("Find a show") and isSelectable()).assertIsDisplayed()
            composeRule.onNode(hasSetTextAction()).assertTextContains("space")
            composeRule.onNodeWithTag("explore_browse_pager").assertDoesNotExist()
        }
        composeRule.runOnIdle { window.value = BoxloreWindowLayout(1_280f, 800f) }
        composeRule.waitForIdle()
        request()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(2, handled)
    }

    private fun request() {
        composeRule.runOnIdle { searchRequested.value = true }
        composeRule.waitForIdle()
    }

    private fun show() {
        composeRule.setContent {
            val size = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        ExploreContent(
                            uiState = state.value,
                            onSearchQueryChanged = { state.value = state.value.copy(searchQuery = it) },
                            onCategorySelected = {},
                            onPodcastClick = { _, _, _, _ -> },
                            onEpisodeClick = { _, _ -> },
                            onTabSelected = { state.value = state.value.copy(selectedTab = it) },
                            onVibeSelected = { _, _ -> },
                            onClearVibe = {
                                clearedTopics++
                                state.value = state.value.copy(currentVibe = null, isSearching = false)
                            },
                            searchRequested = searchRequested.value,
                            onSearchRequestHandled = {
                                handled++
                                searchRequested.value = false
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
