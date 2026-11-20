package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.prefs.ExploreDefaultTab
import cx.aswin.boxlore.core.prefs.NavigationBlurLevel
import cx.aswin.boxlore.core.prefs.OpenAppTo
import cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.settings.R
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
    SettingsScaffold(title = stringResource(R.string.settings_page_navigation), onBack = onBack) {
        SettingsTileSection(title = stringResource(R.string.settings_navigation_bar_title)) {
            SettingsPictureGrid(NavigationStyle.entries, columns = 2) { style, modifier ->
                SettingsPictureTile(
                    label = stringResource(navigationStyleLabel(style)),
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
                        stringResource(R.string.settings_navigation_blur_title),
                        listOf(NavigationBlurLevel.OFF to stringResource(R.string.settings_navigation_blur_off), NavigationBlurLevel.SOFT to stringResource(R.string.settings_navigation_blur_soft), NavigationBlurLevel.MEDIUM to stringResource(R.string.settings_navigation_blur_medium), NavigationBlurLevel.STRONG to stringResource(R.string.settings_navigation_blur_strong)),
                        NavigationBlurLevel.sanitize(state.navigationBlurLevel),
                        actions.onSetNavigationBlurLevel,
                    )
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_navigation_start_title), footer = stringResource(R.string.settings_navigation_start_footer)) {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AppearanceChoicePicker(stringResource(R.string.settings_navigation_start_page), listOf(OpenAppTo.HOME to stringResource(R.string.settings_navigation_start_home), OpenAppTo.SUBSCRIPTIONS to stringResource(R.string.settings_navigation_start_subscriptions), OpenAppTo.DOWNLOADS to stringResource(R.string.settings_navigation_start_downloads)), state.currentOpenAppTo, actions.onSetOpenAppTo)
                    // Without Picked for you, Explore has no For you tab and always opens to Top.
                    if (state.discoverySections.pickedForYou) {
                        AppearanceChoicePicker(stringResource(R.string.settings_navigation_explore_opens_to), listOf(ExploreDefaultTab.FOR_YOU to stringResource(R.string.settings_navigation_explore_for_you), ExploreDefaultTab.TOP to stringResource(R.string.settings_navigation_tab_top)), ExploreDefaultTab.sanitize(state.currentExploreDefaultTab), actions.onSetExploreDefaultTab)
                    }
                    AppearanceChoicePicker(stringResource(R.string.settings_navigation_subscriptions_opens_to), listOf(SubscriptionsDefaultTab.SHOWS to stringResource(R.string.settings_navigation_subscriptions_shows), SubscriptionsDefaultTab.NEW_EPISODES to stringResource(R.string.settings_navigation_subscriptions_new_episodes)), SubscriptionsDefaultTab.sanitize(state.currentSubscriptionsDefaultTab), actions.onSetSubscriptionsDefaultTab)
                    AppearanceChoicePicker(stringResource(R.string.settings_navigation_subscriptions_tab_position), listOf(SubscriptionsTabStyle.TOP to stringResource(R.string.settings_navigation_tab_top), SubscriptionsTabStyle.FLOATING to stringResource(R.string.settings_navigation_tab_floating)), SubscriptionsTabStyle.sanitize(state.currentSubscriptionsTabStyle), actions.onSetSubscriptionsTabStyle)
                }
            }
        }
    }
}
