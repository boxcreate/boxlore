package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

/** Stable Compose [testTag] ids for Downloads settings instrumentation. */
object SettingsDownloadsTestTags {
    const val SMART_DOWNLOADS_ROW = "settings_downloads_smart"
    const val AUTO_DOWNLOADS_ROW = "settings_downloads_auto"
}

@Composable
internal fun DownloadsSettingsPage(
    onSmartDownloadsClick: () -> Unit,
    onAutoDownloadsClick: () -> Unit,
    onBack: () -> Unit,
) {
    TrackScreenView("downloads_settings")
    SettingsScaffold(
        title = stringResource(R.string.settings_page_downloads),
        onBack = onBack,
    ) {
        SettingsGroup(
            title = stringResource(R.string.settings_downloads_offline_title),
            footer = stringResource(R.string.settings_downloads_offline_footer),
        ) {
            SettingsNavigationRow(
                title = stringResource(R.string.settings_downloads_smart),
                supportingText = stringResource(R.string.settings_downloads_smart_description),
                icon = Icons.Rounded.AutoAwesome,
                onClick = onSmartDownloadsClick,
                modifier = Modifier.testTag(SettingsDownloadsTestTags.SMART_DOWNLOADS_ROW),
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = stringResource(R.string.settings_auto_download_title),
                supportingText = stringResource(R.string.settings_downloads_auto_description),
                icon = Icons.Rounded.CloudDownload,
                onClick = onAutoDownloadsClick,
                modifier = Modifier.testTag(SettingsDownloadsTestTags.AUTO_DOWNLOADS_ROW),
            )
        }
    }
}
