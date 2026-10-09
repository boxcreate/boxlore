package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.logic.EpisodeArtworkLogic
import cx.aswin.boxlore.feature.info.sections.stripHtml

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun EpisodeListItem(
    episode: Episode,
    isLiked: Boolean,
    accentColor: Color,
    // Playback State
    isPlaying: Boolean,
    isResume: Boolean,
    progress: Float,
    timeLeft: String?,
    // Download State
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isQueued: Boolean,
    isCompleted: Boolean,
    isUpNext: Boolean = false,
    selectionActive: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPlayClick: () -> Unit,
    onToggleLike: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMarkPlayedClick: () -> Unit,
    showMarkPlayedButton: Boolean = true,
    podcastImageUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val markPlayedClick = rememberPodcastEpisodeControlClick(onMarkPlayedClick)
    val markPlayedLabel = stringResource(if (isCompleted) R.string.episode_info_mark_unplayed else R.string.episode_info_mark_played)
    val selectLabel = stringResource(R.string.podcast_episode_select)
    Surface(
        modifier = modifier.fillMaxWidth().expressiveClickable(
            onLongClickLabel = selectLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            // 1. Content Row (Image + Text)
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Artwork with completion checkmark
                Box(modifier = Modifier.size(76.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        OptimizedImage(
                            url = EpisodeArtworkLogic.listUrl(episode, podcastImageUrl),
                            proxyWidth = 200, // 76dp thumbnails
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    if (isSelected || isCompleted) {
                        Box(
                            modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    CircleShape,
                                ).border(
                                    1.dp,
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(if (isSelected) R.string.podcast_episode_selected else R.string.episode_info_played),
                                tint =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    if (isUpNext) {
                        Surface(
                            shape = ExpressiveShapes.Pill,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.podcast_episode_up_next),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = GoogleSansWeight.bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    fun formatDuration(seconds: Int): String {
                        val hours = seconds / 3600
                        val minutes = (seconds % 3600) / 60
                        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    }

                    fun formatRelativeDate(timestampSeconds: Long): String {
                        if (timestampSeconds == 0L) return ""
                        val now = System.currentTimeMillis() / 1000
                        val diff = now - timestampSeconds
                        return when {
                            diff < 3600 -> "${diff / 60}m ago"
                            diff < 86400 -> "${diff / 3600}h ago"
                            diff < 604800 -> "${diff / 86400}d ago"
                            diff < 2592000 -> "${diff / 604800}w ago"
                            diff < 31536000 -> "${diff / 2592000}mo ago"
                            else -> "${diff / 31536000}y ago"
                        }
                    }

                    val seasonEpisode = buildString {
                        episode.seasonNumber?.let { append("S$it ") }
                        episode.episodeNumber?.let { append("E$it") }
                    }.trim()
                    Text(
                        text = listOf(seasonEpisode, formatRelativeDate(episode.publishedDate), formatDuration(episode.duration))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val type = episode.episodeType?.takeUnless { it == "full" }
                    val isVideo = episode.enclosureType?.startsWith("video/") == true
                    if (type != null || isVideo) {
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (type != null) {
                                val typeLabel = when (type) {
                                    "bonus" -> stringResource(R.string.episode_info_bonus)
                                    "trailer" -> stringResource(R.string.episode_info_trailer)
                                    else -> type.replaceFirstChar { it.uppercase() }
                                }
                                EpisodeTypeChip(typeLabel)
                            }
                            if (isVideo) EpisodeTypeChip(stringResource(R.string.episode_info_video), video = true)
                        }
                    }

                    // Description Preview
                    val stripped = stripHtml(episode.description)
                    if (stripped.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stripped,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            if (!selectionActive) {
                Spacer(modifier = Modifier.height(14.dp))

                PodcastEpisodeActions(
                    state = PodcastEpisodeActionsState(isPlaying, isResume, isLiked, isDownloaded, isDownloading, isQueued),
                    callbacks = PodcastEpisodeActionsCallbacks(onPlayClick, onToggleLike, onDownloadClick, onQueueClick),
                    accentColor = accentColor,
                )
                if (showMarkPlayedButton) {
                    AssistChip(
                        onClick = markPlayedClick,
                        label = { Text(stringResource(if (isCompleted) R.string.episode_info_played else R.string.episode_info_mark_played)) },
                        leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) },
                        shape = ExpressiveShapes.Pill,
                        border = null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = markPlayedLabel
                        },
                    )
                }
                podcastEpisodeCardProgress(isResume, isCompleted, progress)?.let {
                    PodcastEpisodeProgress(it, timeLeft, accentColor)
                }
            }
        }
    }
}

@Composable
private fun EpisodeTypeChip(label: String, video: Boolean = false) {
    Surface(shape = ExpressiveShapes.Pill, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (video) Icon(Icons.Rounded.Videocam, null, Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = GoogleSansWeight.medium)
        }
    }
}
