package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsInfoTip
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import java.util.Locale

/**
 * What the Wear OS page shows about boxlore on the listener's watch. The app fills it from the
 * watch link; Settings only draws it.
 */
data class WearOsUiState(
    /** Whether the watch app can be reached right now. Changes made while it is away arrive later. */
    val watchReachable: Boolean = false,
    /** One of [LAYOUT_HONEYCOMB] or [LAYOUT_LIST]. */
    val showsLayout: String = LAYOUT_HONEYCOMB,
    /** One of [STORAGE_LIMITS_MB]. */
    val storageLimitMb: Int = DEFAULT_STORAGE_LIMIT_MB,
    /** Space downloads take on the watch as last reported, in bytes. */
    val watchStoredBytes: Long = 0L,
    val watchDownloads: Int = 0,
) {
    companion object {
        const val LAYOUT_HONEYCOMB = "Honeycomb"
        const val LAYOUT_LIST = "List"
        const val DEFAULT_STORAGE_LIMIT_MB = 500
        val STORAGE_LIMITS_MB: List<Int> = listOf(250, 500, 1000, 2000)
    }
}

/**
 * The Wear OS page's state and actions. [SettingsScreenConfig] carries it only once boxlore on a
 * watch has linked with this phone; until then Settings has no Wear OS entry.
 */
data class WearOsSettings(
    val state: WearOsUiState = WearOsUiState(),
    val onSetShowsLayout: (String) -> Unit = {},
    val onSetStorageLimitMb: (Int) -> Unit = {},
)

/** Words for the Wear OS page, kept apart from the layout so they can be tested. */
internal object WearOsCopy {
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val MB_PER_GB = 1000

    fun connection(reachable: Boolean): String =
        if (reachable) {
            "Your watch is connected. Changes here reach it right away."
        } else {
            "Your watch isn't connected right now. Changes here reach it the next time it connects."
        }

    /** A limit as the watch shows it: "250 MB", "1 GB". */
    fun limit(megabytes: Int): String = if (megabytes < MB_PER_GB) "$megabytes MB" else "${megabytes / MB_PER_GB} GB"

    /** How much of the limit downloads on the watch use, as last reported by the watch. */
    fun usage(state: WearOsUiState, locale: Locale = Locale.getDefault()): String {
        if (state.watchDownloads <= 0) return "No downloads on your watch yet."
        val used = String.format(locale, "%.0f MB", state.watchStoredBytes.coerceAtLeast(0L) / BYTES_PER_MB.toDouble())
        val episodes = if (state.watchDownloads == 1) "1 episode" else "${state.watchDownloads} episodes"
        return "On your watch: $episodes, $used of ${limit(state.storageLimitMb)}."
    }
}

@Composable
internal fun WearOsSettingsPage(
    linked: WearOsSettings?,
    onBack: () -> Unit,
) {
    TrackScreenView("wear_os")
    val settings = linked ?: WearOsSettings()
    val state = settings.state
    SettingsScaffold(title = "Wear OS", onBack = onBack) {
        SettingsInfoTip(text = WearOsCopy.connection(state.watchReachable), modifier = Modifier.testTag("wear_os_connection"))
        SettingsGroup(title = "Your shows") {
            SettingsContent {
                AppearanceChoicePicker(
                    "Layout on the watch",
                    listOf(WearOsUiState.LAYOUT_HONEYCOMB to "Honeycomb", WearOsUiState.LAYOUT_LIST to "List"),
                    state.showsLayout,
                    settings.onSetShowsLayout,
                )
            }
        }
        SettingsGroup(
            title = "Downloads on the watch",
            footer = "When downloads reach the limit, the watch removes the ones you played or downloaded longest ago.",
        ) {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppearanceChoicePicker(
                        "Storage limit",
                        WearOsUiState.STORAGE_LIMITS_MB.map { it.toString() to WearOsCopy.limit(it) },
                        state.storageLimitMb.toString(),
                    ) { picked -> picked.toIntOrNull()?.let(settings.onSetStorageLimitMb) }
                    SettingsInfoTip(text = WearOsCopy.usage(state), modifier = Modifier.testTag("wear_os_usage"))
                }
            }
        }
    }
}
