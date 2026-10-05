package cx.aswin.boxlore.feature.settings.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

@Composable
internal fun AutoDownloadBackgroundSettingsCard(
    settings: AutoDownloadBackgroundSettings,
    onEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
) {
    SettingsGroup(
        title = "While boxlore is closed",
        footer = if (settings.enabled) {
            "Checks about every 6 hours; Android may delay them. Uses extra battery and data and pauses on low battery."
        } else {
            "Show notifications can still start downloads while boxlore is closed. Otherwise, new episodes are picked up when you open and refresh."
        },
    ) {
        SettingsSwitchRow(
            title = "Backup background checks",
            supportingText = "Find new episodes if show notifications are off or don't arrive.",
            icon = Icons.Rounded.Update,
            checked = settings.enabled,
            onCheckedChange = onEnabledChange,
        )
        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Check on Wi-Fi only",
                    supportingText = "Applies to checks and the downloads they start.",
                    icon = Icons.Rounded.Wifi,
                    checked = settings.wifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Only while charging",
                    icon = Icons.Rounded.BatteryChargingFull,
                    checked = settings.chargingOnly,
                    onCheckedChange = onChargingOnlyChange,
                )
            }
        }
    }
}
