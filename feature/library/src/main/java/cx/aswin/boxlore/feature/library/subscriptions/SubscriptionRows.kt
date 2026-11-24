package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.boxloreCombinedClickable as combinedClickable
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.tabletInputFeedback
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.LocaleFormats.DateStyle
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.library.LibraryEpisodeRow
import cx.aswin.boxlore.feature.library.R
import java.util.Calendar
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

/** The New Episodes date group for [timestampSeconds]: Today, Yesterday, a weekday or a date in [locale]. */
internal fun getChronologicalHeader(timestampSeconds: Long, locale: Locale): UiText {
    if (timestampSeconds == 0L) return UiText.res(R.string.library_header_older)
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
        diffDays == 0L -> UiText.res(R.string.library_header_today)
        diffDays == 1L -> UiText.res(R.string.library_header_yesterday)
        diffDays < 7L -> UiText.raw(LocaleFormats.date(timestampMs, DateStyle.WEEKDAY, locale))
        else -> UiText.raw(LocaleFormats.date(timestampMs, DateStyle.LONG_DAY_MONTH_YEAR, locale))
    }
}

/** How long ago an episode came out, as a line on its own ("Today", "3 days ago", "Mar 5, 2026"). */
internal fun formatRelativeUpdateLabel(
    publishedSeconds: Long,
    locale: Locale,
    nowMs: Long = System.currentTimeMillis(),
): UiText? = relativeUpdate(publishedSeconds, nowMs)?.let { age ->
    when (age) {
        RelativeUpdate.JustNow -> UiText.res(R.string.library_relative_just_now)
        RelativeUpdate.Today -> UiText.res(R.string.library_relative_today)
        RelativeUpdate.Yesterday -> UiText.res(R.string.library_relative_yesterday)
        is RelativeUpdate.Days -> UiText.plural(R.plurals.library_relative_days_ago, age.count, age.count)
        is RelativeUpdate.Weeks -> UiText.plural(R.plurals.library_relative_weeks_ago, age.count, age.count)
        is RelativeUpdate.OnDate -> UiText.raw(LocaleFormats.date(age.epochMillis, DateStyle.DAY_MONTH_YEAR, locale))
    }
}

/** "Updated today", "Updated 3 days ago" or "Updated Mar 5, 2026" for a show's latest episode. */
internal fun formatUpdatedLabel(
    publishedSeconds: Long,
    locale: Locale,
    nowMs: Long = System.currentTimeMillis(),
): UiText? = relativeUpdate(publishedSeconds, nowMs)?.let { age ->
    when (age) {
        RelativeUpdate.JustNow -> UiText.res(R.string.library_updated_just_now)
        RelativeUpdate.Today -> UiText.res(R.string.library_updated_today)
        RelativeUpdate.Yesterday -> UiText.res(R.string.library_updated_yesterday)
        is RelativeUpdate.Days -> UiText.plural(R.plurals.library_updated_days_ago, age.count, age.count)
        is RelativeUpdate.Weeks -> UiText.plural(R.plurals.library_updated_weeks_ago, age.count, age.count)
        is RelativeUpdate.OnDate ->
            UiText.res(R.string.library_updated_on_date, LocaleFormats.date(age.epochMillis, DateStyle.DAY_MONTH_YEAR, locale))
    }
}

private sealed interface RelativeUpdate {
    data object JustNow : RelativeUpdate
    data object Today : RelativeUpdate
    data object Yesterday : RelativeUpdate
    data class Days(val count: Int) : RelativeUpdate
    data class Weeks(val count: Int) : RelativeUpdate
    data class OnDate(val epochMillis: Long) : RelativeUpdate
}

private fun relativeUpdate(publishedSeconds: Long, nowMs: Long): RelativeUpdate? {
    if (publishedSeconds <= 0L) return null
    val publishedMs = publishedSeconds * 1000L
    if (publishedMs > nowMs) return RelativeUpdate.JustNow
    val days = TimeUnit.MILLISECONDS.toDays(nowMs - publishedMs)
    return when {
        days == 0L -> RelativeUpdate.Today
        days == 1L -> RelativeUpdate.Yesterday
        days < 7L -> RelativeUpdate.Days(days.toInt())
        days < 30L -> RelativeUpdate.Weeks((days / 7).toInt())
        else -> RelativeUpdate.OnDate(publishedMs)
    }
}

/** The episode's length ("1h 5m"), or what is left of it ("45m left") while it is in progress. */
internal fun episodeMetaDurationLabel(
    episode: Episode,
    isInProgress: Boolean,
    progress: Float,
    locale: Locale,
): UiText = if (isInProgress && progress > 0f) {
    val remaining = ((1f - progress) * episode.duration).toLong()
    UiText.res(R.string.library_time_left, wholeMinutesDuration(remaining, locale))
} else {
    UiText.raw(wholeMinutesDuration(episode.duration.toLong(), locale))
}

/** Hours and minutes, narrow ("1h 5m"), dropping leftover seconds; under a minute it shows seconds. */
private fun wholeMinutesDuration(seconds: Long, locale: Locale): String {
    val wholeMinutes = seconds / 60 * 60
    return LocaleFormats.duration(if (wholeMinutes > 0) wholeMinutes else seconds, locale, LocaleFormats.DurationWidth.NARROW)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubscriptionListRow(
    podcast: Podcast,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
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
    val locale = currentLocale()
    val updateLabel = remember(podcast.latestEpisode?.publishedDate, locale) {
        podcast.latestEpisode?.publishedDate?.let { published ->
            formatUpdatedLabel(published, locale)
        }
    }?.asString()

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
            .then(
                when {
                    isDragging || (onClick == null && onLongClick == null) -> Modifier
                    onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
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
    modifier: Modifier = Modifier,
    /** A swipe to played whose Undo still shows: drawn as played before it is saved. */
    showAsPlayed: Boolean = false,
) {
    val status = podcast.episodeStatus
    val progress = podcast.resumeProgress ?: 0f
    val isCompleted = showAsPlayed || status == EpisodeStatus.COMPLETED
    val isInProgress = !showAsPlayed && status == EpisodeStatus.IN_PROGRESS
    val locale = currentLocale()
    val relativePublished = remember(episode.publishedDate, locale) {
        formatRelativeUpdateLabel(episode.publishedDate, locale)
    }?.asString()

    // The shared Library episode row, with New Episodes' date and length line and its play button.
    LibraryEpisodeRow(
        title = episode.title,
        artwork = {
            EpisodeRowArtwork(
                episode = episode,
                podcast = podcast,
                isCompleted = isCompleted,
                isInProgress = isInProgress,
                progress = progress
            )
        },
        onClick = onClick,
        modifier = modifier,
        subtitle = podcast.title,
        meta = {
            if (relativePublished != null) {
                Text(
                    text = relativePublished.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            if (episode.duration > 0) {
                val displayText = episodeMetaDurationLabel(episode, isInProgress, progress, locale).asString()
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
        },
        trailing = onPlay?.let { play -> { LatestEpisodePlayButton(onPlay = play) } },
    )
}

/** The round play button at the end of a Library episode row. */
@Composable
internal fun LatestEpisodePlayButton(onPlay: () -> Unit) {
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
            .tabletInputFeedback(interactionSource, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
            ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.library_play_episode),
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun Modifier.subscriptionCardClickable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    isDragging: Boolean,
    shape: Shape,
): Modifier {
    if (isDragging || (onClick == null && onLongClick == null)) return this
    return if (onLongClick != null) {
        expressiveClickable(
            shape = shape,
            onLongClick = onLongClick,
            onClick = onClick ?: {},
        )
    } else {
        expressiveClickable(
            shape = shape,
            pressScaleEnabled = true,
            onClick = onClick ?: {},
        )
    }
}

@Composable
internal fun SubscriptionGridCard(
    podcast: Podcast,
    lastSeenId: String?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    isDragging: Boolean = false,
    onLongClick: (() -> Unit)? = null,
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
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Poster)
            .aspectRatio(1f)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                shape = artworkShape
                clip = true
            }
            .shadow(elevation = dragElevation, shape = artworkShape, clip = false)
            .subscriptionCardClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                isDragging = isDragging,
                shape = artworkShape,
            ),
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            fallbackUrls = podcast.artworkUrls(),
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
            fallbackUrls = episode.artworkUrls(podcast),
            proxyWidth = 400,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
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
                    contentDescription = stringResource(R.string.library_played),
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
