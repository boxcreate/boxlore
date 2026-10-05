package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemeCollection
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import cx.aswin.boxlore.feature.settings.pages.selectedThemeLookKey
import cx.aswin.boxlore.feature.settings.pages.themeLookOptions
import cx.aswin.boxlore.feature.settings.pages.themeSelectionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsLogicTest {
    @Test fun allLooksIncludeExistingBackgroundsAndSixteenDistinctPresets() {
        val looks = themeLookOptions()
        assertEquals(19, looks.size)
        assertEquals(19, looks.map { it.key }.distinct().size)
        assertEquals(16, looks.count { it.isPreset })
        assertTrue(looks.all { it.name.isNotBlank() && it.description.isNotBlank() })
    }

    @Test fun collectionFiltersPartitionChoicesWithoutChangingKeys() {
        val filtered = ThemeCollection.entries.flatMap { collection ->
            val looks = themeLookOptions(collection)
            assertTrue(looks.isNotEmpty())
            assertTrue(looks.all { it.collection == collection })
            looks
        }
        assertEquals(themeLookOptions().map { it.key }.toSet(), filtered.map { it.key }.toSet())
        assertEquals(themeLookOptions().size, filtered.size)
        assertTrue(themeLookOptions(ThemeCollection.MINIMAL).any { it.key == SurfaceStyles.DYNAMIC_OLED_WHITE })
        assertTrue(themeLookOptions(ThemeCollection.BOLD).all { it.isPreset })
    }

    @Test fun legacyModesKeepTheirBackgroundSelectionAndUnknownStylesFallBack() {
        assertEquals(SurfaceStyles.CLASSIC_DYNAMIC, selectedThemeLookKey(SurfaceStyles.CLASSIC_DARK))
        assertEquals(SurfaceStyles.CLASSIC_DYNAMIC, selectedThemeLookKey(SurfaceStyles.CLASSIC_LIGHT))
        assertEquals(SurfaceStyles.DYNAMIC_OLED_WHITE, selectedThemeLookKey(SurfaceStyles.AMOLED))
        assertEquals(SurfaceStyles.DYNAMIC_OLED_WHITE, selectedThemeLookKey(SurfaceStyles.PURE_WHITE))
        assertEquals(SurfaceStyles.STANDARD, selectedThemeLookKey("unknown"))
        assertEquals("preset:voltage", selectedThemeLookKey("preset:voltage"))
    }

    @Test fun summaryDistinguishesModeCompletePaletteAndCustomizedAccents() {
        val state = AppearanceUiState("dark", false, "preset:paper", "preset:paper")
        assertEquals("Paper · Dark · Paper colors", themeSelectionSummary(state))
        assertEquals("Paper · Light · Wallpaper colors", themeSelectionSummary(state.copy(currentThemeConfig = "light", isDynamicColorEnabled = true)))
        assertEquals("Paper · System · Custom accent", themeSelectionSummary(state.copy(currentThemeConfig = "system", currentThemeBrand = "#884466")))
    }
}
