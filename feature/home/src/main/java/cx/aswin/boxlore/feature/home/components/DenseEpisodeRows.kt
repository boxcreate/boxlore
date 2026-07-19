package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

@Composable
internal fun DenseEpisodeRow(
    episode: Episode,
    podcast: Podcast,
    actions: DenseEpisodeRowActions,
    modifier: Modifier = Modifier,
    showPodcastTitle: Boolean = true,
    playback: DenseEpisodeRowPlayback = DenseEpisodeRowPlayback(),
    isEligibleForNewTag: Boolean = true,
) {
    val rowState = episodeRowState(episode, podcast, playback)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .expressiveClickable(onClick = actions.onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DenseEpisodeArtwork(
            episode = episode,
            podcast = podcast,
            rowState = rowState,
            onPlay = actions.onPlay,
        )

        Spacer(modifier = Modifier.width(12.dp))

        DenseEpisodeDetails(
            episode = episode,
            podcast = podcast,
            rowState = rowState,
            showPodcastTitle = showPodcastTitle,
            isEligibleForNewTag = isEligibleForNewTag,
        )
    }
}

internal data class DenseEpisodeRowActions(
    val onClick: () -> Unit,
    val onPlay: () -> Unit,
)

internal data class DenseEpisodeRowPlayback(
    val overrideStatus: EpisodeStatus? = null,
    val overrideProgress: Float? = null,
    val currentPlayingEpisodeId: String? = null,
    val isPlaying: Boolean = false,
)

private data class DenseEpisodeRowState(
    val status: EpisodeStatus,
    val progress: Float,
    val isCompleted: Boolean,
    val isInProgress: Boolean,
    val isCurrentPlaying: Boolean,
)

private fun episodeRowState(
    episode: Episode,
    podcast: Podcast,
    playback: DenseEpisodeRowPlayback,
): DenseEpisodeRowState {
    val isPodcastLatestEpisode = podcast.latestEpisode?.id == episode.id
    val status = playback.overrideStatus ?: if (isPodcastLatestEpisode) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = playback.overrideProgress ?: if (isPodcastLatestEpisode) (podcast.resumeProgress ?: 0f) else 0f
    return DenseEpisodeRowState(
        status = status,
        progress = progress,
        isCompleted = status == EpisodeStatus.COMPLETED,
        isInProgress = status == EpisodeStatus.IN_PROGRESS,
        isCurrentPlaying = playback.currentPlayingEpisodeId == episode.id && playback.isPlaying,
    )
}

@Composable
private fun DenseEpisodeArtwork(
    episode: Episode,
    podcast: Podcast,
    rowState: DenseEpisodeRowState,
    onPlay: () -> Unit,
) {
    Box(modifier = Modifier.size(56.dp)) {
        OptimizedImage(
            url =
                episode.imageUrl?.takeIf { it.isNotEmpty() }
                    ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                    ?: podcast.fallbackImageUrl,
            proxyWidth = 112,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        DenseEpisodePlayButton(
            isCurrentPlaying = rowState.isCurrentPlaying,
            onPlay = onPlay,
            modifier = Modifier.align(Alignment.Center),
        )
        if (rowState.isCompleted) {
            DenseEpisodeCompletedBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
        if (rowState.isInProgress && rowState.progress > 0f) {
            DenseEpisodeProgress(progress = rowState.progress)
        }
    }
}

@Composable
private fun DenseEpisodePlayButton(
    isCurrentPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onPlay,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
        modifier =
            modifier
                .size(28.dp)
                .expressiveClickable(onClick = onPlay),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = if (isCurrentPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DenseEpisodeCompletedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(3.dp)
                .size(14.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Played",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(8.dp),
        )
    }
}

@Composable
private fun BoxScope.DenseEpisodeProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.White.copy(alpha = 0.4f),
        drawStopIndicator = {},
    )
}

@Composable
private fun RowScope.DenseEpisodeDetails(
    episode: Episode,
    podcast: Podcast,
    rowState: DenseEpisodeRowState,
    showPodcastTitle: Boolean,
    isEligibleForNewTag: Boolean,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = episode.title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(3.dp))
        DenseEpisodeMetadataRow(
            episode = episode,
            podcast = podcast,
            rowState = rowState,
            showPodcastTitle = showPodcastTitle,
            isEligibleForNewTag = isEligibleForNewTag,
        )
    }
}

@Composable
private fun DenseEpisodeMetadataRow(
    episode: Episode,
    podcast: Podcast,
    rowState: DenseEpisodeRowState,
    showPodcastTitle: Boolean,
    isEligibleForNewTag: Boolean,
) {
    val isNew = isEligibleForNewTag && isNewEpisode(episode, podcast, rowState.status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showPodcastTitle) {
            DensePodcastTitle(podcast.title)
        }
        if (isNew) {
            DenseNewEpisodeBadge()
        }
        DenseEpisodeDate(episode.publishedDate, showPodcastTitle || isNew)
        DenseEpisodeDuration(episode, rowState)
    }
}

@Composable
private fun RowScope.DensePodcastTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
}

@Composable
private fun DenseNewEpisodeBadge() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "NEW",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
                ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DenseEpisodeDate(
    publishedDate: Long,
    includePrefix: Boolean,
) {
    val dateText = formatRelativeDate(publishedDate)
    if (dateText.isNotEmpty()) {
        val prefix = if (includePrefix) "• " else ""
        Text(
            text = "$prefix$dateText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun DenseEpisodeDuration(
    episode: Episode,
    rowState: DenseEpisodeRowState,
) {
    val displayText = denseDurationText(episode.duration, rowState.progress, rowState.isInProgress) ?: return
    Text(
        text = "• $displayText",
        style = MaterialTheme.typography.bodySmall,
        color =
            if (rowState.isInProgress) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        fontWeight = if (rowState.isInProgress) FontWeight.Bold else FontWeight.Normal,
    )
}

