package cx.aswin.boxlore.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.InsightsUiState
import cx.aswin.boxlore.feature.history.insights.INSIGHTS_LIST_TAG
import cx.aswin.boxlore.feature.history.insights.WHEN_STACKED_TAG
import cx.aswin.boxlore.feature.history.story.STORY_ENTRY_TOTAL_TAG
import cx.aswin.boxlore.feature.history.timeline.JumpToDayContent
import cx.aswin.boxlore.feature.history.timeline.JumpToDayState
import cx.aswin.boxlore.feature.history.timeline.PLAY_DISC_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Listening history's small controls: menus, selectors, the play disc, Clear all, chips and large-text fits. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1400dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val history = HistoryHarness(compose)

    @Test fun `a row's menu removes the episode, with Remove in its own group`() {
        history.render()
        val more = hasContentDescription("More options for Was Jack the Ripper a Freemason?")
        timelineList().performScrollToNode(more)
        compose.onNode(more).performClick()
        compose.onNodeWithText("Go to show").assertExists()
        compose.onNodeWithText("Remove from history").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Removed from history").assertExists()
    }

    @Test fun `Clear all asks first, Cancel keeps the history and the red button clears it once`() {
        val board = BoardHistory(empty = false)
        history.render(board)
        compose.onNode(hasContentDescription("More options")).performClick()
        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, board.clears)
        compose.onNode(hasContentDescription("More options")).performClick()
        compose.onNodeWithText("Clear all").performClick()
        compose.walkWindows(history.window, BoxloreTestWindows.W599_H900, BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900) {
            compose.onNodeWithText("Clear listening history?", substring = true).assertExists()
        }
        compose.onNode(hasText("Clear") and hasClickAction()).performClick()
        history.waitUntil { board.clears == 1 }
        assertEquals(1, board.clears)
    }

    @Test fun `the period selector is one choice group and keeps the period through a window change`() {
        val vm = history.render(tab = HistoryTab.INSIGHTS)
        val periods = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and hasAnyAncestor(hasTestTag(INSIGHTS_LIST_TAG)))
        assertEquals(4, periods.fetchSemanticsNodes().size)
        assertEquals(44f, periods.fetchSemanticsNodes().first().boundsInRoot.height, 0.5f)
        compose.onNode(hasText("30d")).performClick()
        history.waitUntil { (vm.insightsState.value as? InsightsUiState.Ready)?.period == ListeningPeriod.DAYS_30 }
        compose.walkWindows(history.window, BoxloreTestWindows.TABLET, BoxloreTestWindows.PHONE) {
            history.waitUntil { vm.insightsState.value is InsightsUiState.Ready }
            compose.onNode(hasText("30d") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsOn()
        }
    }

    @Test fun `a tap just beside a row's small play disc resumes the episode instead of opening it`() {
        history.render()
        val disc = hasTestTag(PLAY_DISC_TAG)
        timelineList().performScrollToNode(hasContentDescription("Resume Was Jack the Ripper a Freemason?", substring = true))
        // A row's 26dp disc, not Jump back in's larger one.
        val bounds = compose.onAllNodes(disc, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.width in 20f..30f && it.height >= 20f }
        compose.onRoot().performTouchInput { click(Offset(bounds.left - 8f, bounds.center.y)) }
        compose.waitForIdle()
        assertEquals(1, history.plays.size)
        assertTrue(history.opens.isEmpty())
    }

    @Test fun `Insights while timing fills in has no period selector`() {
        history.render(BoardHistory(empty = false, timedDays = 2), tab = HistoryTab.INSIGHTS)
        compose.onNode(hasText("30d")).assertDoesNotExist()
    }

    @Test fun `on a small phone with large text the story total stays on one line and the clock stacks`() {
        history.window.value = BoxloreTestWindows.PHONE_SMALL
        history.fontScale.value = 1.3f
        history.render(tab = HistoryTab.INSIGHTS)
        val total = compose.onNodeWithTag(STORY_ENTRY_TOTAL_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>()
        total.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(1, layout.single().lineCount)
        assertTrue(!layout.single().hasVisualOverflow)
        compose.onNodeWithTag(INSIGHTS_LIST_TAG).performScrollToKey("when")
        compose.onNodeWithTag(WHEN_STACKED_TAG).assertExists()

        history.fontScale.value = 1f
        history.window.value = BoxloreTestWindows.PHONE
        compose.waitForIdle()
        compose.onNodeWithTag(INSIGHTS_LIST_TAG).performScrollToKey("when")
        compose.onNodeWithTag(WHEN_STACKED_TAG).assertDoesNotExist()
    }

    @Test fun `Jump to a day's quick jumps wrap on the cover with large text and still jump`() {
        val jumps = mutableListOf<LocalDate>()
        val today = HistoryTabletAuditCaptureTest.TODAY
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(443.dp, 1_200.dp)) {
                        JumpToDayContent(JumpToDayState(HistoryTabletAuditCaptureTest.BOARD_CALENDAR, HistoryTabletAuditCaptureTest.BOARD_CALENDAR.keys, LocalDate.of(2026, 3, 9), today), { jumps += it }, {})
                    }
                }
            }
        }
        listOf("A week ago", "A month ago").forEach { label ->
            val chip = compose.onNode(hasText(label, substring = true)).fetchSemanticsNode().boundsInRoot
            assertTrue("$label ends at ${chip.right}", chip.right <= 443f - 20f + 0.5f)
        }
        compose.onNode(hasText("A week ago", substring = true)).performClick()
        assertEquals(listOf(today.minusWeeks(1)), jumps)
    }

    private fun timelineList() =
        compose.onAllNodes(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst()
}
