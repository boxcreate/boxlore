package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

/** Show pages: how their episode lists look. The same switch sits in each show page's menu. */
@Composable
internal fun AppearanceShowPagesPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    TrackScreenView("appearance_show_pages")
    SettingsScaffold(title = "Show pages", onBack = onBack) {
        SettingsGroup(title = "Episode lists") {
            SettingsSwitchRow(
                title = "Episode titles only",
                supportingText = "Short rows with each episode’s title, date, length and play button",
                checked = state.episodeTitlesOnly,
                onCheckedChange = actions.onSetEpisodeTitlesOnly,
                icon = Icons.Rounded.ViewHeadline,
            )
        }
    }
}

/** What the Show pages row says under its title on the Appearance overview. */
internal fun showPagesSummary(state: AppearanceUiState): String = if (state.episodeTitlesOnly) "Episode titles only" else "Full episode rows"
