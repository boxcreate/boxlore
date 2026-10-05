package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemeCollection
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import cx.aswin.boxlore.feature.settings.pages.selectThemeLook
import cx.aswin.boxlore.feature.settings.pages.selectedThemeLookKey
import cx.aswin.boxlore.feature.settings.pages.themeColorExplanation
import cx.aswin.boxlore.feature.settings.pages.themeLookOptions
import cx.aswin.boxlore.feature.settings.pages.themeLookSections
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
        assertEquals("Paper · System · Custom color", themeSelectionSummary(state.copy(currentThemeConfig = "system", currentThemeBrand = "#884466")))
    }

    @Test fun sectionsKeepAllChoicesVisibleOnceWithoutAFilter() {
        val sections = themeLookSections()
        assertEquals(listOf("Default themes", "Minimal", "Colorful", "Bold"), sections.map { it.title })
        assertEquals(listOf(3, 2, 10, 4), sections.map { it.looks.size })
        assertEquals(themeLookOptions().map { it.key }.toSet(), sections.flatMap { it.looks }.map { it.key }.toSet())
        assertEquals(19, sections.flatMap { it.looks }.map { it.key }.distinct().size)
    }

    @Test fun tappingTheSelectedThemePreservesCustomAndWallpaperColors() {
        val look = themeLookOptions().first { it.key == "preset:paper" }
        val calls = mutableListOf<String>()
        val actions = recordingActions(calls)
        listOf(false, true).forEach { wallpaper ->
            selectThemeLook(look, AppearanceUiState("dark", wallpaper, "#884466", look.key), actions)
        }
        assertTrue(calls.isEmpty())
    }

    @Test fun choosingMaterial3EnablesWallpaperButChoosingAPresetUsesItsOwnColors() {
        val state = AppearanceUiState("dark", false, "#884466", SurfaceStyles.DYNAMIC_OLED_WHITE)
        val calls = mutableListOf<String>()
        selectThemeLook(themeLookOptions().first { it.key == SurfaceStyles.STANDARD }, state, recordingActions(calls))
        assertEquals(listOf("surface:standard", "wallpaper:true"), calls)
        calls.clear()
        selectThemeLook(themeLookOptions().first { it.key == "preset:paper" }, state, recordingActions(calls))
        assertEquals(listOf("preset:preset:paper"), calls)
    }

    @Test fun instructionsExplainWallpaperAndTheEffectOfChangingAColor() {
        val material = themeLookOptions().first { it.key == SurfaceStyles.STANDARD }
        assertTrue(material.description.contains("automatically uses colors from your wallpaper"))
        assertTrue(themeColorExplanation(material).contains("tint the background and cards"))
        themeLookOptions().filter { it.key != SurfaceStyles.STANDARD }.forEach { look ->
            assertTrue(themeColorExplanation(look).contains("buttons, icons and highlights"))
            assertTrue(themeColorExplanation(look).contains("background stays the same"))
        }
    }

    private fun recordingActions(calls: MutableList<String>) = AppearanceActions(
        onSetThemeConfig = { calls += "mode:$it" },
        onToggleDynamicColor = { calls += "wallpaper:$it" },
        onSetThemeBrand = { calls += "brand:$it" },
        onSetSurfaceStyle = { calls += "surface:$it" },
        onSetFontRoundness = {},
        onSetThemePreset = { calls += "preset:$it" },
    )
}
