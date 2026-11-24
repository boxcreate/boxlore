package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes

/** The hub's cards in order, keyed so a window change keeps the card in view. */
internal object LibraryHubKeys {
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIKED = "liked"
    const val DOWNLOADS = "downloads"
    const val HISTORY = "history"
    const val PLAYLISTS = "playlists"
}

/**
 * The hub's cards in one grid on every window: one column on phones, and on tablets one or two
 * bounded columns. The same grid and [gridState] serve both, so folding or unfolding keeps the hub
 * where it was.
 */
@Composable
internal fun LibraryHubGrid(
    state: LibraryUiState.Success,
    gridState: LazyGridState,
    destinations: LibraryHubDestinations,
    modifier: Modifier,
    playlists: LibraryPlaylistsCard? = null,
) {
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (tablet && boxloreContentWidthDp() >= TWO_COLUMNS_FROM_DP) 2 else 1),
        state = gridState,
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize(),
        // Phones keep room for the floating bar and mini player under the last card; tablets reserve only the player.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = boxloreContentBottomPadding(PHONE_BOTTOM_PADDING)),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = LibraryHubKeys.SUBSCRIPTIONS) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_subscriptions),
                icon = Icons.Rounded.Add,
                onClick = destinations.subscriptions,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                images = state.subscribedPodcasts.take(3).map { it.imageUrl },
                shapes = listOf(ExpressiveShapes.Circle, ExpressiveShapes.Puffy, ExpressiveShapes.Diamond),
            )
        }
        item(key = LibraryHubKeys.LIKED) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_liked_episodes),
                icon = Icons.Rounded.Favorite,
                onClick = destinations.liked,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                images = state.likedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" },
                shapes = listOf(ExpressiveShapes.Heart, ExpressiveShapes.Star, ExpressiveShapes.SoftBurst),
            )
        }
        item(key = LibraryHubKeys.DOWNLOADS) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_downloads),
                icon = Icons.Rounded.DownloadDone,
                onClick = destinations.downloads,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                images = state.downloadedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" },
                shapes = listOf(ExpressiveShapes.Hexagon, ExpressiveShapes.Gem, ExpressiveShapes.Cookie4),
            )
        }
        item(key = LibraryHubKeys.HISTORY) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_listening_history),
                icon = Icons.Rounded.History,
                onClick = destinations.history,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                images = state.recentHistory.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" },
                shapes = listOf(ExpressiveShapes.Circle, ExpressiveShapes.Burst, ExpressiveShapes.Sunny),
            )
        }
        playlists?.let { card ->
            item(key = LibraryHubKeys.PLAYLISTS) { LibraryPlaylistsMenuCard(card) }
        }
    }
}

/** Where each hub card leads. */
internal data class LibraryHubDestinations(
    val subscriptions: () -> Unit,
    val liked: () -> Unit,
    val downloads: () -> Unit,
    val history: () -> Unit,
)

/** Tablet content this wide or wider shows the hub's cards in two columns. */
private const val TWO_COLUMNS_FROM_DP = 640f

/** Phones: room under the last card for the floating bar and mini player. */
private val PHONE_BOTTOM_PADDING = 180.dp
