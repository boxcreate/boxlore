package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ArtworkNavigationColorsTest {
    private val base = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), true)
    private val parent = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), true)
    private val child = generatePersonalColorScheme(CustomThemeSeeds(Color.Green), true)

    @Test fun `previewing home leaves source colors intact and a committed entry clears the preview`() {
        val colors = ArtworkNavigationColors()
        colors.beginEntry("episode", base, true)
        colors.update("episode", parent)
        colors.previewAppTheme("home")
        assertEquals("home", colors.appThemePreviewEntryId)
        assertSame(parent, colors.colorsFor("episode"))
        colors.previewAppTheme(null)
        assertNull(colors.appThemePreviewEntryId)
        assertSame(parent, colors.colorsFor("episode"))
        colors.previewAppTheme("home")
        colors.beginEntry("home", base, true)
        assertNull(colors.appThemePreviewEntryId)
        assertSame(parent, colors.initialColorsFor("home"))
    }

    @Test fun `incoming entry captures the visible parent palette before it leaves composition`() {
        val colors = ArtworkNavigationColors()
        colors.beginEntry("episode", base, true)
        colors.update("episode", parent)
        colors.beginEntry("podcast", base, true)
        colors.update("episode", null)
        assertSame(parent, colors.initialColorsFor("podcast"))
        assertSame(parent, colors.colorsFor("podcast"))
        assertSame(parent, colors.transitionColors)
        colors.update("podcast", child)
        assertSame(parent, colors.initialColorsFor("podcast"))
        assertSame(child, colors.colorsFor("podcast"))
    }

    @Test fun `back starts with departing colors and late outgoing updates cannot overwrite it`() {
        val colors = ArtworkNavigationColors()
        colors.beginEntry("episode", base, true)
        colors.update("episode", parent)
        val firstHandoff = colors.handoffKeyFor("episode")
        colors.beginEntry("podcast", base, true)
        colors.update("podcast", child)
        colors.update("episode", null)
        colors.beginEntry("episode", base, true)
        colors.update("podcast", base)
        colors.update("podcast", null)
        assertSame(child, colors.colorsFor("episode"))
        assertSame(child, colors.initialColorsFor("episode"))
        assertSame(child, colors.transitionColors)
        assertNotEquals(firstHandoff, colors.handoffKeyFor("episode"))
        colors.update("episode", parent)
        assertSame(parent, colors.colorsFor("episode"))
        assertSame(child, colors.initialColorsFor("episode"))
    }

    @Test fun `rapid navigation captures the current visible handoff instead of a late parent result`() {
        val colors = ArtworkNavigationColors()
        colors.beginEntry("a", base, true)
        colors.update("a", parent)
        colors.beginEntry("b", base, true)
        colors.beginEntry("c", base, true)
        colors.update("b", child)
        colors.beginEntry("c", base, true)
        assertSame(parent, colors.initialColorsFor("c"))
        assertSame(parent, colors.colorsFor("c"))
    }

    @Test fun `cold entries theme changes and artwork opt out cannot reuse stale colors`() {
        val colors = ArtworkNavigationColors()
        colors.beginEntry("cold", base, true)
        assertSame(base, colors.initialColorsFor("cold"))
        colors.update("cold", parent)
        colors.beginEntry("disabled", base, false)
        assertSame(base, colors.colorsFor("disabled"))
        assertSame(base, colors.transitionColors)
        assertNull(colors.initialColorsFor("cold"))
        colors.beginEntry("reenabled", base, true)
        colors.update("reenabled", parent)
        colors.beginEntry("changed", child, true)
        assertSame(child, colors.colorsFor("changed"))
        assertSame(child, colors.transitionColors)
        assertNull(colors.initialColorsFor("reenabled"))
    }

    @Test fun `navigation snapshots are bounded during long sessions`() {
        val colors = ArtworkNavigationColors()
        repeat(100) { index ->
            colors.beginEntry("$index", base, true)
            if (index > 0) colors.update("${index - 1}", null)
        }
        assertNull(colors.initialColorsFor("0"))
        assertNull(colors.colorsFor("0"))
        assertSame(base, colors.initialColorsFor("99"))
    }

    @Test fun `inherited palettes respect pure and reject a previous light dark mode`() {
        val pure = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), true, SurfaceStyles.DYNAMIC_OLED_WHITE)
        val inherited = artworkLoadingColorScheme(pure, parent, true, SurfaceStyles.DYNAMIC_OLED_WHITE)
        assertEquals(parent.primary, inherited.primary)
        assertEquals(Color.Black, inherited.background)
        assertEquals(pure.surfaceContainerHigh, inherited.surfaceContainerHigh)
        val light = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), false)
        assertSame(light, artworkLoadingColorScheme(light, parent, false, SurfaceStyles.STANDARD))
        assertSame(base, artworkLoadingColorScheme(base, null, true, SurfaceStyles.STANDARD))
    }
}
