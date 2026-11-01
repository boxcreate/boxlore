package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsPictureGrid
import cx.aswin.boxlore.feature.settings.components.SettingsPictureTile
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow
import cx.aswin.boxlore.feature.settings.components.SettingsTileSection

/** Player: the full player's style as picture tiles, then the miniplayer's seek buttons. */
@Composable
internal fun AppearancePlayerPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    TrackScreenView("appearance_player")
    SettingsScaffold(title = "Player", onBack = onBack) {
        PlayerStyleSection(state.fullPlayerStyle, actions.onSetFullPlayerStyle)
        SettingsGroup(title = "Miniplayer") {
            SettingsSwitchRow(
                title = "Seek buttons",
                supportingText = "Show back and forward controls beside play and pause",
                checked = state.miniPlayerSeekButtonsEnabled,
                onCheckedChange = actions.onSetMiniPlayerSeekButtonsEnabled,
                icon = Icons.Rounded.Replay,
            )
        }
    }
}

/** Standard and bunpod as picture tiles, with what differs between them and bunpod's design credit. */
@Composable
internal fun PlayerStyleSection(selected: String, onSelect: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val current = FullPlayerStyle.sanitize(selected)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsTileSection(title = stringResource(R.string.player_style_title), footer = stringResource(R.string.player_style_description)) {
            SettingsPictureGrid(listOf(FullPlayerStyle.STANDARD, FullPlayerStyle.BUNPOD), columns = 2) { style, modifier ->
                SettingsPictureTile(
                    label = stringResource(if (style == FullPlayerStyle.BUNPOD) R.string.player_style_bunpod else R.string.player_style_standard),
                    selected = current == style,
                    onClick = { onSelect(style) },
                    modifier = modifier,
                ) {
                    PlayerStylePicture(style)
                }
            }
        }
        TextButton(onClick = { uriHandler.openUri("https://github.com/kamranbekirovyz/bunpod") }, modifier = Modifier.padding(start = 4.dp)) {
            Text(stringResource(R.string.player_style_credit), style = MaterialTheme.typography.labelLarge)
        }
    }
}
