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
    fun `monochrome and empty art fall back while meaningful color beats small details`() {
        assertNull(selectArtworkSeed(emptyList()))
        assertNull(selectArtworkSeed(listOf(ArtworkColorCandidate(Color.White.toArgb(), 90), ArtworkColorCandidate(Color.Black.toArgb(), 10))))
        val dominant = Color(0xFF2855BB).toArgb()
        assertEquals(dominant, selectArtworkSeed(listOf(ArtworkColorCandidate(dominant, 80), ArtworkColorCandidate(Color.Red.toArgb(), 1), ArtworkColorCandidate(Color.White.toArgb(), 19))))
    }

    @Test
    fun `art without meaningful colour has no palette, so screens keep the listener's theme`() {
        assertNull(extractArtworkPalette(emptyList(), average))
        assertNull(extractArtworkPalette(listOf(swatch(0xFFFFFFFF, 90), swatch(0xFF000000, 10)), average))
        // Under 1% of the cover is a detail, not the cover's colour.
        assertNull(extractArtworkPalette(listOf(swatch(0xFFF4F1EA, 1000), swatch(0xFFE53935, 5)), average))
    }

    @Test
    fun `coverage and colourfulness both count toward the seed`() {
        val dullBlue = swatch(0xFF4A5A78, 70)
        val orange = swatch(0xFFFF6D00, 15)
        // 70% of the cover outweighs a vivid colour on 15% of it.
        assertEquals(dullBlue.argb, selectArtworkSeed(listOf(dullBlue, orange, swatch(0xFF101010, 15))))
        // With similar coverage the more colourful one wins.
        val greyTeal = swatch(0xFF5F7D7A, 40)
        val red = swatch(0xFFD32F2F, 35)
        assertEquals(red.argb, selectArtworkSeed(listOf(greyTeal, red, swatch(0xFFFFFFFF, 25))))
    }

    @Test
    fun `the palette carries the seed and the cover's mean colour`() {
        val teal = swatch(0xFF008577, 15)
        val palette = extractArtworkPalette(listOf(swatch(0xFFF3EEDC, 85), teal), average)!!
        assertEquals(teal.argb, palette.seed)
        assertEquals(average, palette.average)
    }
}
