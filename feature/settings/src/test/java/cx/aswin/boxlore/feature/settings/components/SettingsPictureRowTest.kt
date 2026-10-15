package cx.aswin.boxlore.feature.settings.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scrolling picture row always cuts its third tile off at the edge, so it reads as more to scroll. */
class SettingsPictureRowTest {
    private val gap = 8f

    @Test fun `phones show two whole tiles and part of a third`() {
        // Settings rows on 320dp to 448dp wide phones, after the page's 16dp sides.
        for (row in listOf(288f, 328f, 360f, 380f, 416f)) {
            val tile = settingsPictureRowTileWidth(row.dp).value
            val twoWhole = tile * 2 + gap
            assertTrue("$row: two tiles fit whole ($twoWhole)", twoWhole <= row)
            assertTrue("$row: the third starts inside the row", twoWhole + gap < row)
            assertTrue("$row: the third is cut off (${twoWhole + gap + tile})", twoWhole + gap + tile > row)
        }
    }

    @Test fun `tiles stay legible on narrow rows and do not balloon on wide ones`() {
        assertEquals(132f, settingsPictureRowTileWidth(200.dp).value, 0.01f)
        assertEquals(200f, settingsPictureRowTileWidth(900.dp).value, 0.01f)
    }
}
