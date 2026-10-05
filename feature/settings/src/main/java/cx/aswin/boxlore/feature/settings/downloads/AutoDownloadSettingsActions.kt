package cx.aswin.boxlore.feature.settings.downloads

import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val autoDownloadEpisodeLimits = listOf(1, 2, 3, 5)

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

internal data class AutoDownloadBackgroundPresentation(val controlsVisible: Boolean, val footer: String)

internal fun autoDownloadBackgroundPresentation(settings: AutoDownloadBackgroundSettings): AutoDownloadBackgroundPresentation =
    AutoDownloadBackgroundPresentation(
        controlsVisible = settings.enabled,
        footer = if (settings.enabled) {
            "Checks about every 6 hours; Android may delay them. Uses extra battery and data and pauses on low battery."
        } else {
            "Show notifications can still start downloads while boxlore is closed. Otherwise, new episodes are picked up when you open and refresh."
        },
    )
