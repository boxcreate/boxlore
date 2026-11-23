package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test fun `tablet search rows preserve visual rank and the search box stops at a readable width`() {
        render(ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = List(15) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }))
        assertVisualRank()
        val clear = fixture.compose.onNodeWithContentDescription("Clear search").fetchSemanticsNode().boundsInRoot
        assertTrue("Clear sits at the end of a box no wider than 720dp, not at ${clear.right}", clear.right in 600f..760f)
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

    @Test
    fun `Top numbers its shows in chart order and only Top does`() {
        render(ExploreUiState.Success(trending = List(16) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }, selectedTab = 0))
        fixture.compose.onNodeWithContentDescription("Number 1, Show 0", useUnmergedTree = true).assertExists()
        fixture.compose.onNodeWithContentDescription("Number 3, Show 2", useUnmergedTree = true).assertExists()
        val ranked = (0..5).map { fixture.compose.onNodeWithContentDescription("Number ${it + 1}, Show $it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        // The featured show leads, then its neighbours read left to right, top to bottom.
        val neighbours = ranked.drop(1)
        assertEquals(neighbours, neighbours.sortedWith(compareBy({ it.top }, { it.left })))
        assertTrue("the featured show is first", ranked[0].left < neighbours.minOf { it.left })
    }

    @Test
    fun `search results carry no chart numbers`() {
        render(ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = List(6) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }))
        assertEquals(0, fixture.compose.onAllNodesWithTag(cx.aswin.boxlore.feature.explore.components.EXPLORE_RANK_BADGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `genre tags show translated names and never the Podcast placeholder`() {
        val genres = listOf("Podcast", "", "Comedy", "Society & Culture", "Podcasts")
        render(ExploreUiState.Success(trending = List(10) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL, genre = genres[it % genres.size]) }, selectedTab = 0))
        val context = fixture.compose.activity
        assertTrue(fixture.compose.onAllNodesWithText(context.getString(cx.aswin.boxlore.core.catalog.R.string.auto_genre_comedy), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(fixture.compose.onAllNodesWithText(context.getString(cx.aswin.boxlore.core.catalog.R.string.auto_genre_society), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, fixture.compose.onAllNodesWithText("Podcast", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, fixture.compose.onAllNodesWithText("Podcasts", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = "de")
    fun `genre tags follow the app's language`() {
        render(ExploreUiState.Success(trending = List(4) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL, genre = "Society & Culture") }, selectedTab = 0))
        val german = fixture.compose.activity.getString(cx.aswin.boxlore.core.catalog.R.string.auto_genre_society)
        assertTrue("German name $german", german != "Society & Culture")
        assertTrue(fixture.compose.onAllNodesWithText(german, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
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
