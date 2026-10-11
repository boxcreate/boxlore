package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R

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
    HomeMixArtwork(
        episode = episode,
        podcast = podcast,
        status = rowState.status,
        downloaded = false,
        playing = rowState.isCurrentPlaying,
        onPlay = onPlay,
        size = 72.dp,
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
                fontWeight = GoogleSansWeight.bold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
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
        if (rowState.isInProgress && rowState.progress > 0f) {
            Spacer(Modifier.height(6.dp))
            HomeMixEpisodeTime(episode, rowState.status, rowState.progress)
        }
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
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showPodcastTitle) {
            DensePodcastTitle(podcast.title)
        }
        if (isNew) {
            DenseNewEpisodeBadge()
        }
        DenseEpisodeDate(episode.publishedDate, showPodcastTitle || isNew)
        if (!rowState.isInProgress || rowState.progress <= 0f) DenseEpisodeDuration(episode, rowState)
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
            text = stringResource(R.string.home_hero_new),
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = GoogleSansWeight.bold,
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
        fontWeight = if (rowState.isInProgress) GoogleSansWeight.bold else FontWeight.Normal,
    )
}
