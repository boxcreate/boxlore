package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.logic.FeedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun SingleTrailerCard(
    episode: Episode,
    globalIndex: Int,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, PodcastInfoViewModel.EpisodePlaybackState>>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    EpisodePlayStateWrapper(episodeId = episode.id, playbackStateFlow = playbackStateFlow) { playState ->
        val isPlaying = playState?.isPlaying == true
        val isResume = playState?.isResume == true

        OutlinedCard(
            modifier =
                modifier
                    .fillMaxWidth()
                    .expressiveClickable { onEpisodeClick(episode, globalIndex) },
            shape = MaterialTheme.shapes.large,
            colors =
                androidx.compose.material3.CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation =
                androidx.compose.material3.CardDefaults
                    .outlinedCardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Play Button
                Surface(
                    shape = CircleShape,
                    color = if (isPlaying || isResume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier
                            .size(40.dp) // Slightly larger play button for a better hit target
                            .expressiveClickable(isolate = true) { onPlayClick(episode) },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint =
                            if (isPlaying ||
                                isResume
                            ) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = ExpressiveShapes.Pill,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = "Trailer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        val durationText =
                            if (episode.duration > 0) {
                                val h = episode.duration / 3600
                                val m = (episode.duration % 3600) / 60
                                if (h > 0) "${h}hr ${m}min" else "${m}min"
                            } else {
                                ""
                            }

                        if (durationText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrailerStackCard(
    group: FeedItem.TrailerGroup,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, PodcastInfoViewModel.EpisodePlaybackState>>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.large,
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation =
            androidx.compose.material3.CardDefaults
                .outlinedCardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Header Row (Always visible)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .expressiveClickable { isExpanded = !isExpanded }
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Promotional Trailers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${group.trailers.size} trailers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded Content (Mini-Trailers)
            if (isExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    group.trailers.forEachIndexed { index, (episode, globalIndex) ->
                        EpisodePlayStateWrapper(episodeId = episode.id, playbackStateFlow = playbackStateFlow) { playState ->
                            val isPlaying = playState?.isPlaying == true
                            val isResume = playState?.isResume == true

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .expressiveClickable { onEpisodeClick(episode, globalIndex) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Play Button
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isPlaying ||
                                            isResume
                                        ) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .expressiveClickable(isolate = true) { onPlayClick(episode) },
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint =
                                            if (isPlaying ||
                                                isResume
                                            ) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Text
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = episode.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val durationText =
                                        if (episode.duration > 0) {
                                            val h = episode.duration / 3600
                                            val m = (episode.duration % 3600) / 60
                                            if (h > 0) "${h}hr ${m}min" else "${m}min"
                                        } else {
                                            "Trailer"
                                        }

                                    Text(
                                        text = durationText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (index < group.trailers.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(start = 64.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EpisodePlayStateWrapper(
    episodeId: String,
    playbackStateFlow: kotlinx.coroutines.flow.Flow<Map<String, PodcastInfoViewModel.EpisodePlaybackState>>,
    content: @Composable (PodcastInfoViewModel.EpisodePlaybackState?) -> Unit,
) {
    val playStateFlow =
        remember(episodeId, playbackStateFlow) {
            playbackStateFlow.map { it[episodeId] }.distinctUntilChanged()
        }
    val playState by playStateFlow.collectAsState(initial = null)
    content(playState)
}
