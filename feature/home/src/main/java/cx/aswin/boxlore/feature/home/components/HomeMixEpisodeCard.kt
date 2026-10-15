package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadDone
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R

@Composable
@Suppress("LongParameterList")
internal fun MixtapeEpisodeCard(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    overrideStatus: EpisodeStatus? = null,
    overrideProgress: Float? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
) {
    val status = overrideStatus ?: if (podcast.latestEpisode?.id == episode.id) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = overrideProgress ?: if (podcast.latestEpisode?.id == episode.id) (podcast.resumeProgress ?: 0f) else 0f
    val playing = currentPlayingEpisodeId == episode.id && isPlaying
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = if (playing) MaterialTheme.colorScheme.primaryContainer else homeMixRestingCardColor(),
        modifier = modifier.width(HomeMixLayout.CardWidth).height(homeMixCardHeight())
            .expressiveClickable(shape = shape, onClick = onClick)
            .testTag("home_mix_episode_${episode.id}"),
    ) {
        BoxWithConstraints {
            val artworkSize = if (maxWidth < 280.dp) 72.dp else 86.dp
            Row(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeMixArtwork(episode, podcast, status, isDownloaded, playing, onPlay, artworkSize)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        episode.title,
                        modifier = Modifier.testTag("home_mix_title_${episode.id}"),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = GoogleSansWeight.semiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HomeMixEpisodeTime(episode, status, progress)
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
internal fun HomeMixArtwork(
    episode: Episode,
    podcast: Podcast,
    status: EpisodeStatus,
    downloaded: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    size: Dp,
) {
    Box(Modifier.size(size).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 12.dp, bottomEnd = 22.dp, bottomStart = 12.dp)).testTag("home_mix_artwork_${episode.id}")) {
        OptimizedImage(
            url = episode.imageUrl,
            fallbackUrls = listOf(episode.podcastImageUrl, podcast.imageUrl, podcast.fallbackImageUrl),
            proxyWidth = 512,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (status == EpisodeStatus.COMPLETED || downloaded) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (status == EpisodeStatus.COMPLETED) Icons.Rounded.Check else Icons.Outlined.DownloadDone,
                    stringResource(if (status == EpisodeStatus.COMPLETED) R.string.home_mix_played else R.string.home_mix_downloaded),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        // A full 48dp target in the corner, drawn as a smaller disc so the cover stays visible.
        Surface(
            onClick = onPlay,
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("home_mix_play_${episode.id}"),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(if (size >= 80.dp) 40.dp else 34.dp).background(Color.Black.copy(alpha = 0.58f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.home_hero_pause else R.string.home_hero_play),
                        modifier = Modifier.size(if (size >= 80.dp) 22.dp else 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeMixEpisodeTime(episode: Episode, status: EpisodeStatus, progress: Float) {
    val inProgress = status == EpisodeStatus.IN_PROGRESS && progress > 0f
    if (episode.duration <= 0 && !inProgress) return
    val minutes = if (inProgress) remainingMixMinutes(episode.duration, progress) else episode.duration / 60
    val duration = if (minutes >= 60) {
        stringResource(R.string.home_hero_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.home_hero_minutes, minutes)
    }
    val text = if (inProgress) stringResource(R.string.home_hero_remaining, duration) else duration
    val progressLabel = stringResource(R.string.home_hero_listening_progress)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val timeWidth = (maxWidth - 36.dp).coerceAtLeast(0.dp)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("home_mix_time_${episode.id}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inProgress) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(5.dp)
                    .testTag("home_mix_progress_${episode.id}")
                    .semantics { contentDescription = progressLabel },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
                gapSize = 2.dp,
            )
        }
        if (episode.duration > 0) {
            Text(
            text,
            modifier = (if (inProgress) Modifier.widthIn(max = timeWidth) else Modifier).testTag("home_mix_time_label_${episode.id}"),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp),
        )
        }
    }
}
}
