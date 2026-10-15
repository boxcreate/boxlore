package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtworkPaletteTest {
    private val average = Color.Gray.toArgb()
    private fun swatch(argb: Long, population: Int) = ArtworkColorCandidate(Color(argb).toArgb(), population)

    @Test
    fun `art without meaningful colour has no palette, so pages keep the listener's theme`() {
        assertNull(extractArtworkPalette(emptyList(), average))
        assertNull(extractArtworkPalette(listOf(swatch(0xFFFFFFFF, 90), swatch(0xFF000000, 10)), average))
        // A speck under 1.5% of the cover is detail, not the cover's colour.
        assertNull(extractArtworkPalette(listOf(swatch(0xFFF4F1EA, 89), swatch(0xFF111111, 10), swatch(0xFFE53935, 1)), average))
        assertNull(extractArtworkPalette(listOf(swatch(0xFFE53935, 0), swatch(0xFF111111, 10)), average))
    }

    @Test
    fun `a mostly neutral cover keeps a neutral ground and still takes its colour as the accent`() {
        val cream = swatch(0xFFF3EEDC, 85)
        val teal = swatch(0xFF008577, 15)
        val palette = extractArtworkPalette(listOf(cream, teal), average)!!
        assertEquals(cream.argb, palette.ground)
        assertEquals(teal.argb, palette.accent)
        assertEquals(average, palette.average)
    }

    @Test
    fun `a family's whole area counts, so a textured field beats one flat swatch`() {
        val tealField = listOf(0xFF0E7C7B, 0xFF138A85, 0xFF0B6E6C, 0xFF16928C, 0xFF0F8380, 0xFF127674).map { swatch(it, 8) }
        val pink = swatch(0xFFE91E63, 30)
        val palette = extractArtworkPalette(tealField + pink + swatch(0xFFFFFFFF, 22), average)!!
        assertEquals(tealField.maxBy { it.population }.argb, palette.ground)
    }

    @Test
    fun `colour that covers at least a fifth of the cover becomes the ground`() {
        val red = swatch(0xFFC62828, 30)
        val palette = extractArtworkPalette(listOf(red, swatch(0xFF000000, 40), swatch(0xFFFFFFFF, 30)), average)!!
        assertEquals(red.argb, palette.ground)
        assertEquals(red.argb, palette.accent)
    }

    @Test
    fun `a vivid colour wins the accent once it covers enough of the cover`() {
        val dullBlue = swatch(0xFF4A5A78, 70)
        val orange = swatch(0xFFFF6D00, 15)
        val vivid = extractArtworkPalette(listOf(dullBlue, orange, swatch(0xFF101010, 15)), average)!!
        assertEquals(dullBlue.argb, vivid.ground)
        assertEquals(orange.argb, vivid.accent)
        // Under 4% it only counts when nothing else qualifies.
        val speck = extractArtworkPalette(listOf(dullBlue, swatch(0xFFFF6D00, 3), swatch(0xFF101010, 27)), average)!!
        assertEquals(dullBlue.argb, speck.accent)
        val onlySpeck = extractArtworkPalette(listOf(swatch(0xFFFF6D00, 3), swatch(0xFF101010, 97)), average)!!
        assertEquals(Color(0xFFFF6D00).toArgb(), onlySpeck.accent)
    }
}
