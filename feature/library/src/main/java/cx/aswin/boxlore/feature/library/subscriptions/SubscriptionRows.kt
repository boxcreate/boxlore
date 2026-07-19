package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun DateHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

internal fun getChronologicalHeader(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return "Older"
    val timestampMs = timestampSeconds * 1000L
    
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestampMs }
    
    val nowDay = now.clone() as Calendar
    nowDay.set(Calendar.HOUR_OF_DAY, 0)
    nowDay.set(Calendar.MINUTE, 0)
    nowDay.set(Calendar.SECOND, 0)
    nowDay.set(Calendar.MILLISECOND, 0)
    
    val timeDay = time.clone() as Calendar
    timeDay.set(Calendar.HOUR_OF_DAY, 0)
    timeDay.set(Calendar.MINUTE, 0)
    timeDay.set(Calendar.SECOND, 0)
    timeDay.set(Calendar.MILLISECOND, 0)
    
    val diffDays = (nowDay.timeInMillis - timeDay.timeInMillis) / (24 * 60 * 60 * 1000L)
    
    return when {
        diffDays == 0L -> "Today"
        diffDays == 1L -> "Yesterday"
        diffDays < 7L -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestampMs))
        }
        else -> {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}

@Composable
internal fun SubscriptionListRow(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = podcast.artist.takeIf { it.isNotEmpty() } ?: "Podcast",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun LatestEpisodeRow(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val status = podcast.episodeStatus
    val progress = podcast.resumeProgress ?: 0f
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isInProgress = status == EpisodeStatus.IN_PROGRESS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode artwork with status overlay
        EpisodeRowArtwork(
            episode = episode,
            podcast = podcast,
            isCompleted = isCompleted,
            isInProgress = isInProgress,
            progress = progress
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (episode.duration > 0) {
                    val h = episode.duration / 3600
                    val m = (episode.duration % 3600) / 60
                    val displayText = if (isInProgress && progress > 0f) {
                        val remaining = ((1f - progress) * episode.duration).toInt()
                        val rh = remaining / 3600
                        val rm = (remaining % 3600) / 60
                        if (rh > 0) "${rh}h ${rm}m left" else "${rm}m left"
                    } else {
                        if (h > 0) "${h}h ${m}m" else "${m}m"
                    }
                    Text(
                        text = "• $displayText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInProgress) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = if (isInProgress) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        // Play button
        if (onPlay != null) {
            Spacer(modifier = Modifier.width(8.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val btnColor by animateColorAsState(
                targetValue = if (isPressed) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.primaryContainer,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "btnColor"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isPressed) MaterialTheme.colorScheme.onPrimary
                             else MaterialTheme.colorScheme.primary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "iconColor"
            )

            Surface(
                shape = CircleShape,
                color = btnColor,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onPlay
                    )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play episode",
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubscriptionGridCard(
    podcast: Podcast,
    lastSeenId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestEpisodeId = podcast.latestEpisode?.id
    val latestEpisodePubDate = podcast.latestEpisode?.publishedDate ?: 0L
    
    val hasRecentNew = remember(podcast.subscribedAt, latestEpisodeId, latestEpisodePubDate, lastSeenId) {
        podcast.isLatestEpisodeNew(lastSeenId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .expressiveClickable(onClick = onClick)
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        // New episode "NEW" text chip indicator (overlapping the top right corner) with a slow shimmer effect
        if (hasRecentNew) {
            NewEpisodeBadge()
        }
    }
}

@Composable
internal fun EpisodeRowArtwork(
    episode: Episode,
    podcast: Podcast,
    isCompleted: Boolean,
    isInProgress: Boolean,
    progress: Float
) {
    Box(modifier = Modifier.size(64.dp)) {
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotEmpty() }
                ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        if (isCompleted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Played",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        if (isInProgress && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                drawStopIndicator = {}
            )
        }
    }
}

internal fun extractDistinctGenres(podcasts: List<Podcast>): List<String> {
    return podcasts.flatMap { pod ->
        pod.genre.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("podcast", ignoreCase = true) }
            .map { genre ->
                genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
}

internal fun filterPodcastsByGenre(podcasts: List<Podcast>, selectedGenre: String): List<Podcast> {
    if (selectedGenre == "All") return podcasts
    return podcasts.filter { pod ->
        pod.genre.split(",")
            .map { it.trim() }
            .any { it.equals(selectedGenre, ignoreCase = true) }
    }
}

