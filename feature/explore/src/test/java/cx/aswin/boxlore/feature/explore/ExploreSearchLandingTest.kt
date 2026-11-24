package cx.aswin.boxlore.feature.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.explore.components.ExploreBrowseChromeState
import cx.aswin.boxlore.feature.explore.components.ExploreEpisodesSearchIdleState
import cx.aswin.boxlore.feature.explore.components.ExploreFloatingHeader
import cx.aswin.boxlore.feature.explore.components.SearchTabSelector
import cx.aswin.boxlore.feature.explore.components.conceptExampleIcon
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

    @Test fun `the search box hint says what to type in each mode`() {
        show()
        fun hint(text: String) = composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue("browsing", hint("Search podcasts"))
        composeRule.onNode(hasSetTextAction()).performClick()
        assertTrue("Find a show", hint("Search by show name"))
        composeRule.onNode(hasText("Ask anything") and isSelectable()).performClick()
        assertTrue("Ask anything", hint("Ask about any topic"))
        assertTrue("no second Ask anything in the box", !hint("Search podcasts"))
    }

    @Test fun `each example has its own icon and none looks like the Ask anything tab`() {
        val icons = ConceptSearchIdleLogic.examples.indices.map { conceptExampleIcon(it) }
        assertEquals(icons.size, icons.toSet().size)
        assertTrue(Icons.Rounded.AutoAwesome !in icons)
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
            composeRule.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(example.label)).performClick()
        }
        assertEquals(ConceptSearchIdleLogic.examples.map { it.query }, queries)
    }

    @Test fun `the hint fits on one line at large text in English`() = assertHintsFit()

    @Test
    @Config(sdk = [33], qualifiers = "de-w360dp-h900dp-mdpi")
    fun `the hint fits on one line at large text in German`() = assertHintsFit()

    @Test
    @Config(sdk = [33], qualifiers = "fr-w360dp-h900dp-mdpi")
    fun `the hint fits on one line at large text in French`() = assertHintsFit()

    @Test
    @Config(sdk = [33], qualifiers = "pt-rBR-w360dp-h900dp-mdpi")
    fun `the hint fits on one line at large text in Brazilian Portuguese`() = assertHintsFit()

    /** At 360dp and 130% text, both search modes' hints show in full. */
    private fun assertHintsFit() {
        show(fontScale = 1.3f, window = BoxloreWindowLayout(360f, 900f))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNode(hasSetTextAction()).performClick()
        assertFits(context.getString(R.string.explore_search_placeholder_shows))
        composeRule.onNode(hasText(context.getString(R.string.explore_search_episodes)) and isSelectable()).performClick()
        assertFits(context.getString(R.string.explore_search_placeholder_questions))
    }

    private fun assertFits(hint: String) {
        val node = composeRule.onNode(hasText(hint), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals("$hint stays on one line", 1, layout.lineCount)
        assertTrue("$hint isn't cut off", !layout.isLineEllipsized(0) && layout.getLineRight(0) <= layout.size.width + 0.5f)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1600dp-h1000dp-mdpi")
    fun `tablet examples sit in two columns under the search box, never past its end`() {
        val window = mutableStateOf(BoxloreTestWindows.TABLET)
        show(windowState = window)
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasText("Ask anything") and isSelectable()).performClick()
        for ((size, columns) in listOf(BoxloreTestWindows.TABLET to 2, BoxloreTestWindows.DESKTOP to 2, BoxloreTestWindows.PHONE to 1, BoxloreTestWindows.TABLET to 2)) {
            composeRule.runOnIdle { window.value = size }
            composeRule.waitForIdle()
            val rows = (0 until 6).map { composeRule.onNodeWithTag("concept_suggestion_$it").getUnclippedBoundsInRoot() }
            assertEquals("$size", columns, rows.map { it.left.value.roundToInt() }.toSet().size)
            val field = composeRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
            // The field sits 6dp inside the header's rounded container; the examples line up with the container.
            assertTrue("$size: examples end by the search box's end", rows.maxOf { it.right.value } <= field.right.value + 8f)
            assertTrue("$size: examples start under the search box", rows.minOf { it.left.value } >= field.left.value - 8f)
        }
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

    private fun show(
        fontScale: Float = 1f,
        window: BoxloreWindowLayout = BoxloreWindowLayout.Unspecified,
        windowState: androidx.compose.runtime.State<BoxloreWindowLayout>? = null,
    ) {
        composeRule.setContent {
            val size = windowState?.value ?: window
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
            ) {
            BoxLoreTheme(dynamicColor = false) {
                // A given window is the space the page gets; the unspecified one keeps the host's size.
                val space = if (size == BoxloreWindowLayout.Unspecified) Modifier else Modifier.requiredSize(size.widthDp.dp, size.heightDp.dp)
                Box(space) {
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
    }
}
