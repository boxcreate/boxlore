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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.logic.FeedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private typealias EpisodePlaybackStates = Map<String, PodcastInfoViewModel.EpisodePlaybackState>

private data class TrailerPlaybackUi(
    val isPlaying: Boolean,
    val isResume: Boolean,
)

private data class TrailerLeadVisuals(
    val containerColor: Color,
    val icon: ImageVector?,
    val contentDescription: String?,
    val iconTint: Color,
)

@Composable
internal fun SingleTrailerCard(
    episode: Episode,
    globalIndex: Int,
    playbackStateFlow: Flow<EpisodePlaybackStates>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    selection: EpisodeSelectionUi = EpisodeSelectionUi(),
    modifier: Modifier = Modifier,
) {
    EpisodePlayStateWrapper(episode.id, playbackStateFlow) { playState ->
        SingleTrailerContent(
            episode = episode,
            globalIndex = globalIndex,
            playback = TrailerPlaybackUi(playState?.isPlaying == true, playState?.isResume == true),
            onEpisodeClick = onEpisodeClick,
            onPlayClick = onPlayClick,
            selection = selection,
            modifier = modifier,
        )
    }
}

@Composable
private fun SingleTrailerContent(
    episode: Episode,
    globalIndex: Int,
    playback: TrailerPlaybackUi,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    selection: EpisodeSelectionUi,
    modifier: Modifier,
) {
    val isSelected = episode.id in selection.selectedEpisodeIds
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .expressiveClickable(
                    onLongClickLabel = "Select episode",
                    onLongClick = { selection.onLongPress(episode) },
                    onClick = {
                        if (selection.isActive) selection.onToggle(episode) else onEpisodeClick(episode, globalIndex)
                    },
                ),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        border = trailerBorder(isSelected),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrailerLeadButton(
                episode = episode,
                playback = playback,
                selection = selection,
                size = 40.dp,
                iconPadding = 10.dp,
                onPlayClick = onPlayClick,
            )
            Spacer(modifier = Modifier.width(14.dp))
            TrailerSummary(
                episode = episode,
                showBadge = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun TrailerStackCard(
    group: FeedItem.TrailerGroup,
    playbackStateFlow: Flow<EpisodePlaybackStates>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    selection: EpisodeSelectionUi = EpisodeSelectionUi(),
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedCount = group.trailers.count { (episode, _) -> episode.id in selection.selectedEpisodeIds }
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor =
                    if (selectedCount > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        border = trailerBorder(selectedCount > 0),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            TrailerStackHeader(
                trailerCount = group.trailers.size,
                selectedCount = selectedCount,
                isExpanded = isExpanded,
                onToggle = { isExpanded = !isExpanded },
            )
            if (isExpanded) {
                TrailerStackContent(
                    group = group,
                    playbackStateFlow = playbackStateFlow,
                    onEpisodeClick = onEpisodeClick,
                    onPlayClick = onPlayClick,
                    selection = selection,
                )
            }
        }
    }
}

@Composable
private fun TrailerStackHeader(
    trailerCount: Int,
    selectedCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().expressiveClickable(onClick = onToggle).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    fontWeight = GoogleSansWeight.bold,
                )
                Text(
                    text =
                        if (selectedCount > 0) {
                            "$selectedCount of $trailerCount selected"
                        } else {
                            "$trailerCount trailers"
                        },
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
}

@Composable
private fun TrailerStackContent(
    group: FeedItem.TrailerGroup,
    playbackStateFlow: Flow<EpisodePlaybackStates>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    selection: EpisodeSelectionUi,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        group.trailers.forEachIndexed { index, (episode, globalIndex) ->
            TrailerStackRow(
                episode = episode,
                globalIndex = globalIndex,
                playbackStateFlow = playbackStateFlow,
                onEpisodeClick = onEpisodeClick,
                onPlayClick = onPlayClick,
                selection = selection,
            )
            if (index < group.trailers.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(start = 64.dp),
                )
            }
        }
    }
}

@Composable
private fun TrailerStackRow(
    episode: Episode,
    globalIndex: Int,
    playbackStateFlow: Flow<EpisodePlaybackStates>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    selection: EpisodeSelectionUi,
) {
    EpisodePlayStateWrapper(episode.id, playbackStateFlow) { playState ->
        val isSelected = episode.id in selection.selectedEpisodeIds
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .expressiveClickable(
                        onLongClickLabel = "Select episode",
                        onLongClick = { selection.onLongPress(episode) },
                        onClick = {
                            if (selection.isActive) selection.onToggle(episode) else onEpisodeClick(episode, globalIndex)
                        },
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrailerLeadButton(
                episode = episode,
                playback = TrailerPlaybackUi(playState?.isPlaying == true, playState?.isResume == true),
                selection = selection,
                size = 36.dp,
                iconPadding = 8.dp,
                onPlayClick = onPlayClick,
            )
            Spacer(modifier = Modifier.width(12.dp))
            TrailerSummary(
                episode = episode,
                showBadge = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrailerLeadButton(
    episode: Episode,
    playback: TrailerPlaybackUi,
    selection: EpisodeSelectionUi,
    size: Dp,
    iconPadding: Dp,
    onPlayClick: (Episode) -> Unit,
) {
    val isSelected = episode.id in selection.selectedEpisodeIds
    val visuals =
        trailerLeadVisuals(
            isSelected = isSelected,
            selectionActive = selection.isActive,
            playback = playback,
        )
    Surface(
        shape = CircleShape,
        color = visuals.containerColor,
        modifier =
            Modifier
                .size(size)
                .then(
                    if (selection.isActive) {
                        Modifier
                    } else {
                        Modifier.expressiveClickable(isolate = true) { onPlayClick(episode) }
                    },
                ),
    ) {
        visuals.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = visuals.contentDescription,
                tint = visuals.iconTint,
                modifier = Modifier.padding(iconPadding),
            )
        }
    }
}

@Composable
private fun trailerLeadVisuals(
    isSelected: Boolean,
    selectionActive: Boolean,
    playback: TrailerPlaybackUi,
): TrailerLeadVisuals =
    when {
        isSelected ->
            TrailerLeadVisuals(
                containerColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Check,
                contentDescription = "Selected",
                iconTint = MaterialTheme.colorScheme.onPrimary,
            )
        selectionActive ->
            TrailerLeadVisuals(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                icon = null,
                contentDescription = null,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        playback.isPlaying ->
            TrailerLeadVisuals(
                containerColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Pause,
                contentDescription = "Pause",
                iconTint = MaterialTheme.colorScheme.onPrimary,
            )
        playback.isResume ->
            TrailerLeadVisuals(
                containerColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                iconTint = MaterialTheme.colorScheme.onPrimary,
            )
        else ->
            TrailerLeadVisuals(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                icon = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }

@Composable
private fun TrailerSummary(
    episode: Episode,
    showBadge: Boolean,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = episode.title,
            style = if (showBadge) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            fontWeight = if (showBadge) GoogleSansWeight.bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBadge) {
                Surface(
                    shape = ExpressiveShapes.Pill,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Trailer",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (!showBadge || episode.duration > 0) {
                Text(
                    text = trailerDurationText(episode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun trailerBorder(isSelected: Boolean): BorderStroke =
    BorderStroke(
        width = if (isSelected) 2.dp else 0.5.dp,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    )

private fun trailerDurationText(episode: Episode): String {
    if (episode.duration <= 0) return "Trailer"
    val hours = episode.duration / 3600
    val minutes = (episode.duration % 3600) / 60
    return if (hours > 0) "${hours}hr ${minutes}min" else "${minutes}min"
}

@Composable
fun EpisodePlayStateWrapper(
    episodeId: String,
    playbackStateFlow: Flow<EpisodePlaybackStates>,
    content: @Composable (PodcastInfoViewModel.EpisodePlaybackState?) -> Unit,
) {
    val playStateFlow =
        remember(episodeId, playbackStateFlow) {
            playbackStateFlow.map { it[episodeId] }.distinctUntilChanged()
        }
    val playState by playStateFlow.collectAsState(initial = null)
    content(playState)
}
