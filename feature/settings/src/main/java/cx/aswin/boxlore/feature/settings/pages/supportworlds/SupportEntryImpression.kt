package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.core.analytics.AnalyticsHelper

/** Where Settings shows the Support boxlore entry. */
internal enum class SupportEntrySurface(val analyticsValue: String) {
    SettingsTopBar("settings_top_bar"),
    SettingsCategoryList("settings_category_list"),
}

/**
 * Reports once that the Support entry was shown, the top of the support-world funnel. Leaving and
 * returning to the hub counts again; rotating, resizing or folding restores the flag instead.
 */
@Composable
internal fun SupportEntryImpression(
    surface: SupportEntrySurface,
    track: (String) -> Unit = AnalyticsHelper::trackSupportEntryViewed,
) {
    var reported by rememberSaveable(surface) { mutableStateOf(false) }
    LaunchedEffect(surface) {
        if (!reported) {
            reported = true
            track(surface.analyticsValue)
        }
    }
}
