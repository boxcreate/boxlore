package cx.aswin.boxlore.feature.library.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.library.R
import java.util.Locale

fun DownloadedEpisodeEntity.toEpisode() = Episode(
    id = episodeId,
    title = episodeTitle,
    description = episodeDescription ?: "",
    audioUrl = localFilePath,
    imageUrl = episodeImageUrl,
    podcastImageUrl = podcastImageUrl,
    // The episode keeps its show, so queueing or playing it never takes another show's name.
    podcastTitle = podcastName.takeIf(String::isNotBlank),
    podcastId = podcastId.takeIf(String::isNotBlank),
    duration = (durationMs / 1000).toInt(),
    publishedDate = publishedDate,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
)

fun DownloadedEpisodeEntity.toPodcast() = Podcast(
    id = podcastId,
    title = podcastName,
    artist = "",
    imageUrl = podcastImageUrl ?: "",
    description = "",
    genre = ""
)

internal data class PodcastGroup(
    val podcastId: String,
    val podcastName: String,
    val podcastImageUrl: String?,
    val episodes: List<DownloadedEpisodeEntity>,
    val totalSizeBytes: Long,
    val latestDownloadedAt: Long
)

/** Byte units, smallest first, in 1024 steps. */
private val SIZE_UNITS = listOf(
    R.string.library_size_bytes,
    R.string.library_size_kilobytes,
    R.string.library_size_megabytes,
    R.string.library_size_gigabytes,
    R.string.library_size_terabytes,
)

/** A file size such as "420.0 MB", with the number written the way [locale] writes it. */
internal fun formatSize(bytes: Long, locale: Locale): UiText {
    if (bytes <= 0) return UiText.res(R.string.library_size_bytes, "0")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, SIZE_UNITS.lastIndex)
    val amount = String.format(locale, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
    return UiText.res(SIZE_UNITS[digitGroups], amount)
}

/** How long ago something was saved, short ("5m ago", "2d ago"); under a minute or ahead it says "Just now". */
internal fun formatRelativeDate(timestampSeconds: Long, locale: Locale, nowMs: Long = System.currentTimeMillis()): UiText? {
    if (timestampSeconds == 0L) return null
    val diffSeconds = nowMs / 1000 - timestampSeconds
    if (diffSeconds < 60) return UiText.res(R.string.library_relative_just_now)
    return UiText.raw(LocaleFormats.compactAgo(timestampSeconds * 1000L, nowMs, locale))
}

@Composable
internal fun PodcastListShowCard(
    group: PodcastGroup,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    selection: DownloadShowCardSelection = DownloadShowCardSelection(),
) {
    val title = group.podcastName
    val imageUrl = group.podcastImageUrl
    val locale = currentLocale()
    val countLine = group.episodes.downloadSummary().countLine(locale = locale).asString()
    val latestDownloadedAt = group.latestDownloadedAt
    Row(
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Episode)
            .fillMaxWidth()
            .expressiveClickable(
                onClick = {
                    if (selection.active) {
                        selection.onSelectedChange(!selection.selected)
                    } else {
                        onClick()
                    }
                },
                onLongClick = selection.onLongClick,
                onLongClickLabel = stringResource(R.string.library_select),
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selection.active) {
            Checkbox(
                checked = selection.selected,
                onCheckedChange = selection.onSelectedChange
            )
        }

        OptimizedImage(
            proxyWidth = 180,
            url = imageUrl,
            fallbackUrls = listOf(group.episodes.firstOrNull()?.episodeImageUrl),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val latestRelative = formatRelativeDate(latestDownloadedAt / 1000L, locale)?.asString()
            if (latestRelative != null) {
                Text(
                    text = stringResource(R.string.library_downloads_latest, latestRelative),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        if (!selection.active) {
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.library_downloads_play_show),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

internal data class DownloadShowCardSelection(
    val active: Boolean = false,
    val selected: Boolean = false,
    val onSelectedChange: (Boolean) -> Unit = {},
    val onLongClick: () -> Unit = {},
)

internal data class DownloadMultiSelect(
    val active: Boolean,
    val ids: Set<String>,
)

/**
 * Long-press a downloads row: enter multi-select with [itemId] checked.
 * If already selecting, toggle [itemId] like a tap.
 */
internal fun longPressDownloadSelection(
    selectionActive: Boolean,
    selectedIds: Set<String>,
    itemId: String,
): DownloadMultiSelect {
    if (!selectionActive) {
        return DownloadMultiSelect(active = true, ids = selectedIds + itemId)
    }
    val ids = if (itemId in selectedIds) selectedIds - itemId else selectedIds + itemId
    return DownloadMultiSelect(active = true, ids = ids)
}

internal fun MutableList<String>.applyLongPressDownloadSelection(
    selectionActive: Boolean,
    itemId: String,
): Boolean {
    val next = longPressDownloadSelection(selectionActive, toSet(), itemId)
    clear()
    addAll(next.ids)
    return next.active
}

/** Saves a selection of IDs, so select mode keeps what was ticked across recreation. */
internal fun selectedIdsSaver(): Saver<SnapshotStateList<String>, Any> = listSaver(
    save = { it.toList() },
    restore = { saved -> saved.toMutableStateList() },
)
