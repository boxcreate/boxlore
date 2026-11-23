package cx.aswin.boxlore.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.history.HISTORY_TABS_TAG
import cx.aswin.boxlore.feature.history.HistoryScreen
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.HistoryViewModel
import cx.aswin.boxlore.feature.history.InsightsUiState
import cx.aswin.boxlore.feature.history.TimelineUiState
import java.time.ZoneId

/**
 * Listening history in a resizable test window: the screen with the boards' history (or none), the
 * window it thinks it has, an optional pane (a fold's half) and a font scale. The test class needs a
 * device at least as large as the biggest window it walks to.
 */
internal class HistoryHarness(private val compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>) {
    val window: MutableState<BoxloreWindowLayout> = mutableStateOf(BoxloreTestWindows.PHONE)
    val pane: MutableState<BoxloreLayoutBounds?> = mutableStateOf(null)
    val fontScale: MutableState<Float> = mutableStateOf(1f)
    val explores = mutableListOf<Unit>()
    val plays = mutableListOf<String>()
    val opens = mutableListOf<String>()
    lateinit var viewModel: HistoryViewModel

    fun render(
        port: BoardHistory = BoardHistory(empty = false),
        tab: HistoryTab = HistoryTab.TIMELINE,
        miniPlayer: Boolean = false,
    ): HistoryViewModel {
        viewModel = HistoryViewModel(port, zoneId = { ZoneId.systemDefault() }, now = { HistoryTabletAuditCaptureTest.TODAY })
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalEchoMotion provides false,
                LocalBoxloreWindowLayout provides window.value,
                LocalBoxlorePaneBounds provides pane.value,
                LocalDensity provides Density(density.density, fontScale.value),
            ) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        HistoryScreen(
                            viewModel = viewModel,
                            onBack = {},
                            onEpisodeClick = { opens += it.episodeId },
                            onExploreClick = { explores += Unit },
                            onPlayEpisode = { plays += it.episodeId },
                            isMiniPlayerVisible = miniPlayer,
                        )
                    }
                }
            }
        }
        select(tab)
        return viewModel
    }

    fun select(tab: HistoryTab) {
        viewModel.selectTab(tab)
        waitUntil {
            if (tab == HistoryTab.INSIGHTS) viewModel.insightsState.value is InsightsUiState.Ready else viewModel.timelineState.value is TimelineUiState.Ready
        }
    }

    /** The tabs build off the main thread: let the clock run until [ready]. */
    fun waitUntil(ready: () -> Boolean) {
        repeat(60) {
            if (ready()) return@repeat
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(40)
            compose.waitForIdle()
        }
        compose.waitForIdle()
        check(ready()) { "Still loading" }
    }

    /** The Insights | Timeline switcher's bounds. */
    fun switcher(): Rect = compose.onNodeWithTag(HISTORY_TABS_TAG).bounds()

    /** The test window's height in dp (the root's own coordinates start at the window's top). */
    val windowHeight: Float get() = window.value.heightDp
}

/** A node's bounds in the root, in dp (tests run at mdpi). */
internal fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot
