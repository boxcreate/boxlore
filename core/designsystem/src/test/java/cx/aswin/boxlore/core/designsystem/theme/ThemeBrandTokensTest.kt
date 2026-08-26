package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeBrandTokensTest {
    @Test
    fun `brand seeds contains twenty named palettes`() {
        assertEquals(20, BrandSeeds.size)
        assertTrue(BrandSeeds.containsKey("violet"))
        assertTrue(BrandSeeds.containsKey("rust"))
    }

    @Test
    fun `is custom theme brand accepts hex seeds and exact pins`() {
        assertTrue(isCustomThemeBrand("#5B5BD6"))
        assertTrue(isCustomThemeBrand("exact:#5B5BD6"))
        assertTrue(isExactThemeBrand("exact:#5B5BD6"))
        assertFalse(isExactThemeBrand("#5B5BD6"))
        assertFalse(isCustomThemeBrand("violet"))
        assertFalse(isCustomThemeBrand("#abc"))
        assertFalse(isExactThemeBrand("exact:nope"))
        assertEquals("#5B5BD6", customThemeBrandHex("exact:#5B5BD6"))
        assertEquals("#5B5BD6", customThemeBrandHex("#5B5BD6"))
        assertEquals("exact:#6750A4", Color(0xFF6750A4).toExactThemeBrandKey())
    }

    @Test
    fun `resolve theme seed color falls back to violet for unknown keys`() {
        assertEquals(BrandSeeds["violet"]!!.second, resolveThemeSeedColor("unknown"))
    }

    @Test
    fun `resolve theme seed color parses custom hex or falls back on invalid`() {
        val resolved = resolveThemeSeedColor("#006C4C")
        assertTrue(
            resolved == BrandSeeds["emerald"]!!.second ||
                resolved == BrandSeeds["violet"]!!.second,
        )
        assertEquals(BrandSeeds["violet"]!!.second, resolveThemeSeedColor("#not-a-color"))
        assertEquals(resolveThemeSeedColor("#006C4C"), resolveThemeSeedColor("exact:#006C4C"))
    }

    @Test
    fun `to theme brand hex strips alpha`() {
        assertEquals("#6750A4", Color(0xFF6750A4).toThemeBrandHex())
    }

    @Test
    fun `contrast color picks black on light and white on dark`() {
        assertEquals(Color.Black, Color.White.contrastColor())
        assertEquals(Color.White, Color.Black.contrastColor())
    }

    @Test
    fun `brand seeds differ by name`() {
        assertTrue(BrandSeeds["violet"]!!.second != BrandSeeds["emerald"]!!.second)
        assertTrue(BrandSeeds["violet"]!!.second != BrandSeeds["ocean"]!!.second)
    }

    @Test
    fun `pinned primary keeps the seed rgb`() {
        val seed = Color(0xFF00C853)
        val base = androidx.compose.material3.lightColorScheme(primary = Color.Red)
        val pinned = base.withPinnedPrimary(seed)
        assertEquals(seed.toArgb(), pinned.primary.toArgb())
        assertEquals(seed.contrastColor().toArgb(), pinned.onPrimary.toArgb())
    }

    @Test
    fun `compute effective dark theme honors locked backgrounds`() {
        assertTrue(computeEffectiveDarkTheme(SurfaceStyles.AMOLED, darkTheme = false))
        assertFalse(computeEffectiveDarkTheme(SurfaceStyles.PURE_WHITE, darkTheme = true))
        assertTrue(computeEffectiveDarkTheme(SurfaceStyles.CLASSIC_DYNAMIC, darkTheme = true))
        assertFalse(computeEffectiveDarkTheme(SurfaceStyles.CLASSIC_DYNAMIC, darkTheme = false))
    }
}
