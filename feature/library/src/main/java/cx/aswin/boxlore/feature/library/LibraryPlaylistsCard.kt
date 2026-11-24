package cx.aswin.boxlore.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes

/**
 * The Library hub's Playlists card: [coverUrls] are the first playlists' covers and [count] how many
 * playlists there are (null leaves the card without a status line). The hub shows the card only when
 * the app passes one, as its last card.
 */
data class LibraryPlaylistsCard(val coverUrls: List<String> = emptyList(), val onClick: () -> Unit, val count: Int? = null)

@Composable
internal fun LibraryPlaylistsMenuCard(card: LibraryPlaylistsCard) {
    LibraryMenuCard(
        title = stringResource(R.string.library_hub_playlists),
        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
        onClick = card.onClick,
        // A colour of its own, apart from the other four cards', in every theme.
        containerColor = MaterialTheme.colorScheme.playlistsCardContainer(),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        images = card.coverUrls.filter(String::isNotBlank).take(3),
        shapes = listOf(ExpressiveShapes.Cookie4, ExpressiveShapes.Puffy, ExpressiveShapes.Circle),
        status = card.count?.let { count ->
            if (count == 0) stringResource(R.string.library_hub_no_playlists) else pluralStringResource(R.plurals.library_hub_playlist_count, count, count)
        },
    )
}

/**
 * The Playlists card's colour: the primary container a quarter of the way deeper, so it reads
 * apart from Downloads' primary container, Subscriptions' secondary, Liked's tertiary and
 * History's neutral container, and follows every theme (brand, preset, wallpaper, light, dark and
 * Pure), with [ColorScheme.onPrimaryContainer] on it.
 */
internal fun ColorScheme.playlistsCardContainer(): Color {
    // Away from the card's text colour: toward primary in light themes, toward the page in dark ones.
    val deeper = if (primary.luminance() < primaryContainer.luminance()) primary else surface
    return lerp(primaryContainer, deeper, PLAYLISTS_TONE_SHIFT)
}

private const val PLAYLISTS_TONE_SHIFT = 0.25f
