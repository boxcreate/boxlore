package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsInfoTip
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import java.util.Locale
import kotlin.math.roundToLong

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

    fun connection(reachable: Boolean): UiText =
        UiText.res(if (reachable) R.string.settings_wear_connected else R.string.settings_wear_not_connected)

    /** A limit as the watch shows it: "250 MB", "1 GB". */
    fun limit(megabytes: Int): UiText =
        if (megabytes < MB_PER_GB) UiText.res(R.string.settings_wear_size_mb, megabytes) else UiText.res(R.string.settings_wear_size_gb, megabytes / MB_PER_GB)

    /** How much of the limit downloads on the watch use, as last reported by the watch. */
    fun usage(state: WearOsUiState, locale: Locale = Locale.getDefault()): UiText {
        if (state.watchDownloads <= 0) return UiText.res(R.string.settings_wear_no_downloads)
        val usedMb = (state.watchStoredBytes.coerceAtLeast(0L) / BYTES_PER_MB.toDouble()).roundToLong()
        return UiText.plural(
            R.plurals.settings_wear_usage,
            state.watchDownloads,
            state.watchDownloads,
            LocaleFormats.number(usedMb, locale),
            limit(state.storageLimitMb),
        )
    }

    /** The stored layout key's name as the page shows it; the keys are stored values and never change. */
    @StringRes
    fun layoutLabel(layout: String): Int = if (layout == WearOsUiState.LAYOUT_LIST) R.string.settings_wear_layout_list else R.string.settings_wear_layout_honeycomb
}

@Composable
internal fun WearOsSettingsPage(
    linked: WearOsSettings?,
    onBack: () -> Unit,
) {
    TrackScreenView("wear_os")
    val settings = linked ?: WearOsSettings()
    val state = settings.state
    SettingsScaffold(title = stringResource(R.string.settings_page_wear_os), onBack = onBack) {
        val locale = currentLocale()
        SettingsInfoTip(text = WearOsCopy.connection(state.watchReachable).asString(), modifier = Modifier.testTag("wear_os_connection"))
        SettingsGroup(title = stringResource(R.string.settings_wear_your_shows)) {
            SettingsContent {
                AppearanceChoicePicker(
                    stringResource(R.string.settings_wear_layout_title),
                    listOf(WearOsUiState.LAYOUT_HONEYCOMB, WearOsUiState.LAYOUT_LIST).map { it to stringResource(WearOsCopy.layoutLabel(it)) },
                    state.showsLayout,
                    settings.onSetShowsLayout,
                )
            }
        }
        SettingsGroup(
            title = stringResource(R.string.settings_wear_downloads_title),
            footer = stringResource(R.string.settings_wear_downloads_footer),
        ) {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppearanceChoicePicker(
                        stringResource(R.string.settings_wear_storage_limit),
                        WearOsUiState.STORAGE_LIMITS_MB.map { it.toString() to WearOsCopy.limit(it).asString() },
                        state.storageLimitMb.toString(),
                    ) { picked -> picked.toIntOrNull()?.let(settings.onSetStorageLimitMb) }
                    SettingsInfoTip(text = WearOsCopy.usage(state, locale).asString(), modifier = Modifier.testTag("wear_os_usage"))
                }
            }
        }
    }
}
