package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsChoiceRow
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

/**
 * App language: the phone's language, then each language boxlore has, named in that language.
 * Picking one applies it at once; the app supplies [AppearanceActions.onSetAppLanguage]. The pick is
 * reported here (not in `trackedForAnalytics`) as `app_language_changed`.
 */
@Composable
internal fun AppearanceLanguagePage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    TrackScreenView("appearance_language")
    SettingsScaffold(title = stringResource(R.string.settings_app_language_title), onBack = onBack) {
        SettingsGroup(
            modifier = Modifier.selectableGroup(),
            footer = stringResource(R.string.settings_app_language_ai_note),
        ) {
            AppLanguage.entries.forEachIndexed { index, language ->
                if (index > 0) SettingsDivider()
                val selected = language == state.appLanguage
                SettingsChoiceRow(
                    title = appLanguageName(language),
                    selected = selected,
                    onClick = {
                        if (!selected) AnalyticsHelper.trackSettingsInteraction("app_language_changed", appLanguageAnalyticsValue(language))
                        actions.onSetAppLanguage(language)
                    },
                )
            }
        }
    }
}

/** The `settings_interaction` value for a pick: the language tag, or "system" for the phone's language. */
internal fun appLanguageAnalyticsValue(language: AppLanguage): String = language.tag.ifEmpty { "system" }

/** A language's name as the list and the Appearance row show it: in that language, or "System default". */
@Composable
internal fun appLanguageName(language: AppLanguage): String =
    language.nativeName ?: stringResource(R.string.settings_app_language_system)
