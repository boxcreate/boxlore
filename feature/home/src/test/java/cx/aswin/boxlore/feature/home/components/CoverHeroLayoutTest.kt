package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import cx.aswin.boxlore.core.designsystem.theme.ArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverHeroLayoutTest {
    private val primary = Color(0xFF5145D8)
    private val palette = ArtworkPalette(seed = Color(0xFF2855BB).toArgb(), average = Color.Gray.toArgb())

    @Test fun `each shade option picks its colour source`() {
        assertEquals(ArtworkShade.Neutral, coverShade(HeroCoverShade.DARK, palette, primary))
        assertEquals(ArtworkShade.fromColor(primary), coverShade(HeroCoverShade.THEME, palette, primary))
        assertEquals(ArtworkShade.fromPalette(palette), coverShade(HeroCoverShade.ARTWORK, palette, primary))
    }

    @Test fun `artwork without meaningful colour and unknown options fall back safely`() {
        assertEquals(ArtworkShade.Neutral, coverShade(HeroCoverShade.ARTWORK, null, primary))
        assertEquals(ArtworkShade.fromPalette(palette), coverShade("sepia", palette, primary))
    }

    @Test fun `fewer episodes get bigger tiles`() {
        assertEquals(emptyList<List<Int>>(), coverGridRows(0))
        assertEquals(listOf(listOf(0)), coverGridRows(1))
        assertEquals(listOf(listOf(0), listOf(1)), coverGridRows(2))
        assertEquals(listOf(listOf(0), listOf(1, 2)), coverGridRows(3))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), coverGridRows(4))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), coverGridRows(6))
    }

    @Test fun `only the top tile of three gets a taller row, and tiles alone in a row are wide`() {
        assertEquals(1.2f, coverGridRowWeight(3, 0), 0f)
        assertEquals(1f, coverGridRowWeight(3, 1), 0f)
        assertEquals(1f, coverGridRowWeight(4, 0), 0f)
        assertTrue(coverGridTileIsWide(2, 1))
        assertTrue(coverGridTileIsWide(3, 0))
        assertFalse(coverGridTileIsWide(3, 1))
        assertFalse(coverGridTileIsWide(4, 0))
    }
}
