package cx.aswin.boxlore.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes

/**
 * The Library hub's Playlists card: [coverUrls] are the first playlists' covers. The hub shows the
 * card only when the app passes one, between Liked Episodes and Downloads.
 */
data class LibraryPlaylistsCard(val coverUrls: List<String> = emptyList(), val onClick: () -> Unit,)

@Composable
internal fun LibraryPlaylistsMenuCard(card: LibraryPlaylistsCard) {
    LibraryMenuCard(
        title = "Playlists",
        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
        onClick = card.onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        images = card.coverUrls.filter(String::isNotBlank).take(3),
        shapes = listOf(ExpressiveShapes.Cookie4, ExpressiveShapes.Puffy, ExpressiveShapes.Circle),
    )
}
