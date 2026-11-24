package cx.aswin.boxlore.feature.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.pages.heroPickerPictureHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The scrolling picture row uses wide tiles and never fits a whole number of them, so the cut-off
 * tile always says there is more to scroll; a fling settles on a tile's start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w1000dp-h800dp-mdpi")
class SettingsPictureRowTest {
    @get:Rule val compose = createComposeRule()

    private val gap = 8f

    /** How many tiles show across the row, counting the cut-off one as a fraction. */
    private fun visible(row: Float): Float {
        val tile = settingsPictureRowTileWidth(row.dp).value
        return (row + gap) / (tile + gap)
    }

    @Test fun `phones show one wide tile and a clear part of the next`() {
        // Settings rows on 320dp to 448dp wide phones and a folded foldable, after the page's 16dp sides.
        for (row in listOf(288f, 328f, 337f, 360f, 380f, 416f)) {
            val tile = settingsPictureRowTileWidth(row.dp).value
            assertTrue("$row: tiles are wide ($tile)", tile >= 0.6f * row)
            val shown = visible(row)
            assertTrue("$row: one whole tile and part of the next ($shown)", shown > 1.3f && shown < 1.6f)
        }
    }

    @Test fun `wider windows add whole tiles but never fit a whole number`() {
        for (row in listOf(500f, 640f, 688f, 720f, 900f, 1200f)) {
            val shown = visible(row)
            val part = shown - shown.toInt()
            assertTrue("$row: the last tile is clearly cut off ($shown)", part > 0.3f && part < 0.7f)
            assertTrue("$row: tiles stay within 160-320dp", settingsPictureRowTileWidth(row.dp).value in 160f..320f)
        }
    }

    @Test fun `tiles never get narrower than 160dp, even on a tiny row`() {
        assertEquals(160f, settingsPictureRowTileWidth(200.dp).value, 0.01f)
    }

    @Test fun `a fling settles on the next tile start in its direction, and a slow release on the nearest`() {
        val step = 240f
        assertEquals(240f - 100f, settingsPictureRowSnapOffset(100f, 2000f, step, velocity = 500f), 0.01f)
        assertEquals(-100f, settingsPictureRowSnapOffset(100f, 2000f, step, velocity = -500f), 0.01f)
        assertEquals(-100f, settingsPictureRowSnapOffset(100f, 2000f, step, velocity = 0f), 0.01f)
        assertEquals(240f - 300f, settingsPictureRowSnapOffset(300f, 2000f, step, velocity = 0f), 0.01f)
        assertEquals(480f - 400f, settingsPictureRowSnapOffset(400f, 2000f, step, velocity = 0f), 0.01f)
    }

    @Test fun `the row's end stays reachable and the start never overshoots`() {
        assertEquals(0f, settingsPictureRowSnapOffset(1100f, 1100f, 240f, velocity = 800f), 0.01f)
        assertEquals(1100f - 1000f, settingsPictureRowSnapOffset(1000f, 1100f, 240f, velocity = 800f), 0.01f)
        assertEquals(0f, settingsPictureRowSnapOffset(0f, 1100f, 240f, velocity = -800f), 0.01f)
    }

    // Regression: after a fold or rotation the picked Home style could rest half off-screen.
    @Test fun `a resize keeps the same tile at the row's start, both ways`() {
        val phone = settingsPictureRowTileWidth(380.dp).value + gap
        val wide = settingsPictureRowTileWidth(820.dp).value + gap
        val atThird = (3 * phone).toInt()
        val widened = settingsPictureRowRescroll(atThird.toFloat(), phone, wide)
        assertEquals(3 * wide, widened.toFloat(), 1f)
        assertEquals(3 * phone, settingsPictureRowRescroll(widened.toFloat(), wide, phone).toFloat(), 1f)
        assertEquals(0, settingsPictureRowRescroll(0f, phone, wide))
    }

    @Test fun `on screen, the tile at the row's start stays there as the row widens and narrows`() {
        var rowWidth by mutableStateOf(380.dp)
        compose.setContent {
            Box(Modifier.width(rowWidth)) {
                SettingsPictureRow(items = (0 until 8).toList(), selectedIndex = 3) { item, modifier, width ->
                    Box(modifier.width(width).height(80.dp).testTag("tile$item"))
                }
            }
        }
        compose.waitForIdle()
        // The row opens on the picked tile.
        val start = compose.onNodeWithTag("tile3").fetchSemanticsNode().positionInRoot.x
        assertTrue("tile 3 in view ($start)", start in 0f..40f)
        for (next in listOf(820.dp, 380.dp)) {
            compose.runOnIdle { rowWidth = next }
            compose.waitForIdle()
            assertEquals("tile 3 at the start at $next", start, compose.onNodeWithTag("tile3").fetchSemanticsNode().positionInRoot.x, 1f)
        }
    }

    @Test fun `hero pictures keep the Home card's shape as the tile widens`() {
        // On a phone the picture is now as wide as a real hero card is tall, not a 122dp sliver.
        val tile = settingsPictureRowTileWidth(337.dp).value
        val phone = heroPickerPictureHeight(tile.dp).value
        assertTrue("phone picture is wide (${tile - 24f})", tile - 24f > 190f)
        assertEquals((tile - 24f) * 0.78f, phone, 0.5f)
        assertEquals(150f, heroPickerPictureHeight(160.dp).value, 0.01f)
        assertEquals(240f, heroPickerPictureHeight(400.dp).value, 0.01f)
    }
}
