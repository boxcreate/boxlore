package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
                fontWeight = GoogleSansWeight.semiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

internal fun formatRelativeUpdateLabel(publishedSeconds: Long): String? {
    if (publishedSeconds <= 0L) return null
    val publishedMs = publishedSeconds * 1000L
    val nowMs = System.currentTimeMillis()
    if (publishedMs > nowMs) return "just now"
    val days = TimeUnit.MILLISECONDS.toDays(nowMs - publishedMs)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        days < 30L -> {
            val weeks = days / 7
            if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        }
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(publishedMs))
    }
}

internal fun episodeMetaDurationLabel(
    episode: Episode,
    isInProgress: Boolean,
    progress: Float,
): String {
    val h = episode.duration / 3600
    val m = (episode.duration % 3600) / 60
    return if (isInProgress && progress > 0f) {
        val remaining = ((1f - progress) * episode.duration).toInt()
        val rh = remaining / 3600
        val rm = (remaining % 3600) / 60
        if (rh > 0) "${rh}h ${rm}m left" else "${rm}m left"
    } else {
        if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

@Composable
internal fun ArtworkTitleFallback(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = GoogleSansWeight.semiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
internal fun SubscriptionListRow(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
) {
    val lastSeen = cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes.current[podcast.id]
    val hasRecentNew =
        remember(
            podcast.subscribedAt,
            podcast.latestEpisode?.id,
            podcast.latestEpisode?.publishedDate,
            podcast.rssHasNewEpisodes,
            lastSeen,
        ) {
            podcast.isLatestEpisodeNew(lastSeen)
        }
    val updateLabel = remember(podcast.latestEpisode?.publishedDate) {
        podcast.latestEpisode?.publishedDate?.let { published ->
            formatRelativeUpdateLabel(published)?.let { "Updated $it" }
        }
    }

    val artworkShape = RoundedCornerShape(10.dp)
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        label = "subscriptionListDragScale",
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 0.dp,
        label = "subscriptionListDragElevation",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubscriptionListArtwork(
            podcast = podcast,
            isPinned = isPinned,
            isDragging = isDragging,
            dragModifier = dragModifier,
            artworkShape = artworkShape,
            dragScale = dragScale,
            dragElevation = dragElevation,
        )

        Spacer(modifier = Modifier.width(14.dp))

        SubscriptionListRowText(
            title = podcast.title,
            artist = podcast.artist,
            hasRecentNew = hasRecentNew,
            updateLabel = updateLabel,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
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
    val relativePublished = remember(episode.publishedDate) {
        formatRelativeUpdateLabel(episode.publishedDate)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                fontWeight = GoogleSansWeight.semiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (relativePublished != null) {
                    Text(
                        text = relativePublished.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                if (episode.duration > 0) {
                    val displayText = episodeMetaDurationLabel(episode, isInProgress, progress)
                    Text(
                        text = if (relativePublished != null) "· $displayText" else displayText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isInProgress) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        fontWeight = if (isInProgress) GoogleSansWeight.medium else GoogleSansWeight.regular
                    )
                }
            }
        }

        if (onPlay != null) {
            Spacer(modifier = Modifier.width(10.dp))
            LatestEpisodePlayButton(onPlay = onPlay)
        }
    }
}

@Composable
private fun LatestEpisodePlayButton(onPlay: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnColor by animateColorAsState(
        targetValue =
        if (isPressed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnColor",
    )
    val iconColor by animateColorAsState(
        targetValue =
        if (isPressed) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconColor",
    )

    Surface(
        shape = CircleShape,
        color = btnColor,
        modifier =
        Modifier
            .size(44.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
            ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play episode",
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun SubscriptionGridCard(
    podcast: Podcast,
    lastSeenId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
) {
    val latestEpisodeId = podcast.latestEpisode?.id
    val latestEpisodePubDate = podcast.latestEpisode?.publishedDate ?: 0L

    val hasRecentNew =
        remember(
            podcast.subscribedAt,
            latestEpisodeId,
            latestEpisodePubDate,
            podcast.rssHasNewEpisodes,
            lastSeenId,
        ) {
            podcast.isLatestEpisodeNew(lastSeenId)
        }

    val artworkShape = RoundedCornerShape(12.dp)
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        label = "subscriptionGridDragScale",
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "subscriptionGridDragElevation",
    )
    Box(
        modifier =
        modifier
            .then(dragModifier)
            .fillMaxWidth()
            .aspectRatio(1f)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                shape = artworkShape
                clip = true
            }
            .shadow(elevation = dragElevation, shape = artworkShape, clip = false)
            .expressiveClickable(
                shape = artworkShape,
                pressScaleEnabled = !isDragging,
                onClick = onClick,
            ),
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier =
            Modifier
                .fillMaxSize()
                .clip(artworkShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = artworkShape,
                ),
            errorContent = {
                ArtworkTitleFallback(title = podcast.title)
            },
        )

        if (hasRecentNew) {
            NewEpisodeBadge()
        }
        if (isPinned) {
            SubscriptionPinnedBadge(modifier = Modifier.align(Alignment.BottomStart))
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
    Box(modifier = Modifier.size(72.dp)) {
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotEmpty() }
                ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
            errorContent = {
                ArtworkTitleFallback(title = podcast.title)
            }
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
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                drawStopIndicator = {}
            )
        }
    }
}

private fun parseGenreTokens(raw: String): List<String> =
    raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("podcast", ignoreCase = true) }
        .map { genre ->
            genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

internal fun extractDistinctGenres(podcasts: List<Podcast>): List<String> {
    val customCounts = mutableMapOf<String, Int>()
    val customDisplay = mutableMapOf<String, String>()
    val catalogGenres = mutableSetOf<String>()

    for (pod in podcasts) {
        val customRaw = pod.customGenre?.takeIf { it.isNotBlank() }
        if (customRaw != null) {
            for (tag in parseGenreTokens(customRaw)) {
                val key = tag.lowercase()
                customCounts[key] = (customCounts[key] ?: 0) + 1
                customDisplay.putIfAbsent(key, tag)
            }
        } else {
            catalogGenres.addAll(parseGenreTokens(pod.genre.orEmpty()))
        }
    }

    val sortedCustom = customCounts.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { customDisplay[it.key] ?: it.key }
        )
        .map { customDisplay[it.key] ?: it.key }

    val customLower = customCounts.keys
    val sortedCatalog = catalogGenres
        .filter { it.lowercase() !in customLower }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    return sortedCustom + sortedCatalog
}

internal fun filterPodcastsByGenre(podcasts: List<Podcast>, selectedGenre: String): List<Podcast> {
    if (selectedGenre == "All") return podcasts
    val resolved = resolveSubscriptionGenreItem(selectedGenre, podcasts)
    return podcasts.filter { pod ->
        pod.effectiveGenre.split(",")
            .map { it.trim() }
            .any {
                it.equals(selectedGenre, ignoreCase = true) ||
                    it.equals(resolved.value, ignoreCase = true) ||
                    it.equals(resolved.label, ignoreCase = true)
            }
    }
}
