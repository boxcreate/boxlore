package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance as relativeLuminance
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalColorSchemeTest {
    @Test fun `custom colors round trip and malformed keys fail closed`() {
        val seeds = CustomThemeSeeds(Color(0xFF4222EE), Color(0xFF007B66), Color(0xFFAE561A))
        assertEquals(seeds, CustomThemeSeeds.decode(seeds.encode()))
        assertEquals(CustomThemeSeeds(Color.Red), CustomThemeSeeds.decode("custom:1:#FF0000:auto:auto"))
        listOf("custom:2:#FF0000:auto:auto", "custom:1:#GG0000:auto:auto", "custom:1:#FF0000:bad:auto", "custom:1:#FF0000:auto", "violet").forEach { assertNull(CustomThemeSeeds.decode(it)) }
    }

    @Test fun `all accent and surface text pairs stay readable for adversarial colors in both modes`() {
        listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black, Color(0xFF4222EE)).forEach { seed ->
            listOf(false, true).forEach { dark ->
                listOf(SurfaceStyles.STANDARD, SurfaceStyles.CLASSIC_DYNAMIC, SurfaceStyles.DYNAMIC_OLED_WHITE).forEach { style ->
                    val scheme = generatePersonalColorScheme(CustomThemeSeeds(seed, Color.Yellow, Color.Cyan), dark, style)
                    assertReadablePairs(scheme, "$seed $dark $style")
                }
            }
        }
    }

    @Test fun `pure surfaces and artwork mode rules are preserved`() {
        assertEquals(Color.Black, generatePersonalColorScheme(CustomThemeSeeds(Color.Red), true, SurfaceStyles.DYNAMIC_OLED_WHITE).surface)
        assertEquals(Color.White, generatePersonalColorScheme(CustomThemeSeeds(Color.Red), false, SurfaceStyles.DYNAMIC_OLED_WHITE).surface)
        assertEquals("dynamic_oled_white", artworkSurfaceStyle("dynamic_oled_white"))
        assertEquals("standard", artworkSurfaceStyle("preset:paper"))
        assertEquals("standard", artworkSurfaceStyle("classic_dynamic"))
    }

    @Test fun `monochrome and empty art fall back while meaningful color beats small details`() {
        assertNull(selectArtworkSeed(emptyList()))
        assertNull(selectArtworkSeed(listOf(ArtworkColorCandidate(Color.White.toArgb(), 90), ArtworkColorCandidate(Color.Black.toArgb(), 10))))
        val dominant = Color(0xFF2855BB).toArgb()
        assertEquals(dominant, selectArtworkSeed(listOf(ArtworkColorCandidate(dominant, 80), ArtworkColorCandidate(Color.Red.toArgb(), 1), ArtworkColorCandidate(Color.White.toArgb(), 19))))
    }

    @Test fun `navbar shares the page palette without changing other entries or error semantics`() {
        val base = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), true)
        val art = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), true)
        val merged = navigationSchemeWithArtwork(base, art)
        assertEquals(art.primary, merged.primary)
        assertEquals(art.onPrimary, merged.onPrimary)
        assertEquals(art.primaryFixed, merged.primaryFixed)
        assertEquals(art.surface, merged.surface)
        assertEquals(art.surfaceContainerHigh, merged.surfaceContainerHigh)
        assertEquals(art.onSurface, merged.onSurface)
        assertEquals(base.error, merged.error)
        val entries = ArtworkNavigationColors()
        entries.update("old", base)
        entries.update("new", art)
        entries.update("old", null)
        assertEquals(art, entries.colorsFor("new"))
        assertNull(entries.colorsFor("home"))
    }

    private fun assertReadablePairs(scheme: ColorScheme, context: String) {
        listOf(
            scheme.primary to scheme.onPrimary,
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondary to scheme.onSecondary,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiary to scheme.onTertiary,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.primaryFixed to scheme.onPrimaryFixed,
            scheme.primaryFixed to scheme.onPrimaryFixedVariant,
            scheme.secondaryFixed to scheme.onSecondaryFixed,
            scheme.secondaryFixed to scheme.onSecondaryFixedVariant,
            scheme.tertiaryFixed to scheme.onTertiaryFixed,
            scheme.tertiaryFixed to scheme.onTertiaryFixedVariant,
            scheme.surface to scheme.onSurface,
            scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        ).forEach { (background, foreground) ->
            assertTrue(contrast(background, foreground) >= 4.49, "$context contrast ${contrast(background, foreground)}")
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = a.relativeLuminance().toDouble()
        val y = b.relativeLuminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
