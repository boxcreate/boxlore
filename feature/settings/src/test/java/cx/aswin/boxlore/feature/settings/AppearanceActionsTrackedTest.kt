package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppearanceActionsTrackedTest {

    @Test
    fun `saving a custom draft is separate from selecting or remembering an existing theme`() {
        val selection = cx.aswin.boxlore.core.prefs.ThemeSelection("custom", "standard")
        val calls = mutableListOf<String>()
        val actions = AppearanceActions({}, {}, {}, {}, onApplyTheme = { _, _ -> calls.add("select") }, onSaveCustomTheme = { calls.add("save") }).trackedForAnalytics()
        actions.onApplyTheme(selection, selection)
        assertEquals(listOf("select"), calls)
        actions.onSaveCustomTheme(selection)
        assertEquals(listOf("select", "save"), calls)
    }

    @Test
    fun `complete preset selection forwards one action without changing mode or individual controls`() {
        val calls = mutableListOf<String>()
        val tracked = AppearanceActions(
            onSetThemeConfig = { calls.add("mode") },
            onToggleDynamicColor = { calls.add("wallpaper") },
            onSetThemeBrand = { calls.add("accent") },
            onSetSurfaceStyle = { calls.add("background") },
            onSetThemePreset = { calls.add(it) },
        ).trackedForAnalytics()

        tracked.onSetThemePreset("preset:moss")

        assertEquals(listOf("preset:moss"), calls)
    }

    @Test
    fun `trackedForAnalytics preserves miniplayer seek toggle in both directions`() {
        val recordedValues = mutableListOf<Boolean>()
        val tracked = AppearanceActions(
            onSetThemeConfig = {},
            onToggleDynamicColor = {},
            onSetThemeBrand = {},
            onSetSurfaceStyle = {},
            onSetMiniPlayerSeekButtonsEnabled = { recordedValues.add(it) },
        ).trackedForAnalytics()

        tracked.onSetMiniPlayerSeekButtonsEnabled(true)
        tracked.onSetMiniPlayerSeekButtonsEnabled(false)

        assertEquals(listOf(true, false), recordedValues)
    }

    @Test
    fun `trackedForAnalytics forwards onSetSubscriptionsTabStyle`() {
        var recordedStyle: String? = null
        val baseActions =
            AppearanceActions(
                onSetThemeConfig = {},
                onToggleDynamicColor = {},
                onSetThemeBrand = {},
                onSetSurfaceStyle = {},
                onSetSubscriptionsTabStyle = { recordedStyle = it },
            )

        val tracked = baseActions.trackedForAnalytics()
        tracked.onSetSubscriptionsTabStyle(SubscriptionsTabStyle.FLOATING)

        assertEquals(SubscriptionsTabStyle.FLOATING, recordedStyle)
    }

    @Test
    fun `trackedForAnalytics forwards other navigation and tab callbacks`() {
        var recordedDefaultTab: String? = null
        var recordedNavStyle: String? = null
        var recordedFontRoundness: String? = null

        val baseActions =
            AppearanceActions(
                onSetThemeConfig = {},
                onToggleDynamicColor = {},
                onSetThemeBrand = {},
                onSetSurfaceStyle = {},
                onSetSubscriptionsDefaultTab = { recordedDefaultTab = it },
                onSetNavigationStyle = { recordedNavStyle = it },
                onSetFontRoundness = { recordedFontRoundness = it },
            )

        val tracked = baseActions.trackedForAnalytics()
        tracked.onSetSubscriptionsDefaultTab("new_episodes")
        tracked.onSetNavigationStyle("classic")
        tracked.onSetFontRoundness("sharp")

        assertEquals("new_episodes", recordedDefaultTab)
        assertEquals("classic", recordedNavStyle)
        assertEquals("sharp", recordedFontRoundness)
    }

    @Test
    fun `tracked actions forward artwork switch and cohesive custom selection`() {
        var enabled: Boolean? = null
        var saved: cx.aswin.boxlore.core.prefs.ThemeSelection? = null
        val selection = cx.aswin.boxlore.core.prefs.ThemeSelection("custom:1:#4422EE:auto:auto", "standard")
        val actions = AppearanceActions({}, {}, {}, {}, onSetArtworkColorsEnabled = { enabled = it }, onApplyTheme = { _, custom -> saved = custom }).trackedForAnalytics()
        actions.onSetArtworkColorsEnabled(false)
        actions.onApplyTheme(selection, selection)
        assertEquals(false, enabled)
        assertEquals(selection, saved)
    }
}
