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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_443
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_475
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE_LAND
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W599_H900
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W900_H479
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W900_H480
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreGridResizeTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 960, false))
    private val window = mutableStateOf(BoxloreWindowLayout(599f, 800f))
    private val shows = List(120) {
        TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL)
    }
    private val taps = mutableListOf<List<Any?>>()

    @Test
    fun `show search retains the visible result across live compact and tablet columns`() {
        render(ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = shows))
        fixture.compose.onNodeWithTag("explore_feed_1").performScrollToIndex(36)
        assertAnchorAcrossWindowChanges("Show 36")
        fixture.compose.onNodeWithText("Show 36").performClick()
        assertEquals(listOf(listOf("36", "explore_search", "All", 36)), taps)
    }

    @Test
    fun `topic retains the visible result and absolute tap rank through resizing`() {
        render(ExploreUiState.Success(currentVibe = "What's happening", isSearching = true, searchResults = shows))
        fixture.compose.onNodeWithTag("explore_feed_1").performScrollToIndex(37)
        assertAnchorAcrossWindowChanges("Show 36")
        fixture.compose.onNodeWithText("Show 36").performClick()
        assertEquals(listOf(listOf("36", "explore_vibe", "All", 36)), taps)
    }

    @Test
    fun `browse uses the app's poster columns and keeps its place across compact and tablet windows`() {
        window.value = PHONE
        render(ExploreUiState.Success(trending = shows, selectedTab = 0, hasForYou = false))
        fixture.compose.onNodeWithTag("explore_feed_0").performScrollToIndex(30)
        val steps = listOf(
            PHONE to 2, COVER_443 to 2, COVER_475 to 2, BoxloreWindowLayout(540f, 860f) to 3, W599_H900 to 3,
            PHONE_LAND to 5, PHONE to 2, W900_H479 to 5, W900_H480 to null, W900_H479 to 5, PHONE to 2,
        )
        for ((size, columns) in steps) {
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            // Index 30 is "Show 29" (the heading and the featured first show come before the grid).
            fixture.compose.onNodeWithText("Show 29").assertIsDisplayed()
            if (columns != null) assertEquals("$size", columns, postersInRowOf("Show 29"))
        }
    }

    /** How many posters share the row of [title], counting the visible shows around it. */
    private fun postersInRowOf(title: String): Int {
        val top = fixture.compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        return (20..45).count { index ->
            val nodes = fixture.compose.onAllNodesWithText("Show $index").fetchSemanticsNodes()
            nodes.any { kotlin.math.abs(it.boundsInRoot.top - top) < 1f }
        }
    }

    private fun assertAnchorAcrossWindowChanges(title: String) {
        fixture.compose.onNodeWithText(title).assertIsDisplayed()
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
            fixture.compose.onNodeWithText(title).assertIsDisplayed()
        }
    }

    private fun render(state: ExploreUiState.Success) {
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
                            onPodcastClick = { id, entry, category, position -> taps += listOf(id, entry, category, position) },
                            onEpisodeClick = { _, _ -> },
                            onTabSelected = {},
                            onVibeSelected = { _, _ -> },
                            onClearVibe = {},
                        )
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }
}
