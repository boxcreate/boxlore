package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.sections.stripHtml

@Composable
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
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleLike: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMarkPlayedClick: () -> Unit,
    showMarkPlayedButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .expressiveClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation =
            androidx.compose.material3.CardDefaults
                .outlinedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp), // Generous padding inside the card
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
                            url = episode.imageUrl,
                            proxyWidth = 200, // 76dp thumbnails
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    if (isCompleted) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    if (isUpNext) {
                        Surface(
                            shape = ExpressiveShapes.Pill,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Text(
                                "UP NEXT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Metadata
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Podcast 2.0: Season/Episode number
                        val seLabel =
                            buildString {
                                episode.seasonNumber?.let { append("S$it ") }
                                episode.episodeNumber?.let { append("E$it") }
                            }.trim()
                        if (seLabel.isNotEmpty()) {
                            Text(
                                text = seLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            text = formatRelativeDate(episode.publishedDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            text = formatDuration(episode.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Podcast 2.0: Episode type badge
                        if (episode.episodeType != null && episode.episodeType != "full") {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Surface(
                                shape = ExpressiveShapes.Pill,
                                color =
                                    if (episode.episodeType == "trailer") {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                            ) {
                                Text(
                                    text = episode.episodeType!!.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (episode.episodeType == "trailer") {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        // Video Badge inside Metadata Row
                        if (episode.enclosureType?.startsWith("video/") == true) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )

                    // Description Preview
                    val stripped = stripHtml(episode.description)
                    if (stripped.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stripped,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Control Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween, // Push play button to edge
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
            ) {
                // Secondary Controls (Tonal squircle for premium feel on card)
                cx.aswin.boxlore.core.designsystem.components.AdvancedPlayerControls(
                    isLiked = isLiked,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    colorScheme = MaterialTheme.colorScheme,
                    onLikeClick = onToggleLike,
                    onDownloadClick = onDownloadClick,
                    onQueueClick = onQueueClick,
                    style = cx.aswin.boxlore.core.designsystem.components.ControlStyle.TonalSquircle,
                    overrideColor = accentColor,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    showAddQueueIcon = true,
                    isQueued = isQueued,
                    showShareButton = false,
                    isPlayed = isCompleted,
                    showMarkPlayedButton = showMarkPlayedButton,
                    onMarkPlayedClick = onMarkPlayedClick,
                    controlSize = 40.dp,
                )

                // Play Button
                cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButton(
                    onClick = onPlayClick,
                    state =
                        cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButtonState(
                            isPlaying = isPlaying,
                            isResume = isResume,
                            progress = progress,
                            timeText = timeLeft,
                        ),
                    accentColor = accentColor,
                    modifier =
                        Modifier
                            .height(44.dp)
                            .padding(start = 16.dp)
                            .weight(1f),
                )
            }
        }
    }
}
