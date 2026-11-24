package cx.aswin.boxlore.feature.briefing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.BriefingSource
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Brief across window sizes, postures, side insets and states, with the same live state owner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BriefingAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window: MutableState<BoxloreWindowLayout> = mutableStateOf(BoxloreTestWindows.PHONE)
    private val pane: MutableState<BoxloreLayoutBounds?> = mutableStateOf(null)
    private val state: MutableState<BriefingUiState> = mutableStateOf(edition())
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val fontScale = mutableStateOf(1f)
    private val regions = mutableListOf<String>()
    private val defaults = mutableListOf<String>()
    private var backs = 0

    private fun render(bottomContentPadding: Dp = 0.dp) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides window.value,
                LocalBoxlorePaneBounds provides pane.value,
                LocalLayoutDirection provides direction.value,
                LocalDensity provides Density(density.density, fontScale.value),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        BriefingScreen(
                            uiState = state.value,
                            onBackClick = { backs++ },
                            onRegionSelect = { regions += it },
                            onPlayPauseClick = { _, _ -> },
                            onSeekTo = {},
                            onEpisodeClick = {},
                            onFeedbackClick = {},
                            bottomContentPadding = bottomContentPadding,
                            defaultEdition = "global",
                            onDefaultEditionSelect = { defaults += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the error page scrolls Try again above the mini player in short windows and retries once`() {
        state.value = BriefingUiState.Error(LONG_ERROR, "in")
        render(bottomContentPadding = 160.dp)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.COVER_443, BoxloreTestWindows.PHONE) { size ->
            scrollToEnd(compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("briefingError"))))
            val retry = compose.onNodeWithText("Try again").bounds()
            assertTrue("$size: Try again ends at ${retry.bottom}", retry.bottom <= size.heightDp - 160f)
            assertTrue("$size: still the error page", state.value is BriefingUiState.Error)
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("in"), regions)
    }

    @Test fun `on a sideways phone Back and Preferences clear the cutout and side buttons while the cover reaches the window's edges`() {
        render()
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.COVER_LAND)) {
            window.value = size
            compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
            compose.waitForIdle()
            val frame = compose.onNodeWithTag("briefing_reading_frame").bounds()
            val back = compose.onNodeWithTag("briefingBack").bounds()
            val preferences = compose.onNodeWithContentDescription("Brief preferences").bounds()
            val cover = compose.onNodeWithTag("briefingPageCover").bounds()
            val logo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).bounds()
            assertTrue("$size: Back starts at ${back.left - frame.left} in the frame", back.left - frame.left >= 32f)
            assertTrue("$size: Preferences ends ${frame.right - preferences.right} from the frame's end", frame.right - preferences.right >= 48f)
            // The page is the window here (the test window is the root's size): the cover is full-bleed.
            assertEquals(size.widthDp, frame.width, 0.5f)
            assertEquals(frame.left, cover.left, 0.5f)
            assertEquals(frame.right, cover.right, 0.5f)
            assertTrue("$size: the logo sits beside Back", logo.left >= back.right)
            // The text below keeps a reading width.
            assertTrue(compose.onNodeWithTag("briefingMeta").bounds().width <= 720f)
        }
    }

    @Test fun `Back sits at the start beside the logo, Preferences at the end, both 48dp and calling once`() {
        render()
        val back = compose.onNodeWithTag("briefingBack").bounds()
        val preferences = compose.onNodeWithContentDescription("Brief preferences").bounds()
        val logo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).bounds()
        assertEquals(48f, back.width, 0.5f)
        assertEquals(48f, preferences.width, 0.5f)
        assertTrue(back.right <= logo.left)
        assertTrue(back.right < preferences.left)
        compose.onNodeWithTag("briefingBack").performClick()
        assertEquals(1, backs)

        direction.value = LayoutDirection.Rtl
        compose.waitForIdle()
        val rtlBack = compose.onNodeWithTag("briefingBack").bounds()
        val rtlPreferences = compose.onNodeWithContentDescription("Brief preferences").bounds()
        val rtlLogo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).bounds()
        assertTrue("Back moves to the right in right-to-left", rtlBack.left > rtlPreferences.right)
        assertTrue("The logo clears Preferences on the left", rtlPreferences.right <= rtlLogo.left)
    }

    @Test fun `the article keeps its place turning a phone sideways and back`() {
        render()
        val article = compose.onNodeWithTag("briefingArticle")
        article.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
        compose.waitForIdle()
        val scrolled = article.scroll()
        assertTrue(scrolled > 0f)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { size ->
            assertEquals("$size: scroll changed", scrolled, compose.onNodeWithTag("briefingArticle").scroll(), 1f)
            assertTrue(state.value is BriefingUiState.Success)
        }
    }

    @Test fun `in short panes the cover is shorter so the headline and Listen show on the first screen`() {
        render()
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.TABLET, BoxloreTestWindows.PHONE_LAND) { size ->
            val cover = compose.onNodeWithTag("briefingPageCover").bounds()
            if (size.heightDp < 480f) {
                assertTrue("$size: cover is ${cover.height} tall", cover.height <= size.heightDp * 0.42f + 0.5f)
                assertTrue("$size: Listen starts below the first screen", compose.onNodeWithText("Listen to the Brief").bounds().bottom <= size.heightDp)
            } else {
                assertEquals("$size: the cover keeps 1.6:1", cover.width / 1.6f, cover.height, 1f)
            }
        }
        // A tabletop fold's lower half is short though the window isn't.
        window.value = BoxloreTestWindows.FOLD_TABLETOP
        pane.value = BoxloreLayoutBounds(0f, 336f, 932f, 650f)
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("briefingPageCover").bounds().height <= 314f * 0.42f + 0.5f)
    }

    @Test fun `loading scrolls to its last placeholder on a sideways phone`() {
        state.value = BriefingUiState.Loading("global")
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        val loading = compose.onNodeWithTag("briefingLoading")
        val range = loading.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertTrue("Loading should scroll in a short window", (range?.maxValue?.invoke() ?: 0f) > 0f)
    }

    @Test fun `Brief preferences is one choice list that stays open through fold and width changes`() {
        render()
        compose.onNodeWithContentDescription("Brief preferences").performClick()
        compose.waitForIdle()
        val rows = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        assertEquals(5, rows.fetchSemanticsNodes().size)
        rows.fetchSemanticsNodes().forEach { node -> assertTrue(node.config.contains(SemanticsProperties.Selected)) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and hasText("Global")).assertIsSelected()
        compose.walkWindows(window, BoxloreTestWindows.FOLD_TABLETOP, BoxloreTestWindows.PHONE, BoxloreTestWindows.W839, BoxloreTestWindows.W841, BoxloreTestWindows.W839) { size ->
            compose.onNodeWithText("Default Brief region").assertExists()
            assertTrue("$size: the default changed", defaults.isEmpty())
        }
        compose.onNodeWithText("India").performScrollTo().performClick()
        assertEquals(listOf("in"), defaults)
    }

    @Test fun `with many sources the sources title stays while the list scrolls to the last one`() {
        state.value = edition().let { it.copy(briefing = it.briefing.copy(sources = (1..12).map { n -> BriefingSource("Source story $n", "https://site$n.example.invalid/a") })) }
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        compose.onNodeWithContentDescription("Sources").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Source story 1"))).performScrollToIndex(11)
        val last = compose.onNodeWithText("Source story 12").assertExists().bounds()
        val title = compose.onNode(hasText("Sources") and !hasClickAction()).assertIsDisplayed().bounds()
        assertTrue("The title stays above the list", title.bottom <= last.top)
    }

    @Test fun `Listen spans the row with no sources, and a story's play button is a smaller end-aligned button`() {
        state.value = edition().let { it.copy(briefing = it.briefing.copy(sources = emptyList())) }
        render()
        val listen = compose.onNodeWithTag("briefingListen").bounds()
        val edition = compose.onNode(hasContentDescription("Choose Brief edition", substring = true)).bounds()
        compose.onNodeWithContentDescription("Sources").assertDoesNotExist()
        assertEquals("Listen reaches the end of the text column", edition.right, listen.right, 1f)

        compose.onNodeWithTag("briefingArticle").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 400f) }
        compose.waitForIdle()
        val play = compose.onAllNodes(hasTestTag(STORY_PLAY_BUTTON_TAG)).onFirst()
        play.assertContentDescriptionEquals("Play story: Story title 1")
        val button = play.bounds()
        val card = compose.onAllNodes(hasTestTag(STORY_PLAY_BUTTON_TAG)).onFirst().fetchSemanticsNode().let { node ->
            generateSequence(node.parent) { it.parent }.first { it.boundsInRoot.width > button.width + 60f }.boundsInRoot
        }
        assertTrue("The play button is ${button.height} tall", button.height >= 48f)
        assertTrue("The play button is narrower than the card", button.width < card.width - 60f)
        assertTrue("The play button sits at the card's end", card.right - button.right < 40f)
    }

    @Test fun `the counter is the only story number, cards line up with the text, and text scrolls under a band`() {
        render()
        compose.onNodeWithTag(BRIEFING_TOP_BAND_TAG).assertDoesNotExist()
        val textStart = compose.onNodeWithTag("briefingMeta").bounds().left
        scrollToEnd(compose.onNodeWithTag("briefingArticle"))
        compose.onNodeWithText("Story 1 of 6").assertExists()
        compose.onAllNodesWithText("Story 1").assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Story 1 of 6", substring = true)).onFirst().assertExists()
        // The first card starts level with the page's text.
        val button = compose.onAllNodes(hasTestTag(STORY_PLAY_BUTTON_TAG)).onFirst().fetchSemanticsNode()
        val card = generateSequence(button.parent) { it.parent }.first { it.boundsInRoot.width > button.boundsInRoot.width + 60f }.boundsInRoot
        assertEquals(textStart, card.left, 1f)
        // Past the cover, the floating buttons sit on a band of page colour.
        val band = compose.onNodeWithTag(BRIEFING_TOP_BAND_TAG).bounds()
        assertTrue(compose.onNodeWithTag("briefingBack").bounds().bottom <= band.bottom)
    }

    @Test fun `TalkBack names the seek bar and says where playback is`() {
        state.value = edition().copy(currentPosition = 60_000)
        render()
        val seek = compose.onNodeWithTag("briefingSeekbar")
        assertEquals("Playback position", seek.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
        assertEquals("1:00 of 8:00", seek.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        state.value = edition().copy(currentPosition = 120_000)
        compose.waitForIdle()
        assertEquals("2:00 of 8:00", compose.onNodeWithTag("briefingSeekbar").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test fun `the date line wraps instead of cutting the listen time on a small phone with very large text`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        fontScale.value = 2f
        render()
        val meta = compose.onNodeWithTag("briefingMeta").fetchSemanticsNode()
        val text = meta.config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue("The listen time is in \"$text\"", text.contains("min") || text.contains("m "))
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        meta.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        val lines = layout.single()
        assertTrue("The date line should wrap here", lines.lineCount == 2)
        assertTrue("The date line is cut", !lines.isLineEllipsized(1))
    }

    private fun scrollToEnd(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 5_000f) }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun SemanticsNodeInteraction.scroll(): Float = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f

    private companion object {
        const val LONG_ERROR = "Couldn’t load this edition. Check your connection and try again. If it keeps happening, choose another edition below."

        fun edition() = BriefingUiState.Success(
            briefing = Briefing(
                date = "2026-10-30",
                region = "global",
                title = "Energy markets, digital policy and the week ahead",
                script = (1..6).joinToString("\n\n") { "Story $it’s reporting in full, with enough words to fill a few lines of the card." },
                audioUrl = "https://example.invalid/audio",
                coverUrl = "",
                sources = (1..4).map { BriefingSource("Original reporting $it", "https://example$it.invalid/story") },
            ),
            selectedRegion = "global",
            duration = 480_000,
            chapters = (0 until 6).map { Chapter(it * 75.0, "Story title ${it + 1}") },
        )
    }
}
