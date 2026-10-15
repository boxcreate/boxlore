package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes

@Composable
internal fun TabletLibraryHub(state: LibraryUiState.Success, subscriptions: () -> Unit, liked: () -> Unit, downloads: () -> Unit, history: () -> Unit, modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (boxloreContentWidthDp() >= 640f) 2 else 1),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = boxloreContentBottomPadding(96.dp)),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "subscriptions") {
            LibraryMenuCard("Subscriptions", Icons.Rounded.Add, subscriptions, MaterialTheme.colorScheme.secondaryContainer, state.subscribedPodcasts.take(3).map { it.imageUrl }, listOf(ExpressiveShapes.Circle, ExpressiveShapes.Puffy, ExpressiveShapes.Diamond))
        }
        item(key = "liked") {
            LibraryMenuCard("Liked Episodes", Icons.Rounded.Favorite, liked, MaterialTheme.colorScheme.tertiaryContainer, state.likedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" }, listOf(ExpressiveShapes.Heart, ExpressiveShapes.Star, ExpressiveShapes.SoftBurst))
        }
        item(key = "downloads") {
            LibraryMenuCard("Downloads", Icons.Rounded.DownloadDone, downloads, MaterialTheme.colorScheme.primaryContainer, state.downloadedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" }, listOf(ExpressiveShapes.Hexagon, ExpressiveShapes.Gem, ExpressiveShapes.Cookie4))
        }
        item(key = "history") {
            LibraryMenuCard("Listening History", Icons.Rounded.History, history, MaterialTheme.colorScheme.surfaceContainerHigh, state.recentHistory.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" }, listOf(ExpressiveShapes.Circle, ExpressiveShapes.Burst, ExpressiveShapes.Sunny))
        }
    }
}
