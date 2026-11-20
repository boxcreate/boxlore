package cx.aswin.boxlore.feature.settings.downloads

import androidx.annotation.StringRes
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import cx.aswin.boxlore.feature.settings.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val autoDownloadEpisodeLimits = listOf(1, 2, 3, 5)

/**
 * One `settings_interaction` per download control (Smart Downloads and automatic downloads), sent
 * only when [value] differs from [previous], so re-picking the current choice reports nothing.
 */
internal fun trackDownloadSetting(key: String, value: Any, previous: Any) {
    if (value != previous) AnalyticsHelper.trackSettingsInteraction(key, value.toString())
}

/** Only write the chosen preference; hiding background controls never resets their values. */
internal class AutoDownloadSettingsActions(
    private val scope: CoroutineScope,
    private val saveEpisodeLimit: suspend (Int) -> Unit,
    private val saveBackgroundEnabled: suspend (Boolean) -> Unit,
    private val onHelpVisibilityChange: (Boolean) -> Unit,
) {
    fun selectEpisodeLimit(limit: Int) {
        if (limit !in autoDownloadEpisodeLimits) return
        scope.launch { saveEpisodeLimit(limit) }
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        scope.launch { saveBackgroundEnabled(enabled) }
    }

    fun showHelp() = onHelpVisibilityChange(true)

    fun dismissHelp() = onHelpVisibilityChange(false)
}

internal data class AutoDownloadBackgroundPresentation(val controlsVisible: Boolean, @StringRes val footer: Int)

internal fun autoDownloadBackgroundPresentation(settings: AutoDownloadBackgroundSettings): AutoDownloadBackgroundPresentation =
    AutoDownloadBackgroundPresentation(
        controlsVisible = settings.enabled,
        footer = if (settings.enabled) {
            R.string.settings_auto_download_background_on_footer
        } else {
            R.string.settings_auto_download_background_off_footer
        },
    )
