package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.runtime.Composable

/** Older direct theme entry points open the unified Appearance page. */
@Composable
internal fun ThemeSettingsPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit, onEditCustomTheme: () -> Unit) {
    AppearanceSettingsPage(state, actions, onBack, onEditCustomTheme)
}
