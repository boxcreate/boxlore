package cx.aswin.boxlore.feature.briefing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Brief on wide windows: two panes, a one-column list on portrait tablets, and the pager elsewhere. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BriefingWideLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window: MutableState<BoxloreWindowLayout> = mutableStateOf(BoxloreTestWindows.PHONE)
    private val pane: MutableState<BoxloreLayoutBounds?> = mutableStateOf(null)
    private val state: MutableState<BriefingUiState> = mutableStateOf(edition())
    private val plays = mutableListOf<Long?>()

    private fun render() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalBoxlorePaneBounds provides pane.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        BriefingScreen(state.value, {}, {}, { _, position -> plays += position }, {}, {}, {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the layout follows the pane's size`() {
        assertEquals(BriefingLayoutMode.Pager, BriefingLayoutMode.of(412f, 915f, shortPane = false))
        assertEquals(BriefingLayoutMode.Pager, BriefingLayoutMode.of(475f, 695f, shortPane = false))
        assertEquals(BriefingLayoutMode.Pager, BriefingLayoutMode.of(915f, 412f, shortPane = true))
        assertEquals(BriefingLayoutMode.List, BriefingLayoutMode.of(800f, 1_280f, shortPane = false))
        assertEquals(BriefingLayoutMode.List, BriefingLayoutMode.of(750f, 832f, shortPane = false))
        assertEquals(BriefingLayoutMode.TwoPane, BriefingLayoutMode.of(1_280f, 800f, shortPane = false))
        assertEquals(BriefingLayoutMode.TwoPane, BriefingLayoutMode.of(841f, 900f, shortPane = false))
        assertEquals(BriefingLayoutMode.List, BriefingLayoutMode.of(839f, 900f, shortPane = false))
        assertEquals(BriefingLayoutMode.TwoPane, BriefingLayoutMode.of(932f, 650f, shortPane = false))
        // Landscape windows too narrow for a readable story list beside the edition keep one column.
        assertEquals(BriefingLayoutMode.List, BriefingLayoutMode.of(600f, 480f, shortPane = false))
        assertEquals(BriefingLayoutMode.List, BriefingLayoutMode.of(700f, 500f, shortPane = false))
        assertEquals(BriefingLayoutMode.Pager, BriefingLayoutMode.of(599f, 479f, shortPane = true))
        assertTrue(briefingStoryListRoom(764f) >= BRIEFING_STORY_LIST_MIN_DP)
        assertEquals(BriefingLayoutMode.TwoPane, BriefingLayoutMode.of(764f, 600f, shortPane = false))
    }

    @Test fun `a landscape tablet shows the edition beside every story`() {
        window.value = BoxloreTestWindows.TABLET
        render()
        val start = compose.onNodeWithTag(BRIEFING_START_PANE_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("start pane is ${start.width} wide", start.width in BRIEFING_START_PANE_MIN_DP..BRIEFING_START_PANE_MAX_DP)
        compose.onNodeWithText("Listen to the Brief").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next story").assertDoesNotExist()
        val list = compose.onNodeWithTag(BRIEFING_STORY_LIST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the story list is ${list.width} wide", list.width <= BRIEFING_STORY_LIST_MAX_DP + 0.5f)
        // The list starts below the floating buttons, so no story passes under Preferences.
        assertTrue(list.top >= compose.onNodeWithContentDescription("Brief preferences").fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithTag(BRIEFING_STORY_LIST_TAG).performScrollToIndex(5)
        compose.onNode(hasContentDescription("Story 6 of 6", substring = true)).assertExists()
        // The cover sits in the start pane, not under the whole status bar: dark icons on the light page.
        assertTrue(lightStatusBars())
        window.value = BoxloreTestWindows.PHONE
        compose.waitForIdle()
        assertFalse(lightStatusBars())
    }

    @Test fun `held like a book, neither pane crosses the hinge`() {
        window.value = BoxloreTestWindows.FOLD_BOOK
        render()
        val frame = compose.onNodeWithTag("briefing_reading_frame").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag(BRIEFING_START_PANE_TAG).fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag(BRIEFING_STORY_LIST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("start pane ends at ${start.right - frame.left}", start.right - frame.left <= 456f + 0.5f)
        assertTrue("story list starts at ${list.left - frame.left}", list.left - frame.left >= 477f - 0.5f)
    }

    @Test fun `the lists end above a 3-button navigation bar`() {
        render()
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        for (size in listOf(BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.TABLET)) {
            window.value = size
            compose.waitForIdle()
            // Sent after the window has settled, as the system would.
            compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets(statusTop = 24, navBottom = 48)) }
            compose.waitForIdle()
            val frame = compose.onNodeWithTag("briefing_reading_frame").fetchSemanticsNode().boundsInRoot
            val (list, lastIndex) = if (size == BoxloreTestWindows.TABLET) BRIEFING_STORY_LIST_TAG to 5 else "briefingArticle" to 7
            compose.onNodeWithTag(list).performScrollToIndex(lastIndex)
            compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets(statusTop = 24, navBottom = 48)) }
            compose.waitForIdle()
            val last = if (size == BoxloreTestWindows.TABLET) {
                compose.onAllNodesWithTag(BRIEFING_LISTED_STORY_TAG).fetchSemanticsNodes().maxBy { it.boundsInRoot.bottom }.boundsInRoot
            } else {
                compose.onNodeWithText("Report an issue").fetchSemanticsNode().boundsInRoot
            }
            assertTrue("$size: the last item ends at ${frame.bottom - last.bottom} above the window's bottom", last.bottom <= frame.bottom - 48f)
        }
    }

    @Test fun `in one column, playback moving on leaves Listen in view until the listener scrolls to the stories`() {
        window.value = BoxloreTestWindows.TABLET_PORTRAIT
        state.value = edition().copy(isPlaying = true, currentPosition = 0)
        render()
        state.value = edition().copy(isPlaying = true, currentPosition = 75_000L + 1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Pause the Brief").assertIsDisplayed()
    }

    @Test fun `another edition opens at its first story`() {
        render()
        repeat(2) { compose.onNodeWithContentDescription("Next story").performScrollTo().performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Story 3 of 6").assertExists()
        state.value = edition().copy(briefing = edition().briefing.copy(region = "us", date = "2026-10-31"), selectedRegion = "us")
        compose.waitForIdle()
        compose.onNodeWithText("Story 1 of 6").assertExists()
    }

    @Test fun `the current story stays current through layouts and window changes`() {
        render()
        repeat(2) { compose.onNodeWithContentDescription("Next story").performScrollTo().performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Story 3 of 6").assertExists()
        compose.walkWindows(
            window,
            BoxloreTestWindows.TABLET,
            BoxloreTestWindows.TABLET_PORTRAIT,
            BoxloreTestWindows.PHONE,
            BoxloreTestWindows.W839,
            BoxloreTestWindows.W841,
            BoxloreTestWindows.GATE_599x479,
            BoxloreTestWindows.GATE_600x480,
            BoxloreTestWindows.GATE_599x479,
            BoxloreTestWindows.FOLD_FLAT,
            BoxloreTestWindows.PHONE,
        ) { size ->
            compose.waitForIdle()
            if (BriefingLayoutMode.of(size.widthDp, size.heightDp, size.heightDp < 480f) == BriefingLayoutMode.Pager) {
                compose.onNodeWithText("Story 3 of 6").assertExists()
            } else {
                val listed = compose.onAllNodesWithTag(BRIEFING_LISTED_STORY_TAG).fetchSemanticsNodes()
                assertTrue("$size lists the stories", listed.isNotEmpty())
                compose.onNode(hasContentDescription("Story 3 of 6", substring = true)).assertIsDisplayed()
            }
        }
        assertTrue("No playback started", plays.isEmpty())
    }

    @Test fun `playback moving to another story brings it into view in two panes`() {
        window.value = BoxloreTestWindows.TABLET
        state.value = edition().copy(isPlaying = true, currentPosition = 0)
        render()
        state.value = edition().copy(isPlaying = true, currentPosition = 4 * 75_000L + 1_000)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Story 5 of 6", substring = true)).assertIsDisplayed()
        compose.onAllNodesWithTag(PLAYING_STORY_STRIP_TAG).onFirst().assertExists()
    }

    @Test fun `a portrait tablet lists the stories in one column and a phone keeps the pager`() {
        window.value = BoxloreTestWindows.TABLET_PORTRAIT
        render()
        compose.onNodeWithTag(BRIEFING_START_PANE_TAG).assertDoesNotExist()
        assertTrue(compose.onAllNodesWithTag(BRIEFING_LISTED_STORY_TAG).fetchSemanticsNodes().isNotEmpty() || listedAfterScroll())
        window.value = BoxloreTestWindows.PHONE
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Next story").assertExists()
        compose.onAllNodesWithTag(BRIEFING_LISTED_STORY_TAG).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        compose.onNode(hasTestTag("briefing_reading_frame")).assertExists()
    }

    private fun lightStatusBars(): Boolean = compose.runOnIdle {
        WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars
    }

    private fun listedAfterScroll(): Boolean {
        compose.onNodeWithTag("briefingArticle").performScrollToIndex(2)
        return compose.onAllNodesWithTag(BRIEFING_LISTED_STORY_TAG).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        fun edition() = BriefingUiState.Success(
            briefing = Briefing(
                date = "2026-10-30",
                region = "global",
                title = "Energy markets, digital policy and the week ahead",
                script = (1..6).joinToString("\n\n") { "Story $it’s reporting in full, with enough words to fill a few lines of the card." },
                audioUrl = "https://example.invalid/audio",
                coverUrl = "",
            ),
            selectedRegion = "global",
            duration = 480_000,
            chapters = (0 until 6).map { Chapter(it * 75.0, "Story title ${it + 1}") },
        )
    }
}
