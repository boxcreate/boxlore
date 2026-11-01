package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.prefs.ExploreDefaultTab
import cx.aswin.boxlore.core.prefs.NavigationBlurLevel
import cx.aswin.boxlore.core.prefs.OpenAppTo
import cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsPictureGrid
import cx.aswin.boxlore.feature.settings.components.SettingsPictureTile
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsTileSection

/** Navigation: the bar's style as picture tiles, its blur, then where boxlore starts and which tabs pages open to. */
@Composable
internal fun AppearanceNavigationPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    TrackScreenView("appearance_navigation")
    val bar = NavigationStyle.fromKey(state.currentNavigationStyle)
    SettingsScaffold(title = "Navigation", onBack = onBack) {
        SettingsTileSection(title = "Navigation bar") {
            SettingsPictureGrid(NavigationStyle.entries, columns = 2) { style, modifier ->
                SettingsPictureTile(
                    label = style.label,
                    selected = bar == style,
                    onClick = { actions.onSetNavigationStyle(style.key) },
                    modifier = modifier,
                ) {
                    NavigationBarPicture(style)
                }
            }
        }
        // The page blurs under the floating bar only, so the strength picker shows only with it.
        if (bar == NavigationStyle.Floating) {
            SettingsGroup {
                SettingsContent {
                    AppearanceChoicePicker(
                        "Blur behind the bar",
                        listOf(NavigationBlurLevel.OFF to "Off", NavigationBlurLevel.SOFT to "Soft", NavigationBlurLevel.MEDIUM to "Medium", NavigationBlurLevel.STRONG to "Strong"),
                        NavigationBlurLevel.sanitize(state.navigationBlurLevel),
                        actions.onSetNavigationBlurLevel,
                    )
                }
            }
        }
        SettingsGroup(title = "Start page & tabs", footer = "Start page applies when you relaunch boxlore. Links can open a different page or tab.") {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AppearanceChoicePicker("Start page", listOf(OpenAppTo.HOME to "Home", OpenAppTo.SUBSCRIPTIONS to "Subscriptions", OpenAppTo.DOWNLOADS to "Downloads"), state.currentOpenAppTo, actions.onSetOpenAppTo)
                    // Without Picked for you, Explore has no For you tab and always opens to Top.
                    if (state.discoverySections.pickedForYou) {
                        AppearanceChoicePicker("Explore opens to", listOf(ExploreDefaultTab.FOR_YOU to "For You", ExploreDefaultTab.TOP to "Top"), ExploreDefaultTab.sanitize(state.currentExploreDefaultTab), actions.onSetExploreDefaultTab)
                    }
                    AppearanceChoicePicker("Subscriptions opens to", listOf(SubscriptionsDefaultTab.SHOWS to "Shows", SubscriptionsDefaultTab.NEW_EPISODES to "New episodes"), SubscriptionsDefaultTab.sanitize(state.currentSubscriptionsDefaultTab), actions.onSetSubscriptionsDefaultTab)
                    AppearanceChoicePicker("Subscriptions tab position", listOf(SubscriptionsTabStyle.TOP to "Top", SubscriptionsTabStyle.FLOATING to "Floating"), SubscriptionsTabStyle.sanitize(state.currentSubscriptionsTabStyle), actions.onSetSubscriptionsTabStyle)
                }
            }
        }
    }
}
