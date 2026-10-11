package cx.aswin.boxlore.feature.explore

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.explore.components.ExploreBrowseChromeState
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodesSearchIdleState
import cx.aswin.boxlore.feature.explore.components.ExploreFloatingHeader
import cx.aswin.boxlore.feature.explore.components.SearchTabSelector
import cx.aswin.boxlore.feature.explore.logic.ConceptSearchIdleLogic
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
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
class ExploreSearchLandingTest {
    @get:Rule val composeRule = createComposeRule()
    private val state = mutableStateOf(
        ExploreUiState.Success(
        selectedTab = 1,
        suggestedVibes = listOf("morning_news" to "What's happening", "afternoon_learn" to "Something to learn"),
    )
    )
    private val topics = mutableListOf<Pair<String, String>>()
    private val queries = mutableListOf<String>()

    @Test fun `opening search shows topic suggestions and preserves their destination`() {
        show()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNodeWithText("Explore collections").assertIsDisplayed()
        composeRule.onNodeWithText("Browse shows by topic.").assertDoesNotExist()
        composeRule.onNodeWithText("Something to learn").performClick()
        assertEquals(listOf("afternoon_learn" to "Something to learn"), topics)
        assertEquals("Something to learn", state.value.currentVibe)
        composeRule.onNodeWithTag("explore_topic_toolbar").assertIsDisplayed()
    }

    @Test fun `missing suggestions still explain how to find a show`() {
        state.value = state.value.copy(suggestedVibes = emptyList())
        show()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNodeWithText("Search for a show").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a show name in the search field.").assertIsDisplayed()
        composeRule.onNodeWithText("Explore collections").assertDoesNotExist()
    }

    @Test fun `both search headings and their hints align to the center of the feed`() {
        show()
        composeRule.onNode(hasSetTextAction()).performClick()
        assertCentered("Explore collections")
        composeRule.onNodeWithText("Browse shows by topic.").assertDoesNotExist()
        composeRule.onNode(hasText("Ask anything") and isSelectable()).performClick()
        assertCentered("Try a question")
        assertCentered("Find shows and episodes about an idea.")
    }

    @Test fun `search switcher has clearance and fully painted capsule edges inside the search container`() {
        checkSearchCapsule(fontScale = 1f, direction = LayoutDirection.Ltr)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h900dp-mdpi")
    fun `search switcher keeps both mode edges at narrow width with enlarged RTL text`() {
        checkSearchCapsule(fontScale = 1.6f, direction = LayoutDirection.Rtl)
    }

    @Test fun `empty browse and empty search give guidance for their actual controls`() {
        state.value = state.value.copy(selectedTab = 0)
        show()
        composeRule.onNodeWithText("No shows to browse").assertIsDisplayed()
        composeRule.onNodeWithText("Try another genre, or search for a show.").assertIsDisplayed()
        composeRule.runOnIdle { state.value = state.value.copy(searchQuery = "no matches", isSearching = true) }
        composeRule.onNodeWithText("No shows found").assertIsDisplayed()
        composeRule.onNodeWithText("Try a different name or check the spelling.").assertIsDisplayed()
        composeRule.onNodeWithText("No shows to browse").assertDoesNotExist()
    }

    @Test fun `switching search mode exposes prompts and sends the exact query`() {
        show()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasText("Ask anything") and isSelectable()).performClick()
        composeRule.onNodeWithText("Try a question").assertIsDisplayed()
        composeRule.onNodeWithText("Find shows and episodes about an idea.").assertIsDisplayed()
        composeRule.onNodeWithTag("concept_suggestion_0").performClick()
        assertEquals(listOf(ConceptSearchIdleLogic.examples.first().query), queries)
        assertEquals(SearchTab.EPISODES, state.value.searchTab)
    }

    @Test fun `no concept matches retain actionable questions`() {
        state.value = state.value.copy(searchQuery = "unmatched idea", searchTab = SearchTab.EPISODES, hasPerformedSemanticSearch = true)
        show()
        composeRule.onNodeWithText("Nothing matched that idea").assertIsDisplayed()
        composeRule.onNodeWithTag("concept_suggestion_1").performClick()
        assertEquals(listOf(ConceptSearchIdleLogic.examples[1].query), queries)
    }

    @Test fun `both search loading states have bounded height and accessible status`() {
        state.value = state.value.copy(searchQuery = "search term", isLoading = true, isSearching = true)
        show()
        composeRule.onNodeWithText("Searching for shows").assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag("explore_search_loading").getUnclippedBoundsInRoot()
        assertTrue((bounds.bottom - bounds.top).value < 120f)
        assertEquals(LiveRegionMode.Polite, composeRule.onNodeWithTag("explore_search_loading").fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        composeRule.runOnIdle { state.value = state.value.copy(searchTab = SearchTab.EPISODES, isSemanticLoading = true) }
        composeRule.onNodeWithText("Finding shows and episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Searching for shows").assertDoesNotExist()
    }

    @Test fun `connected prompts preserve order callbacks and minimum targets at large RTL text`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoxLoreTheme(dynamicColor = false) {
                    ExploreEpisodesSearchIdleState(onExampleClick = { queries.add(it) })
                }
            }
        }
        var previousBottom = 0f
        ConceptSearchIdleLogic.examples.forEachIndexed { index, example ->
            val node = composeRule.onNodeWithTag("concept_suggestion_$index")
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.top.value >= previousBottom)
            assertTrue((bounds.bottom - bounds.top).value >= 64f)
            assertTrue(bounds.left.value >= 0f && bounds.right.value <= 360f)
            previousBottom = bounds.bottom.value
            composeRule.onNodeWithText(example.label).performClick()
        }
        assertEquals(ConceptSearchIdleLogic.examples.map { it.query }, queries)
    }

    private fun assertCentered(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertEquals(180f, (bounds.left.value + bounds.right.value) / 2f, 1f)
    }

    private fun checkSearchCapsule(fontScale: Float, direction: LayoutDirection) {
        val selected = mutableStateOf(SearchTab.SHOWS)
        lateinit var view: View
        var selectedColor = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalLayoutDirection provides direction) {
                BoxLoreTheme(dynamicColor = false) {
                    view = LocalView.current
                    selectedColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
                    Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                        ExploreFloatingHeader(
                            state = remember { ExploreBrowseChromeState() },
                            onExpandedHeightChanged = {},
                            search = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                            selectors = {
                                Spacer(Modifier.height(12.dp))
                                SearchTabSelector(selected.value, onTabSelected = { selected.value = it })
                                Spacer(Modifier.height(8.dp))
                            },
                        )
                    }
                }
            }
        }
        val parent = composeRule.onNodeWithTag("explore_header_container").getUnclippedBoundsInRoot()
        val tabs = composeRule.onNodeWithTag("explore_search_switcher").getUnclippedBoundsInRoot()
        assertTrue(tabs.left.value - parent.left.value >= 6f)
        assertTrue(parent.right.value - tabs.right.value >= 6f)
        assertTrue(parent.bottom.value - tabs.bottom.value >= 14f)
        listOf("Find a show", "Ask anything").forEach { label ->
            composeRule.onNode(hasText(label) and isSelectable()).performClick()
            composeRule.waitForIdle()
            val bounds = composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot()
            composeRule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val radius = (bounds.bottom.value - bounds.top.value) / 2f
                val centerY = (bounds.top.value + bounds.bottom.value) / 2f
                for (dy in listOf(-radius * 0.7f, 0f, radius * 0.7f)) {
                    val inset = radius - sqrt(radius * radius - dy * dy) + 2f
                    val y = (centerY + dy).roundToInt()
                    for (x in listOf((bounds.left.value + inset).roundToInt(), (bounds.right.value - inset).roundToInt())) {
                        val actual = bitmap.getPixel(x, y)
                        // Native rendering can round a color channel by one; a clipped edge has a different surface color.
                        for (shift in listOf(24, 16, 8, 0)) {
                            assertTrue("$label capsule edge remains visible", abs(((selectedColor ushr shift) and 255) - ((actual ushr shift) and 255)) <= 1)
                        }
                    }
                }
                bitmap.recycle()
            }
        }
    }

    private fun show() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                ExploreContent(
                    uiState = state.value,
                    onSearchQueryChanged = {
                        queries.add(it)
                        state.value = state.value.copy(searchQuery = it)
                    },
                    onCategorySelected = {},
                    onPodcastClick = { _, _, _, _ -> },
                    onEpisodeClick = { _, _ -> },
                    onTabSelected = { state.value = state.value.copy(selectedTab = it) },
                    onSearchTabSelected = { state.value = state.value.copy(searchTab = it) },
                    onVibeSelected = { id, title ->
                        topics.add(id to title)
                        state.value = state.value.copy(currentVibe = title)
                    },
                    onClearVibe = { state.value = state.value.copy(currentVibe = null) },
                )
            }
        }
    }
}
