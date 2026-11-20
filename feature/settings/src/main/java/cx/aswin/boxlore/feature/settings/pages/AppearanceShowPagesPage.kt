package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

/** Show pages: how their episode lists look. The same switch sits in each show page's menu. */
@Composable
internal fun AppearanceShowPagesPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    TrackScreenView("appearance_show_pages")
    SettingsScaffold(title = stringResource(R.string.settings_page_show_pages), onBack = onBack) {
        SettingsGroup(title = stringResource(R.string.settings_show_pages_episode_lists)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_pages_titles_only),
                supportingText = stringResource(R.string.settings_show_pages_titles_only_description),
                checked = state.episodeTitlesOnly,
                onCheckedChange = actions.onSetEpisodeTitlesOnly,
                icon = Icons.Rounded.ViewHeadline,
            )
        }
    }
}

/** What the Show pages row says under its title on the Appearance overview. */
internal fun showPagesSummary(state: AppearanceUiState): UiText =
    UiText.res(if (state.episodeTitlesOnly) R.string.settings_show_pages_titles_only else R.string.settings_show_pages_full_rows)
