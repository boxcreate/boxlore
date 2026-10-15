package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreTabletFeaturedTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))
    private val taps = mutableListOf<Pair<String, Int?>>()

    @Test
    fun `tablet charts start the featured card on the left with ranked neighbors alongside`() {
        render(ExploreUiState.Success(trending = List(16) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }, selectedTab = 0))
        assertLeadingRow()
        fixture.compose.onNodeWithText("Show 0").performClick()
        fixture.compose.onNodeWithText("Show 1").performClick()
        assertEquals(listOf("0" to 0, "1" to 1), taps)
    }

    @Test
    fun `tablet recommendations keep the first episode and subsequent episodes on the same leading row`() {
        render(ExploreUiState.Success(recommendations = List(16) { TestFixtures.episode(id = "$it", title = "Episode $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }, selectedTab = 1))
        assertLeadingRow()
        fixture.compose.onNodeWithText("Episode 0").performClick()
        fixture.compose.onNodeWithText("Episode 1").performClick()
        assertEquals(listOf("0" to null, "1" to null), taps)
    }

    @Test fun `tablet search rows preserve visual rank and fill the search field`() {
        render(ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = List(15) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }))
        assertVisualRank()
        val clear = fixture.compose.onNodeWithContentDescription("Clear search").fetchSemanticsNode().boundsInRoot
        assertTrue("Clear follows the search field trailing edge", clear.left > 1180f)
        fixture.compose.onNodeWithText("Show 14").performClick()
        assertEquals(listOf("14" to 14), taps)
    }

    @Test fun `tablet topic rows preserve visual rank and original tap indices`() {
        render(ExploreUiState.Success(currentVibe = "What's happening", isSearching = true, searchResults = List(15) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }))
        assertVisualRank()
        fixture.compose.onNodeWithText("Show 14").performClick()
        assertEquals(listOf("14" to 14), taps)
    }

    private fun assertVisualRank() {
        val visualOrder = (0..14).map { index -> index to fixture.compose.onNodeWithText("Show $index").fetchSemanticsNode().boundsInRoot }
            .sortedWith(compareBy({ it.second.top.toInt() }, { it.second.left }))
            .map { it.first }
        assertEquals((0..14).toList(), visualOrder)
    }

    private fun assertLeadingRow() {
        val hero = fixture.compose.onNodeWithTag("tablet_featured_card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val neighbors = fixture.compose.onNodeWithTag("tablet_featured_neighbors", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(16f, hero.left, 1f)
        assertEquals(hero.top, neighbors.top, 1f)
        assertEquals(hero.bottom, neighbors.bottom, 1f)
        val artwork = fixture.compose.onNodeWithTag("feed_featured_artwork", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(hero.height, artwork.height, 1f)
        assertEquals(12f, neighbors.left - hero.right, 1f)
        assertTrue(hero.width <= 480f)
        assertTrue(neighbors.right <= 1_264f)
    }

    private fun render(state: ExploreUiState.Success) {
        fixture.prepare()
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1280f, 800f), LocalBoxlorePaneWidth provides 1280f) {
            BoxLoreTheme(dynamicColor = false) {
                ExploreContent(state, onSearchQueryChanged = {}, onCategorySelected = {}, onPodcastClick = { id, _, _, position -> taps += id to position }, onEpisodeClick = { episode, _ -> taps += episode.id to null }, onTabSelected = {}, onVibeSelected = { _, _ -> }, onClearVibe = {})
            }
            }
        }
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
