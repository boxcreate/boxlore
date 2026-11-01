package cx.aswin.boxlore.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.history.HistoryScreen
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.HistoryViewModel
import cx.aswin.boxlore.feature.history.TimelineUiState
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Days|Shows on Timeline: Jump back in, above the toggle, folds away and back, so the toggle slides
 * to its new place over several frames instead of jumping its full height in one. All in progress
 * scrolls the list to the filters, so the tap visibly lands somewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h900dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryTimelineGroupingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreWindowLayout(400f, 890f))
    private lateinit var vm: HistoryViewModel

    @Test fun `switching Days and Shows on a phone slides the toggle instead of jumping`() {
        render()
        assertSlides()
    }

    @Test fun `switching Days and Shows on a tablet slides the toggle instead of jumping`() {
        window.value = BoxloreWindowLayout(1_280f, 800f)
        render()
        assertSlides()
    }

    @Test fun `Shows stays folded through live resizing and Days still slides back`() {
        render()
        val days = toggleTop()
        switchTo(TimelineGrouping.SHOWS)
        val shows = toggleTop()
        for (size in listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(1_280f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(400f, 890f),
        )) {
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            compose.onNodeWithText("Shows").assertIsSelected()
            compose.onNodeWithText("Jump back in").assertDoesNotExist()
        }
        assertEquals(shows, toggleTop(), 1f)
        val tops = switchTo(TimelineGrouping.DAYS)
        assertGradual(tops, from = shows, to = days)
    }

    @Test fun `All in progress scrolls to the filters on a phone`() {
        render()
        assertAllInProgressScrolls()
    }

    @Test fun `All in progress scrolls to the filters on a tablet and keeps them through resizing`() {
        window.value = BoxloreWindowLayout(1_280f, 800f)
        render()
        assertAllInProgressScrolls()
        val chips = chipTop()
        for (size in listOf(BoxloreWindowLayout(599f, 800f), BoxloreWindowLayout(600f, 800f), BoxloreWindowLayout(840f, 479f), BoxloreWindowLayout(1_280f, 800f))) {
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            compose.onNode(hasText("In progress") and isSelected()).assertIsDisplayed()
            compose.onNodeWithText("Search episodes and shows").assertIsNotDisplayed()
            assertEquals(chips, chipTop(), 1f)
        }
    }

    /** Before, only the chip changed and the list stayed put; now the list moves the filters to its top. */
    private fun assertAllInProgressScrolls() {
        val before = chipTop()
        compose.onNodeWithText("Search episodes and shows").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("All ${(vm.timelineState.value as TimelineUiState.Ready).content.inProgressCount}").performClick()
        val tops = mutableListOf(before)
        repeat(60) {
            Thread.sleep(5)
            compose.mainClock.advanceTimeByFrame()
            tops += chipTop()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val after = chipTop()
        assertEquals(TimelineFilter.IN_PROGRESS, (vm.timelineState.value as TimelineUiState.Ready).filter)
        compose.onNode(hasText("In progress") and isSelected()).assertIsDisplayed()
        // The filters reach the list's top, past the search field and Jump back in.
        compose.onNodeWithText("Search episodes and shows").assertIsNotDisplayed()
        assertTrue("filters move up, $before → $after", before - after > 100f)
        assertTrue("the list scrolls over several frames, saw $tops", tops.count { it < before - 4f && it > after + 4f } >= 3)
    }

    private fun chipTop(): Float = compose.onNodeWithText("Finished").getUnclippedBoundsInRoot().top.value

    private fun assertSlides() {
        val days = toggleTop()
        compose.onNodeWithText("Jump back in").assertExists()
        val toShows = switchTo(TimelineGrouping.SHOWS)
        val shows = toggleTop()
        compose.onNodeWithText("Jump back in").assertDoesNotExist()
        assertTrue("toggle moves up by Jump back in's height, not $days → $shows", days - shows > 100f)
        assertGradual(toShows, from = days, to = shows)

        val toDays = switchTo(TimelineGrouping.DAYS)
        compose.onNodeWithText("Jump back in").assertExists()
        assertEquals(days, toggleTop(), 1f)
        assertGradual(toDays, from = shows, to = days)
    }

    /** Several frames sit between [from] and [to], moving one way: no single-frame jump. */
    private fun assertGradual(tops: List<Float>, from: Float, to: Float) {
        val between = tops.filter { it > minOf(from, to) + 4f && it < maxOf(from, to) - 4f }
        assertTrue("toggle passes through in-between places, saw $tops", between.size >= 3)
        val steps = tops.zipWithNext { a, b -> b - a }
        assertTrue("toggle moves one way, saw $tops", if (to < from) steps.all { it <= 0.5f } else steps.all { it >= -0.5f })
        assertTrue("no frame covers most of the distance, saw $tops", steps.all { kotlin.math.abs(it) < kotlin.math.abs(to - from) * 0.6f })
    }

    /** Taps [grouping] with the clock held, then records the toggle's top on each frame until it settles. */
    private fun switchTo(grouping: TimelineGrouping): List<Float> {
        val label = if (grouping == TimelineGrouping.SHOWS) "Shows" else "Days"
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(label).performClick()
        repeat(50) {
            if ((vm.timelineState.value as? TimelineUiState.Ready)?.grouping == grouping) return@repeat
            Thread.sleep(20)
            compose.runOnIdle { }
        }
        check((vm.timelineState.value as? TimelineUiState.Ready)?.grouping == grouping) { "Grouping did not change" }
        val tops = mutableListOf(toggleTop())
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            tops += toggleTop()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(label).assertIsSelected()
        return tops
    }

    private fun toggleTop(): Float = compose.onNodeWithText("Shows").getUnclippedBoundsInRoot().top.value

    private fun render() {
        vm = HistoryViewModel(BoardHistory(empty = false), zoneId = { ZoneId.systemDefault() }, now = { HistoryTabletAuditCaptureTest.TODAY })
        compose.setContent {
            val size = window.value
            CompositionLocalProvider(LocalEchoMotion provides false, LocalBoxloreWindowLayout provides size) {
                BoxLoreTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) { HistoryScreen(vm, {}, {}, {}) }
                }
            }
        }
        vm.selectTab(HistoryTab.TIMELINE)
        repeat(50) {
            if (vm.timelineState.value is TimelineUiState.Ready) return@repeat
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
            compose.waitForIdle()
        }
        compose.waitForIdle()
        check(vm.timelineState.value is TimelineUiState.Ready) { "Still loading" }
    }
}
