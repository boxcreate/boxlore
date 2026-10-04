package cx.aswin.boxlore.feature.settings.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings

@Composable
internal fun AutoDownloadBackgroundSettingsCard(
    settings: AutoDownloadBackgroundSettings,
    onEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackgroundCheckSwitch("Check for episodes in the background", settings.enabled, true, onEnabledChange)
            Text(
                "Optional. Check shows with auto-download enabled about every six hours when boxlore is closed. " +
                    "This can recover episodes without a new-episode push, but uses additional battery and data. " +
                    "Android may delay checks. Enabling auto-download for a show does not enable this option.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BackgroundCheckSwitch("Use unmetered networks only", settings.wifiOnly, settings.enabled, onWifiOnlyChange)
            BackgroundCheckSwitch("Only while charging", settings.chargingOnly, settings.enabled, onChargingOnlyChange)
            Text(
                "Background checks and the downloads they start always pause when the battery is low. " +
                    "Your download network setting also applies to audio files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "When this is off, normal refreshes while boxlore is open can still trigger auto-downloads. " +
                    "Enable a show's notifications for push-triggered downloads while the app is closed. " +
                    "With both options off, new episodes download after you open and refresh the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackgroundCheckSwitch(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
