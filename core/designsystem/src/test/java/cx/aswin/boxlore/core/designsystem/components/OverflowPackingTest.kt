package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
class OverflowPackingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all chips fit without a redundant more control`() {
        assertEquals(OverflowPacking(listOf(0, 1, 2), emptyList()), packOverflowItems(listOf(60, 80, 90), 246, 8, 70, null))
    }

    @Test fun `one pixel less reserves more and preserves remaining canonical order`() {
        assertEquals(OverflowPacking(listOf(0, 1), listOf(2)), packOverflowItems(listOf(60, 80, 90), 245, 8, 70, null))
    }

    @Test fun `width growth reveals more than the old fixed shortlist`() {
        val widths = List(20) { 80 }
        assertEquals(7, packOverflowItems(widths, 720, 8, 80, null).visible.size)
        assertEquals(12, packOverflowItems(widths, 1200, 8, 80, null).visible.size)
    }

    @Test fun `late selection remains inline while overflow excludes every visible item`() {
        val packing = packOverflowItems(List(8) { 80 }, 360, 8, 70, 7)
        assertEquals(listOf(0, 7, 1), packing.visible)
        assertEquals(listOf(2, 3, 4, 5, 6), packing.remaining)
    }

    @Test fun `required controls use scroll fallback instead of hiding the selection`() {
        assertEquals(OverflowPacking(listOf(0, 3), listOf(1, 2)), packOverflowItems(List(4) { 120 }, 200, 8, 90, 3))
    }

    @Test fun `the More sheet stays open and follows the row while the window grows and shrinks`() {
        val window = mutableStateOf(BoxloreTestWindows.W600_H900)
        val states = mutableListOf<AdaptiveOverflowState>()
        val reported = mutableListOf<List<Int>>()
        compose.setContent {
            val state = rememberAdaptiveOverflowState()
            states += state
            var sheetOpen by remember { mutableStateOf(false) }
            Column {
                AdaptiveOverflowRow(
                    itemCount = CHIPS,
                    selectedIndex = null,
                    onOverflow = { sheetOpen = true },
                    modifier = Modifier.width(window.value.widthDp.dp),
                    state = state,
                    onOverflowChanged = { reported += it },
                    item = { Box(Modifier.size(CHIP_WIDTH.dp, 40.dp)) },
                    more = { onClick -> Box(Modifier.size(MORE_WIDTH.dp, 40.dp).testTag("more").clickable(onClick = onClick)) },
                )
                if (sheetOpen) Text("${state.remaining.size} more", Modifier.testTag("sheet"))
            }
        }
        compose.onNodeWithTag("more").performClick()
        compose.walkWindows(window, BoxloreTestWindows.W841, BoxloreTestWindows.TABLET, BoxloreTestWindows.W600_H900) { step ->
            val state = states.last()
            val expected = packOverflowItems(List(CHIPS) { CHIP_WIDTH }, step.widthDp.toInt(), 8, MORE_WIDTH, null).remaining
            assertTrue("$step still overflows", expected.isNotEmpty())
            assertEquals("$step", expected, state.remaining)
            assertEquals("$step is reported", expected, reported.last())
            compose.onNodeWithTag("sheet").assertExists("the sheet stays open at $step")
            states.forEach { assertSame("one overflow state for the row", states.first(), it) }
        }
        assertTrue("the row repacked at each width", reported.distinct().size >= 3)
    }

    private companion object {
        const val CHIPS = 20
        const val CHIP_WIDTH = 80
        const val MORE_WIDTH = 70
    }
}
