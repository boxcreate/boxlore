package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.ui.unit.Density
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaylistGridCellsTest {
    /** How many cards fit across a grid this many dp wide (after the page's side padding). */
    private fun columns(widthDp: Int): Int = with(PlaylistGridCells) {
        Density(1f).calculateCrossAxisCellSizes(widthDp, PlaylistGridSpacing.value.toInt()).size
    }

    @Test
    fun `phones show three small cards across, from the smallest to the largest`() {
        assertEquals(3, columns(328)) // 360dp phone
        assertEquals(3, columns(380)) // 412dp phone
        assertEquals(3, columns(408)) // 440dp phone
    }

    @Test
    fun `tablet-wide grids switch to roomier cards instead of shrinking them into many columns`() {
        assertEquals(4, columns(641)) // unfolded foldable
        assertEquals(4, columns(768)) // tablet, portrait
        assertEquals(7, columns(1248)) // tablet, landscape
    }
}
