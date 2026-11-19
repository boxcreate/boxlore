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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A read that failed never shows as "nothing there": failed show searches, Top charts and chart
 * pages show Try again, which survives live compact and tablet window changes and asks once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreLoadFailureTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))
    private val window = mutableStateOf(BoxloreWindowLayout(599f, 800f))
    private val retries = mutableListOf<ExploreRetry>()
    private val shows = List(30) {
        TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL)
    }

    @Test
    fun `a failed show search offers Try again instead of no matches`() {
        render(ExploreUiState.Success(searchQuery = "darknet diaries", isSearching = true, showSearchFailed = true))
        assertAcrossWindowChanges("Couldn’t search right now")
        fixture.compose.onNodeWithText("No shows found").assertDoesNotExist()
        fixture.compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf(ExploreRetry.SHOW_SEARCH), retries)
    }

    @Test
    fun `a show search that was read and found nothing still says no matches`() {
        render(ExploreUiState.Success(searchQuery = "zzzz", isSearching = true))
        fixture.compose.onNodeWithText("No shows found").assertIsDisplayed()
        fixture.compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `a failed Top chart offers Try again instead of no shows to browse`() {
        render(ExploreUiState.Success(selectedTab = 0, hasForYou = false, topChartFailed = true))
        assertAcrossWindowChanges("Couldn’t load shows")
        fixture.compose.onNodeWithText("No shows to browse").assertDoesNotExist()
        fixture.compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf(ExploreRetry.TOP_CHART), retries)
    }

    @Test
    fun `a failed chart page keeps the chart and offers Try again at its end`() {
        render(ExploreUiState.Success(selectedTab = 0, hasForYou = false, trending = shows, hasMore = true, nextTopChartPageFailed = true))
        fixture.compose.onNodeWithTag("explore_feed_0").performScrollToNode(hasText("Couldn’t load more shows"))
        assertAcrossWindowChanges("Couldn’t load more shows")
        // Scrolled to the very end, as a listener does, the footer sits clear of the tab switcher.
        fixture.compose.onNodeWithTag(feedTag).performTouchInput { repeat(4) { swipeUp() } }
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Try again").assertIsDisplayed().performClick()
        assertEquals(listOf(ExploreRetry.NEXT_TOP_CHART_PAGE), retries)
    }

    private fun assertAcrossWindowChanges(text: String) {
        fixture.compose.onNodeWithText(text).assertIsDisplayed()
        listOf(
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(840f, 800f),
            BoxloreWindowLayout(1_280f, 800f),
            BoxloreWindowLayout(840f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(599f, 800f),
        ).forEach { size ->
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            fixture.compose.onNodeWithTag(feedTag).performScrollToNode(hasText(text))
            fixture.compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    private var feedTag = "explore_feed_1"

    private fun render(state: ExploreUiState.Success) {
        feedTag = if (state.isSearching) "explore_feed_1" else "explore_feed_0"
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.setContent {
            val size = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        ExploreContent(
                            state,
                            onSearchQueryChanged = {},
                            onCategorySelected = {},
                            onPodcastClick = { _, _, _, _ -> },
                            onEpisodeClick = { _, _ -> },
                            onTabSelected = {},
                            onVibeSelected = { _, _ -> },
                            onClearVibe = {},
                            onRetry = { retries += it },
                        )
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }
}
