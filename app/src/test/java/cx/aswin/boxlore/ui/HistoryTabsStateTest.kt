package cx.aswin.boxlore.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import cx.aswin.boxlore.core.designsystem.components.BOXLORE_SNACKBAR_TAG
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.history.HISTORY_EMPTY_TAG
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.insights.INSIGHTS_LIST_TAG
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
