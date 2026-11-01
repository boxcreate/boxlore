package cx.aswin.boxlore.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.history.HistoryScreen
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.HistoryViewModel
import cx.aswin.boxlore.feature.history.InsightsUiState
import cx.aswin.boxlore.feature.history.TimelineUiState
import cx.aswin.boxlore.feature.history.insights.INSIGHTS_LIST_TAG
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Insights keeps its place while Timeline is open: back on Insights, the list is where it was left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h890dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryTabsStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `Insights keeps its scroll position when Timeline is opened and closed`() {
        val vm = HistoryViewModel(BoardHistory(empty = false), zoneId = { ZoneId.systemDefault() }, now = { HistoryTabletAuditCaptureTest.TODAY })
        compose.setContent {
            CompositionLocalProvider(LocalEchoMotion provides false) {
                BoxLoreTheme(darkTheme = true, dynamicColor = false) { HistoryScreen(vm, {}, {}, {}) }
            }
        }
        vm.selectTab(HistoryTab.INSIGHTS)
        waitUntil { vm.insightsState.value is InsightsUiState.Ready }
        val list = compose.onNodeWithTag(INSIGHTS_LIST_TAG)
        list.performScrollToIndex(6)
        compose.waitForIdle()
        val scrolled = list.scroll()
        assertTrue(scrolled > 0f)

        vm.selectTab(HistoryTab.TIMELINE)
        waitUntil { vm.timelineState.value is TimelineUiState.Ready }
        vm.selectTab(HistoryTab.INSIGHTS)
        waitUntil { vm.insightsState.value is InsightsUiState.Ready }

        assertEquals(scrolled, compose.onNodeWithTag(INSIGHTS_LIST_TAG).scroll(), 1f)
    }

    /** The tabs build off the main thread: let the clock run until [ready]. */
    private fun waitUntil(ready: () -> Boolean) {
        repeat(50) {
            if (ready()) return@repeat
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
            compose.waitForIdle()
        }
        compose.waitForIdle()
        check(ready()) { "Still loading" }
    }

    private fun SemanticsNodeInteraction.scroll(): Float = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
}
