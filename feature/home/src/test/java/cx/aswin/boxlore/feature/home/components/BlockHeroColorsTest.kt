package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import cx.aswin.boxlore.core.designsystem.theme.ArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Block takes the hero colour choice: the app theme until picked, then artwork or dark. */
class BlockHeroColorsTest {
    private val theme = lightColorScheme(primary = Color(0xFF5145D8))
    private val palette = ArtworkPalette(seed = Color(0xFF2E9D5B).toArgb(), average = Color.Gray.toArgb())

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test fun `a listener who never picked keeps Block in the app theme`() {
        val mode = HeroCoverShade.forStyle(HeroCardStyle.BLOCK, HeroCoverShade.UNSET)
        assertEquals(HeroCoverShade.THEME, mode)
        assertSame(theme, blockColorScheme(mode, palette, theme, isDark = false, SurfaceStyles.STANDARD))
    }

    @Test fun `artwork builds Block from the cover's colour and falls back to the theme without one`() {
        val artwork = blockColorScheme(HeroCoverShade.ARTWORK, palette, theme, isDark = false, SurfaceStyles.STANDARD)
        assertNotEquals(theme.primary, artwork.primary)
        assertSame(theme, blockColorScheme(HeroCoverShade.ARTWORK, null, theme, isDark = false, SurfaceStyles.STANDARD))
    }

    @Test fun `dark keeps Block near black in light mode too, with readable words and buttons`() {
        val dark = blockColorScheme(HeroCoverShade.DARK, palette, theme, isDark = false, SurfaceStyles.STANDARD)
        assertSame(BlockDarkColors, dark)
        // A dark background makes Block use the primary container as its card.
        assertEquals(dark.primaryContainer, HomeHeroStyle.container(dark))
        assertTrue(dark.primaryContainer.luminance() < 0.05f)
        assertTrue(contrast(dark.onPrimaryContainer, dark.primaryContainer) >= 4.5f)
        // Play: the light foreground on the card colour; Details: its on colour on the mixed container.
        assertTrue(contrast(HomeHeroStyle.foreground(dark), HomeHeroStyle.container(dark)) >= 4.5f)
        assertTrue(contrast(dark.onPrimaryContainer, HomeHeroStyle.secondaryContainer(dark)) >= 4.5f)
    }
}
