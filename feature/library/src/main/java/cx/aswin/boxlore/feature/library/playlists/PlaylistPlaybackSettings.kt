package cx.aswin.boxlore.feature.library.playlists

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_PER_SHOW_CHOICES
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.library.R

/** A mix's name, one-line explanation and icon. */
internal data class PlaylistMixCopy(@StringRes val name: Int, @StringRes val body: Int, val icon: ImageVector)

internal fun playlistMixCopy(mix: PlaylistMix): PlaylistMixCopy = when (mix) {
    PlaylistMix.TAKE_TURNS -> PlaylistMixCopy(R.string.library_playlist_mix_take_turns, R.string.library_playlist_mix_take_turns_body, Icons.Rounded.SyncAlt)
    PlaylistMix.NEWEST_FIRST -> PlaylistMixCopy(R.string.library_playlist_newest_first, R.string.library_playlist_mix_newest_body, Icons.Rounded.NewReleases)
    PlaylistMix.ONE_SHOW_AT_A_TIME -> PlaylistMixCopy(R.string.library_playlist_mix_one_show, R.string.library_playlist_mix_one_show_body, Icons.AutoMirrored.Rounded.QueueMusic)
}

/**
 * How a playlist plays: its mix (a card each, with what it does) and the most episodes it takes from
 * one show. Stateless; New playlist and a playlist's page both use it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaylistPlaybackSettings(
    mix: PlaylistMix,
    perShowLimit: Int?,
    onChange: (PlaylistMix, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag(PLAYLIST_PLAYBACK_SETTINGS_TAG), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.library_playlist_how_it_plays),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.bold,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaylistMix.entries.forEach { option ->
                MixOption(option, selected = option == mix, onSelect = { if (option != mix) onChange(option, perShowLimit) })
            }
        }
        Text(
            stringResource(R.string.library_playlist_per_show),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = GoogleSansWeight.semiBold,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        val locale = currentLocale()
        // The app's single-choice control, as for the auto-download episode limit: TalkBack reads it as a choice.
        val none = stringResource(R.string.library_playlist_per_show_none)
        ConnectedOptionSelector(
            options = PLAYLIST_PER_SHOW_CHOICES.map { choice -> choice to (choice?.let { LocaleFormats.number(it.toLong(), locale) } ?: none) },
            selected = perShowLimit,
            onSelect = { choice -> if (choice != perShowLimit) onChange(mix, choice) },
        )
        Text(
            perShowLimit?.let { pluralStringResource(R.plurals.library_playlist_per_show_summary, it, it) }
                ?: stringResource(R.string.library_playlist_per_show_summary_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MixOption(mix: PlaylistMix, selected: Boolean, onSelect: () -> Unit) {
    val copy = playlistMixCopy(mix)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, colors.secondary) else null,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                copy.icon,
                contentDescription = null,
                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    stringResource(copy.name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                )
                Text(
                    stringResource(copy.body),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
            // The card is the radio; the button only shows its state.
            RadioButton(selected = selected, onClick = null)
        }
    }
}

internal const val PLAYLIST_PLAYBACK_SETTINGS_TAG = "playlist_playback_settings"
