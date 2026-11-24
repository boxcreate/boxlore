package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.FOLD_FLAT
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET_PORTRAIT
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.UPRIGHT_BOOK
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.explore.components.EXPLORE_RANK_BADGE_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `Top numbers its shows in chart order and reads each number once`() {
        render(ExploreUiState.Success(trending = chart(16), selectedTab = 0))
        // Merged as a screen reader hears them: "Number 1, Show 0", never the title twice.
        val ranked = (0..8).map { index ->
            val node = fixture.compose.onNode(hasContentDescription("Number ${index + 1}") and hasText("Show $index")).fetchSemanticsNode()
            assertEquals(listOf("Number ${index + 1}"), node.config.getOrNull(SemanticsProperties.ContentDescription))
            node.boundsInRoot
        }
        // The featured show leads, then the chart reads left to right, top to bottom.
        val rows = ranked.drop(1)
        assertEquals(rows, rows.sortedWith(compareBy({ it.top }, { it.left })))
        assertTrue("the featured show leads the first row", ranked[0].left <= rows.minOf { it.left } + 1f && ranked[0].top <= rows.minOf { it.top } + 1f)
    }

    @Test
    fun `tablet Top shows ranked rows in two or three columns and phones keep numbered posters`() {
        val columns = mapOf(TABLET to 3, FOLD_FLAT to 3, TABLET_PORTRAIT to 2, UPRIGHT_BOOK to 2)
        for ((size, expected) in columns) {
            render(ExploreUiState.Success(trending = chart(16), selectedTab = 0), size)
            // Rows from number 6 on sit under the featured card, one chart column each.
            val lefts = (6..11).map { rank -> fixture.compose.onNode(hasContentDescription("Number $rank")).fetchSemanticsNode().boundsInRoot.left.toInt() }.toSet()
            assertEquals("$size", expected, lefts.size)
            fixture.compose.onNodeWithText("Show 0").performClick()
            fixture.compose.onNode(hasContentDescription("Number 7")).performClick()
            assertEquals(listOf("0" to 0, "6" to 6), taps)
            taps.clear()
        }
        render(ExploreUiState.Success(trending = chart(16), selectedTab = 0), PHONE)
        assertEquals(0, fixture.compose.onAllNodesWithTag("tablet_featured_neighbors", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(listOf("Number 3"), fixture.compose.onNode(hasText("Show 2") and hasClickAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `the featured number sits on the artwork, never over the title, and taps open the show`() {
        val long = chart(8).toMutableList().also { it[0] = it[0].copy(title = ExploreTabletAuditCaptureTest.LONG_SHOW) }
        for ((size, scale) in listOf(PHONE to 1f, PHONE to 1.3f, BoxloreWindowLayout(600f, 480f) to 1f, TABLET to 1f)) {
            render(ExploreUiState.Success(trending = long, selectedTab = 0), size, scale)
            val badge = fixture.compose.onAllNodesWithTag(EXPLORE_RANK_BADGE_TAG, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
            val title = fixture.compose.onNodeWithText(ExploreTabletAuditCaptureTest.LONG_SHOW, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertFalse("$size@$scale badge $badge over title $title", badge.overlaps(title))
            fixture.compose.onAllNodesWithTag(EXPLORE_RANK_BADGE_TAG, useUnmergedTree = true)[0].performTouchInput { click(center) }
            assertEquals("$size@$scale", listOf("0" to 0), taps)
            taps.clear()
        }
    }

    @Test
    fun `search results carry no chart numbers`() {
        render(ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = List(6) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }))
        assertEquals(0, fixture.compose.onAllNodesWithTag(EXPLORE_RANK_BADGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
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

    private fun chart(count: Int) = List(count) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }

    private val window = mutableStateOf(TABLET)
    private val fontScale = mutableFloatStateOf(1f)
    private var shown: androidx.compose.runtime.MutableState<ExploreUiState.Success>? = null

    /** Shows [state] at [size]; later calls change the live window, text size and state in place. */
    private fun render(state: ExploreUiState.Success, size: BoxloreWindowLayout = TABLET, scale: Float = 1f) {
        shown?.let { current ->
            fixture.compose.runOnIdle {
                window.value = size
                fontScale.floatValue = scale
                current.value = state
            }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.mainClock.advanceTimeBy(1_600)
            fixture.compose.waitForIdle()
            return
        }
        window.value = size
        fontScale.floatValue = scale
        val current = mutableStateOf(state).also { shown = it }
        fixture.prepare()
        fixture.setContent {
            val live = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides live, LocalBoxlorePaneWidth provides live.widthDp) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
                    Box(Modifier.width(live.widthDp.dp).height(live.heightDp.dp)) {
                        BoxLoreTheme(dynamicColor = false) {
                            ExploreContent(current.value, onSearchQueryChanged = {}, onCategorySelected = {}, onPodcastClick = { id, _, _, position -> taps += id to position }, onEpisodeClick = { episode, _ -> taps += episode.id to null }, onTabSelected = {}, onVibeSelected = { _, _ -> }, onClearVibe = {})
                        }
                    }
                }
            }
        }
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
