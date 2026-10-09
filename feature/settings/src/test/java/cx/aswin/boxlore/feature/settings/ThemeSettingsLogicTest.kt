package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.prefs.ThemeSelection
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import cx.aswin.boxlore.feature.settings.pages.CUSTOM_THEME_KEY
import cx.aswin.boxlore.feature.settings.pages.appearanceDisplayMode
import cx.aswin.boxlore.feature.settings.pages.automaticSurfaceStyle
import cx.aswin.boxlore.feature.settings.pages.selectThemeLook
import cx.aswin.boxlore.feature.settings.pages.selectedLookKey
import cx.aswin.boxlore.feature.settings.pages.themeLookOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsLogicTest {
    private val classic = AppearanceUiState("system", false, "violet", SurfaceStyles.CLASSIC_DYNAMIC)

    @Test fun pickerOffersOnlyThreeDefaultsAndCustom() {
        assertEquals(listOf("classic_dynamic", "dynamic_oled_white", "standard", "custom"), themeLookOptions().map { it.key })
        assertTrue(themeLookOptions().none { it.isPreset })
    }

    @Test fun legacyPresetAndCustomColorsBecomeCustomWithoutChangingTheirInputs() {
        listOf("preset:paper", "preset:voltage", "highcontrast").forEach { style ->
            assertEquals(CUSTOM_THEME_KEY, classic.copy(currentSurfaceStyle = style).selectedLookKey())
        }
        listOf("emerald", "#884466", "exact:#884466", "custom:1:#884466:auto:auto").forEach { brand ->
            assertEquals(CUSTOM_THEME_KEY, classic.copy(currentThemeBrand = brand).selectedLookKey())
        }
        assertEquals("standard", classic.copy(currentSurfaceStyle = "standard", isDynamicColorEnabled = true).selectedLookKey())
    }

    @Test fun legacyModeLocksAreShownAccuratelyAndUnlockToTheirMatchingSurface() {
        assertEquals("dark", appearanceDisplayMode(classic.copy(currentSurfaceStyle = "amoled")))
        assertEquals("light", appearanceDisplayMode(classic.copy(currentSurfaceStyle = "classic_light")))
        assertEquals("dynamic_oled_white", automaticSurfaceStyle("amoled"))
        assertEquals("classic_dynamic", automaticSurfaceStyle("classic_light"))
        assertEquals("preset:paper", automaticSurfaceStyle("preset:paper"))
    }

    @Test fun themeChoiceIsAtomicAndRemembersAnExistingPresetBeforeLeavingIt() {
        val state = classic.copy(currentThemeBrand = "preset:paper", currentSurfaceStyle = "preset:paper")
        var result: Pair<ThemeSelection, ThemeSelection?>? = null
        val actions = AppearanceActions({}, {}, {}, {}, onApplyTheme = { theme, saved -> result = theme to saved })
        selectThemeLook(themeLookOptions().first { it.key == "standard" }, state, actions)
        assertEquals(ThemeSelection("violet", "standard", true), result?.first)
        assertEquals(ThemeSelection("preset:paper", "preset:paper"), result?.second)
    }

    @Test fun customCanBeRestoredAfterSwitchingToADefault() {
        val saved = ThemeSelection("custom:1:#884466:#006677:auto", "dynamic_oled_white")
        var applied: ThemeSelection? = null
        val actions = AppearanceActions({}, {}, {}, {}, onApplyTheme = { theme, _ -> applied = theme })
        selectThemeLook(themeLookOptions().last(), classic.copy(savedCustomTheme = saved), actions)
        assertEquals(saved, applied)
    }

    @Test fun reselectingADefaultOrUnsavedCustomNeverResetsColors() {
        val calls = mutableListOf<ThemeSelection>()
        val actions = AppearanceActions({}, {}, {}, {}, onApplyTheme = { theme, _ -> calls += theme })
        selectThemeLook(themeLookOptions().first(), classic, actions)
        selectThemeLook(themeLookOptions().last(), classic, actions)
        assertTrue(calls.isEmpty())
    }
}
