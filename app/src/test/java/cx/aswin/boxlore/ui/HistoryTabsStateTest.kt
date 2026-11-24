package cx.aswin.boxlore.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.components.BOXLORE_SNACKBAR_TAG
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.history.HISTORY_EMPTY_TAG
import cx.aswin.boxlore.feature.history.HISTORY_INSIGHTS_LOADING_TAG
import cx.aswin.boxlore.feature.history.HISTORY_TOP_BAR_TAG
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.insights.INSIGHTS_LIST_TAG
import cx.aswin.boxlore.feature.history.insights.INSIGHTS_PAIR_TAG
import cx.aswin.boxlore.feature.history.insights.MOSAIC_GRID_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Listening history's tabs, messages and pages across window sizes, with the boards' history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1400dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryTabsStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val history = HistoryHarness(compose)

    @Test fun `Insights keeps its scroll position when Timeline is opened and closed`() {
        history.window.value = cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout(400f, 890f)
        val vm = history.render(tab = HistoryTab.INSIGHTS)
        val list = compose.onNodeWithTag(INSIGHTS_LIST_TAG)
        list.performScrollToIndex(6)
        compose.waitForIdle()
        val scrolled = list.scroll()
        assertTrue(scrolled > 0f)

        history.select(HistoryTab.TIMELINE)
        history.select(HistoryTab.INSIGHTS)

        assertEquals(scrolled, compose.onNodeWithTag(INSIGHTS_LIST_TAG).scroll(), 1f)
        assertTrue(vm.selectedTab.value == HistoryTab.INSIGHTS)
    }

    @Test fun `Undo shows above the switcher at every size and brings the row back`() {
        val board = BoardHistory(empty = false)
        val vm = history.render(board)
        compose.mainClock.autoAdvance = false
        vm.removeHistoryItem("diginotar")
        frames()
        compose.walkWindows(
            history.window,
            BoxloreTestWindows.PHONE,
            BoxloreTestWindows.COVER_443,
            BoxloreTestWindows.COVER_475,
            BoxloreTestWindows.FOLD_FLAT,
            BoxloreTestWindows.COVER_443,
            BoxloreTestWindows.TABLET,
        ) { window ->
            frames()
            val message = snackbarCard()
            val switcher = history.switcher()
            assertTrue("$window: Undo ends at ${message.bottom}, the switcher starts at ${switcher.top}", message.bottom <= switcher.top)
            assertTrue("$window: Undo is inside the window", message.top >= 0f && message.bottom <= window.heightDp)
            if (window == BoxloreTestWindows.TABLET) {
                assertEquals(480f, message.width, 1f)
                assertEquals(window.widthDp / 2f, message.center.x, 1f)
            }
        }
        history.fontScale.value = 1.3f
        history.window.value = BoxloreTestWindows.COVER_475
        frames()
        assertTrue(snackbarCard().bottom <= history.switcher().top)

        compose.onNodeWithText("Undo").performClick()
        frames()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag(BOXLORE_SNACKBAR_TAG).assertDoesNotExist()
        assertEquals(listOf("diginotar"), board.restored)
    }

    @Test fun `with no history both tabs show one empty page whose Explore button is always reachable`() {
        history.render(BoardHistory(empty = true), tab = HistoryTab.INSIGHTS)
        for (tab in listOf(HistoryTab.INSIGHTS, HistoryTab.TIMELINE)) {
            history.select(tab)
            compose.walkWindows(
                history.window,
                BoxloreTestWindows.COVER_443,
                BoxloreTestWindows.PHONE_LAND,
                BoxloreTestWindows.SPLIT_HALF,
                BoxloreTestWindows.COVER_475,
                BoxloreTestWindows.PHONE,
            ) { window ->
                compose.onNodeWithTag(HISTORY_EMPTY_TAG).assertExists()
                compose.onNodeWithTag(INSIGHTS_LIST_TAG).assertDoesNotExist()
                val explore = exploreAtEnd()
                assertTrue("$tab at $window: Explore sits under the switcher", explore.bounds().bottom <= history.switcher().top)
                assertEquals("$tab at $window: the selected tab changed", tab, history.viewModel.selectedTab.value)
            }
            // Large text on the cover still leaves Explore above the switcher.
            history.window.value = BoxloreTestWindows.COVER_475
            history.fontScale.value = 1.3f
            compose.waitForIdle()
            assertTrue(exploreAtEnd().bounds().bottom <= history.switcher().top)
            history.fontScale.value = 1f
            history.window.value = BoxloreTestWindows.PHONE
            compose.waitForIdle()
        }
        // On a phone the page fits: nothing to scroll.
        assertEquals(0f, emptyPage().maxScroll(), 0f)
        compose.onNodeWithText("Explore podcasts").performClick()
        assertEquals(1, history.explores.size)
    }

    @Test fun `the big title starts slim in short windows and opens again once the window is tall`() {
        history.render(tab = HistoryTab.INSIGHTS)
        fun listTop() = compose.onNodeWithTag(INSIGHTS_LIST_TAG).bounds().top
        val tall = listTop()
        compose.walkWindows(
            history.window,
            BoxloreTestWindows.PHONE_LAND,
            BoxloreTestWindows.PHONE,
            BoxloreTestWindows.W900_H479,
            BoxloreTestWindows.W900_H480,
            BoxloreTestWindows.SPLIT_HALF,
            BoxloreTestWindows.PHONE,
        ) { window ->
            compose.waitForIdle()
            if (window.heightDp < 480f) {
                assertTrue("$window: the title should start slim (list at ${listTop()})", listTop() < tall - 40f)
            } else {
                assertEquals("$window: the title should be open again", tall, listTop(), 1f)
            }
        }
        // A tabletop fold's lower half is short too, though the whole window isn't.
        history.window.value = BoxloreTestWindows.FOLD_TABLETOP
        history.pane.value = BoxloreLayoutBounds(0f, 336f, 932f, 650f)
        compose.waitForIdle()
        assertTrue(listTop() < tall - 40f)
        history.pane.value = null
        history.window.value = BoxloreTestWindows.FOLD_FLAT
        compose.waitForIdle()
        assertEquals(tall, listTop(), 1f)
        assertEquals(HistoryTab.INSIGHTS, history.viewModel.selectedTab.value)
    }

    @Test fun `in short windows the empty page shows Explore above the switcher without scrolling`() {
        history.window.value = BoxloreTestWindows.PHONE_LAND
        history.render(BoardHistory(empty = true), tab = HistoryTab.INSIGHTS)
        compose.walkWindows(history.window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.COVER_LAND) { window ->
            for (tab in listOf(HistoryTab.INSIGHTS, HistoryTab.TIMELINE)) {
                history.select(tab)
                frames()
                val explore = compose.onNodeWithText("Explore podcasts").bounds()
                assertTrue("$tab at $window: Explore ends at ${explore.bottom}, the switcher starts at ${history.switcher().top}", explore.bottom <= history.switcher().top)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `opened straight into a short window the title starts slim on Insights, loading and loaded`() {
        val board = BoardHistory(empty = false, loading = true)
        history.window.value = BoxloreTestWindows.PHONE_LAND
        history.render(board, tab = HistoryTab.INSIGHTS, waitForTab = false)
        frames()
        val collapsed = TopAppBarDefaults.LargeAppBarCollapsedHeight.value
        assertEquals("Loading: the title should start slim", collapsed, compose.onNodeWithTag(HISTORY_TOP_BAR_TAG).bounds().height, 1f)
        // Loading shows the tab's skeleton, with the switcher where it stays once the tab loads.
        compose.onNodeWithTag(HISTORY_INSIGHTS_LOADING_TAG).assertExists()
        val loadingSwitcher = history.switcher().top
        board.finishLoading()
        history.select(HistoryTab.INSIGHTS)
        frames()
        assertEquals("Loaded: the title should stay slim", collapsed, compose.onNodeWithTag(HISTORY_TOP_BAR_TAG).bounds().height, 1f)
        assertEquals(loadingSwitcher, history.switcher().top, 0.5f)
    }

    @Test fun `on a sideways phone row menus and the switcher clear the cutout and side buttons`() {
        history.window.value = BoxloreTestWindows.PHONE_LAND
        history.render()
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
        compose.waitForIdle()
        val width = BoxloreTestWindows.PHONE_LAND.widthDp
        val rowMore = hasContentDescription("More options for", substring = true)
        compose.onAllNodes(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst().performScrollToNode(rowMore)
        val more = compose.onAllNodes(rowMore).onFirst().bounds()
        assertTrue("A row's More button ends at ${more.right}", more.right <= width - 48f)
        val switcher = history.switcher()
        assertTrue(switcher.left >= 32f && switcher.right <= width - 48f)
        assertEquals((32f + width - 48f) / 2f, switcher.center.x, 1f)
    }

    @Test fun `on large tablets Insights pairs its cards, its title follows the grid, and the listener keeps their card`() {
        // On the cover the list is long enough to bring When you listen to the top.
        history.window.value = BoxloreTestWindows.COVER_443
        history.render(tab = HistoryTab.INSIGHTS)
        compose.onAllNodesWithTag(INSIGHTS_PAIR_TAG).assertCountEquals(0)
        compose.onNodeWithTag(INSIGHTS_LIST_TAG).performScrollToKey("when")
        compose.walkWindows(history.window, BoxloreTestWindows.DESKTOP, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.DESKTOP, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.COVER_443) { window ->
            compose.waitForIdle()
            val pairs = compose.onAllNodesWithTag(INSIGHTS_PAIR_TAG).fetchSemanticsNodes().size
            if (window == BoxloreTestWindows.DESKTOP) {
                assertTrue("$window should pair cards", pairs > 0)
                // The title and its buttons line up with the grid of cards, not a narrower reading column.
                val bar = compose.onNodeWithTag(HISTORY_TOP_BAR_TAG).bounds()
                val list = compose.onNodeWithTag(INSIGHTS_LIST_TAG).bounds()
                assertEquals(list.left, bar.left, 1f)
                assertEquals(list.right, bar.right, 1f)
            } else {
                assertEquals("$window keeps one column", 0, pairs)
            }
            assertCardAtTop("When you listen", window.toString())
        }
        // The window changes while Timeline is open: back on Insights, the same card is at the top.
        history.window.value = BoxloreTestWindows.DESKTOP
        compose.waitForIdle()
        history.select(HistoryTab.TIMELINE)
        history.window.value = BoxloreTestWindows.COVER_443
        compose.waitForIdle()
        history.select(HistoryTab.INSIGHTS)
        frames()
        assertCardAtTop("When you listen", "back from Timeline")
        assertEquals(HistoryTab.INSIGHTS, history.viewModel.selectedTab.value)
    }

    @Test fun `Records stays at the top across the column change and back`() {
        history.window.value = BoxloreTestWindows.DESKTOP
        history.render(tab = HistoryTab.INSIGHTS)
        compose.onNodeWithTag(INSIGHTS_LIST_TAG).performScrollToKey("records")
        compose.walkWindows(history.window, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.COVER_443, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.DESKTOP) { window ->
            compose.waitForIdle()
            assertCardAtTop("Records", window.toString())
        }
        // Each pair of record tiles ends at the same height, whichever has the longer text.
        fun tile(label: String) = hasText(label) and hasAnyAncestor(hasTestTag(INSIGHTS_LIST_TAG))
        val pairs = listOf("Biggest day" to "Longest run", "Longest sitting" to "Most in one day")
            .filter { (first, second) -> listOf(first, second).all { compose.onAllNodes(tile(it)).fetchSemanticsNodes().isNotEmpty() } }
        assertTrue("The board has a pair of record tiles", pairs.isNotEmpty())
        pairs.forEach { (first, second) ->
            assertEquals("$first and $second", compose.onNode(tile(first)).bounds().height, compose.onNode(tile(second)).bounds().height, 0.5f)
        }
    }

    /** [heading]'s card is the one at the top of the Insights list. */
    private fun assertCardAtTop(heading: String, where: String) {
        val list = compose.onNodeWithTag(INSIGHTS_LIST_TAG).bounds()
        val card = compose.onNode(hasText(heading) and hasAnyAncestor(hasTestTag(INSIGHTS_LIST_TAG)), useUnmergedTree = true).bounds()
        assertTrue("$where: $heading starts at ${card.top - list.top}dp into the list", card.top - list.top in 0f..80f)
    }

    @Test fun `the month's days stop at 56dp on big screens and keep their size on phones`() {
        history.render(tab = HistoryTab.INSIGHTS)
        compose.onNodeWithTag(INSIGHTS_LIST_TAG).performScrollToKey("mosaic")
        val phone = compose.onNodeWithTag(MOSAIC_GRID_TAG).bounds().width
        compose.walkWindows(history.window, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.TABLET, BoxloreTestWindows.PHONE) { window ->
            compose.onNodeWithTag(INSIGHTS_LIST_TAG).performScrollToKey("mosaic")
            val grid = compose.onNodeWithTag(MOSAIC_GRID_TAG).bounds().width
            if (window == BoxloreTestWindows.PHONE) assertEquals(phone, grid, 0.5f) else assertTrue("$window grid is $grid wide", grid <= 7 * 56f + 6 * 6f + 0.5f)
        }
    }

    /** The snackbar's card: Material pads it 12dp inside the tagged node. */
    private fun snackbarCard() = compose.onNodeWithTag(BOXLORE_SNACKBAR_TAG).bounds().deflate(12f)

    private fun emptyPage() = compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(HISTORY_EMPTY_TAG)))

    /** Explore after scrolling the empty page as far as it goes, as a listener would. */
    private fun exploreAtEnd(): SemanticsNodeInteraction {
        emptyPage().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 5_000f) }
        compose.waitForIdle()
        return compose.onNodeWithText("Explore podcasts")
    }

    private fun frames() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(50)
            Thread.sleep(20)
            compose.waitForIdle()
        }
    }

    private fun SemanticsNodeInteraction.maxScroll(): Float = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f

    private fun SemanticsNodeInteraction.scroll(): Float = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
}
