package cx.aswin.boxlore.feature.home.settings

import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.home.settings.pages.AppearanceActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppearanceActionsTrackedTest {

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
}
