package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `the Fold 8 cover and split halves keep three cards, which never shrink as the window grows`() {
        for (width in listOf(411, 443, 488, 520, 567)) assertEquals(3, columns(width), "$width")
        val cardWidths = (411..1248).map { width -> cardWidth(width) }
        // From the Fold 8 cover up, a card is never narrower than it is there.
        assertTrue(cardWidths.all { it >= cardWidth(411) })
        assertTrue(isPlaylistWideGrid(568.dp) && !isPlaylistWideGrid(567.dp))
    }

    @Test
    fun `a playlist page's show covers keep phone size and grow to fill wider windows`() {
        assertEquals(100.dp, playlistShowTileSize(320.dp, 360f))
        assertEquals(100.dp, playlistShowTileSize(372.dp, 412f))
        val cover = playlistShowTileSize(435.dp, 475f)
        assertTrue(cover > 100.dp && cover <= 140.dp, "$cover")
        val columns = playlistShowColumns(435.dp)
        assertEquals(435f, (cover * columns + 12.dp * (columns - 1)).value, 1f)
    }

    private fun cardWidth(widthDp: Int): Int = with(PlaylistGridCells) {
        Density(1f).calculateCrossAxisCellSizes(widthDp, PlaylistGridSpacing.value.toInt()).first()
    }

    @Test
    fun `tablet-wide grids switch to roomier cards instead of shrinking them into many columns`() {
        assertEquals(4, columns(641)) // unfolded foldable
        assertEquals(4, columns(768)) // tablet, portrait
        assertEquals(7, columns(1248)) // tablet, landscape
    }

    @Test
    fun `a playlist page fits its show covers to the width, never fewer than one`() {
        assertEquals(2, playlistShowColumns(320.dp)) // 360dp phone
        assertEquals(3, playlistShowColumns(372.dp)) // 412dp phone
        assertEquals(1, playlistShowColumns(80.dp))
    }
}
