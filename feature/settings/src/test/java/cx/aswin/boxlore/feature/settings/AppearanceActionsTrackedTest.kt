package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppearanceActionsTrackedTest {
    @Test
    fun `player style callback survives the appearance wrapper without toggling miniplayer`() {
        val styles = mutableListOf<String>()
        var miniChanges = 0
        val tracked = AppearanceActions(
            {},
            {},
            {},
            {},
            onSetFullPlayerStyle = { styles.add(it) },
            onSetMiniPlayerSeekButtonsEnabled = { miniChanges++ },
        ).trackedForAnalytics()
        tracked.onSetFullPlayerStyle("bunpod")
        tracked.onSetFullPlayerStyle("standard")
        assertEquals(listOf("bunpod", "standard"), styles)
        assertEquals(0, miniChanges)
    }

    @Test
    fun `LeaveMeAlone switches reach the setter with their section and value`() {
        val calls = mutableListOf<Pair<cx.aswin.boxlore.core.prefs.DiscoverySection, Boolean>>()
        val tracked = trackedDiscoverySectionSetter(cx.aswin.boxlore.core.prefs.DiscoverySections()) { section, on -> calls.add(section to on) }
        tracked(cx.aswin.boxlore.core.prefs.DiscoverySection.PICKED_FOR_YOU, false)
        tracked(cx.aswin.boxlore.core.prefs.DiscoverySection.MORE_LIKE_THIS, true)
        assertEquals(
            listOf(
                cx.aswin.boxlore.core.prefs.DiscoverySection.PICKED_FOR_YOU to false,
                cx.aswin.boxlore.core.prefs.DiscoverySection.MORE_LIKE_THIS to true,
            ),
            calls,
        )
    }

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

    @Test
    fun `tracked actions forward the hero card style and the Cover shade separately`() {
        var style: String? = null
        var shade: String? = null
        val actions = AppearanceActions({}, {}, {}, {}, onSetHeroCardStyle = { style = it }, onSetHeroCoverShade = { shade = it }).trackedForAnalytics()
        actions.onSetHeroCardStyle(cx.aswin.boxlore.core.prefs.HeroCardStyle.COVER)
        assertEquals(cx.aswin.boxlore.core.prefs.HeroCardStyle.COVER, style)
        assertEquals(null, shade)
        actions.onSetHeroCoverShade(cx.aswin.boxlore.core.prefs.HeroCoverShade.THEME)
        assertEquals(cx.aswin.boxlore.core.prefs.HeroCoverShade.THEME, shade)
        assertEquals(cx.aswin.boxlore.core.prefs.HeroCardStyle.COVER, style)
    }

    @Test
    fun `hero picks report the full look from the state at the moment of the pick`() {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            var state = AppearanceUiState("system", false, "violet", "standard", heroCardStyle = HeroCardStyle.COVER, heroCoverShade = HeroCoverShade.THEME)
            val actions = AppearanceActions(
                {},
                {},
                {},
                {},
                onSetHeroCardStyle = { state = state.copy(heroCardStyle = it) },
                onSetHeroCoverShade = { state = state.copy(heroCoverShade = it) },
            ).trackedForAnalytics { state }

            actions.onSetHeroCardStyle(HeroCardStyle.DUOTONE)
            actions.onSetHeroCoverShade(HeroCoverShade.DARK)
            // Re-tapping the picked tile or colour keeps the look, so neither event records it.
            actions.onSetHeroCardStyle(HeroCardStyle.DUOTONE)
            actions.onSetHeroCoverShade(HeroCoverShade.DARK)

            val picks = events.filter { it.first == "home_hero_style_selected" }.map { it.second }
            assertEquals(listOf("duotone_theme", "duotone_dark"), picks.map { it["hero_style_combo"] })
            assertEquals(listOf("style", "colors"), picks.map { it["changed"] })
            assertEquals(listOf("cover_theme", "duotone_theme"), picks.map { it["previous_hero_style_combo"] })
            assertEquals(2, events.count { it.first == "settings_interaction" })
            assertEquals(HeroCardStyle.DUOTONE, state.heroCardStyle)
            assertEquals(HeroCoverShade.DARK, state.heroCoverShade)
        } finally {
            restore()
        }
    }

    @Test
    fun `a setting reports only when its value changes`() {
        // Regression: every tap reported, so re-picking the current choice looked like a change.
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(events)
        try {
            var state = AppearanceUiState("system", false, "violet", "standard")
            val actions = AppearanceActions(
                onSetThemeConfig = { state = state.copy(currentThemeConfig = it) },
                onToggleDynamicColor = {},
                onSetThemeBrand = {},
                onSetSurfaceStyle = {},
                onSetFullPlayerStyle = { state = state.copy(fullPlayerStyle = it) },
                onSetMiniPlayerSeekButtonsEnabled = { state = state.copy(miniPlayerSeekButtonsEnabled = it) },
            ).trackedForAnalytics { state }

            actions.onSetThemeConfig("system")
            actions.onSetThemeConfig("dark")
            actions.onSetThemeConfig("dark")
            actions.onSetFullPlayerStyle("bunpod")
            actions.onSetFullPlayerStyle("bunpod")
            actions.onSetMiniPlayerSeekButtonsEnabled(true)

            val keys = events.filter { it.first == "settings_interaction" }.map { it.second["setting_key"] to it.second["value"] }
            assertEquals(
                listOf("theme_mode_changed" to "dark", "full_player_style_changed" to "bunpod", "mini_player_seek_buttons_toggled" to "true"),
                keys,
            )
        } finally {
            restore()
        }
    }
}
