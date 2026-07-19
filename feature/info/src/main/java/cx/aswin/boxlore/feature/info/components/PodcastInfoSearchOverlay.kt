package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastInfoSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    results: List<Episode>?,
    allEpisodes: List<Episode>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    onToggleLike: (Episode) -> Unit,
    onQueueClick: (Episode) -> Unit,
    onDownloadClick: (Episode) -> Unit,
    onToggleCompletion: (Episode) -> Unit,
    likedEpisodeIds: Set<String>,
    completedEpisodeIds: Set<String>,
    queuedEpisodeIds: Set<String>,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, cx.aswin.boxlore.feature.info.PodcastInfoViewModel.EpisodePlaybackState>>,
    isSearching: Boolean,
    accentColor: Color,
    downloadedEpisodeIds: Set<String>,
    downloadingEpisodeIds: Set<String>,
) {
    val focusRequester =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                // Unified "M3 Style" Search Bar Component
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(56.dp),
                    // Standard M3 Search Height
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = androidx.compose.foundation.shape.CircleShape, // Full Pill
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Leading Icon (Back) acts as Navigation
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // Input Field
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search episodes...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }

                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush =
                                    androidx.compose.ui.graphics
                                        .SolidColor(accentColor),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                            )
                        }

                        // Trailing Icon (Clear)
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        val safeResults = results ?: emptyList()
        val displayList = if (query.isEmpty()) emptyList() else safeResults

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader.Expressive(
                        size = 64.dp,
                    )
                }
            } else if (query.isNotEmpty() && displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No episodes found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (displayList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = 16.dp,
                            bottom = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(displayList, key = { _, ep -> ep.id }) { index, episode ->
                        val isDownloaded = downloadedEpisodeIds.contains(episode.id)
                        val isDownloading = downloadingEpisodeIds.contains(episode.id)
                        val isCompleted = completedEpisodeIds.contains(episode.id)

                        EpisodePlayStateWrapper(
                            episodeId = episode.id,
                            playbackStateFlow = playbackStateFlow,
                        ) { playState ->
                            EpisodeListItem(
                                episode = episode,
                                isLiked = likedEpisodeIds.contains(episode.id),
                                accentColor = accentColor,
                                isPlaying = playState?.isPlaying == true,
                                isResume = playState?.isResume == true,
                                progress = playState?.progress ?: 0f,
                                timeLeft = playState?.timeLeft,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isQueued = queuedEpisodeIds.contains(episode.id),
                                isCompleted = isCompleted,
                                onClick = {
                                    onEpisodeClick(episode, index)
                                    onClose() // Close search on nav
                                },
                                onPlayClick = { onPlayClick(episode) },
                                onToggleLike = { onToggleLike(episode) },
                                onQueueClick = { onQueueClick(episode) },
                                onDownloadClick = { onDownloadClick(episode) },
                                onMarkPlayedClick = { onToggleCompletion(episode) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
