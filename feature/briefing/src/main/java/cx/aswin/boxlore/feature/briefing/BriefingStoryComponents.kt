package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.UiText
import java.net.URI

internal fun formatChapterTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
}

/** Time left in the brief ("3m left"), in the app language; null once nothing is left. */
@Composable
internal fun formatRemaining(totalSeconds: Long): String? {
    if (totalSeconds <= 0) return null
    val length = LocaleFormats.duration(totalSeconds, currentLocale(), LocaleFormats.DurationWidth.NARROW)
    return stringResource(R.string.briefing_screen_time_left, length)
}

/** The brief's length, rounded to whole minutes ("4 min listen"); null when it is unknown. */
internal fun formatBriefingListenDuration(durationMs: Long): UiText? {
    if (durationMs <= 0) return null
    val minutes = ((durationMs + 30_000L) / 60_000L).coerceAtLeast(1).toInt()
    return UiText.plural(R.plurals.briefing_screen_listen_minutes, minutes, minutes)
}

internal fun getDomainName(url: String): String = try {
    val uri = URI(url)
    val domain = uri.host ?: ""
    if (domain.startsWith("www.")) domain.substring(4) else domain
} catch (e: Exception) {
    url
}

/** A related episode tile's test tag. */
internal const val RELATED_EPISODE_TILE_TAG = "briefing_related_episode"

@Composable
internal fun CompactEpisodeChip(
    episode: Episode,
    isActiveCard: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A raised tile, so it has an edge against its story card.
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .width(260.dp)
            .testTag(RELATED_EPISODE_TILE_TAG)
            .expressiveClickable(
                shape = MaterialTheme.shapes.large,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactEpisodeImage(episode)
            CompactEpisodeDetails(
                episode = episode,
                isActiveCard = isActiveCard,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun CompactEpisodeImage(episode: Episode) {
    OptimizedImage(
        url = compactEpisodeImageUrl(episode),
        fallbackUrls = listOf(episode.podcastImageUrl),
        proxyWidth = 120,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(52.dp)
            .clip(MaterialTheme.shapes.small)
    )
}

private fun compactEpisodeImageUrl(episode: Episode): String? = episode.imageUrl?.takeIf { it.isNotEmpty() }
    ?: episode.podcastImageUrl?.takeIf { it.isNotEmpty() }

@Composable
private fun androidx.compose.foundation.layout.RowScope.CompactEpisodeDetails(
    episode: Episode,
    isActiveCard: Boolean,
    accentColor: Color,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CompactEpisodeTitle(episode.title)
        CompactEpisodePodcastTitle(
            podcastTitle = episode.podcastTitle,
            isActiveCard = isActiveCard,
            accentColor = accentColor,
        )
        CompactEpisodeInfo(
            infoText = compactEpisodeInfoText(episode),
        )
    }
}

@Composable
private fun CompactEpisodeTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = GoogleSansWeight.bold,
            fontSize = 13.sp,
            lineHeight = 16.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CompactEpisodePodcastTitle(
    podcastTitle: String?,
    isActiveCard: Boolean,
    accentColor: Color,
) {
    if (podcastTitle.isNullOrEmpty()) return
    Text(
        text = podcastTitle,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            fontWeight = GoogleSansWeight.medium
        ),
        color = if (isActiveCard) accentColor else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CompactEpisodeInfo(
    infoText: String,
) {
    if (infoText.isEmpty()) return
    Text(
        text = infoText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun compactEpisodeInfoText(episode: Episode): String {
    val locale = currentLocale()
    return listOfNotNull(
        episode.duration.takeIf { it > 0 }?.let { LocaleFormats.duration(it / 60 * 60L, locale) },
        episode.publishedDate.takeIf { it > 0 }?.let { LocaleFormats.date(it * 1000L, LocaleFormats.DateStyle.DAY_MONTH, locale) },
    ).joinToString(" • ")
}
