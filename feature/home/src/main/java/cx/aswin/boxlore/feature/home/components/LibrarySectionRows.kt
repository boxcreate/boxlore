package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList

@Composable
internal fun SelectorCover(
    podcast: Podcast,
    lastSeenId: String?,
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp),
) {
    val latestEpisodeId = podcast.latestEpisode?.id
    val latestEpisodePubDate = podcast.latestEpisode?.publishedDate ?: 0L

    val hasRecentNew =
        remember(podcast.subscribedAt, latestEpisodeId, latestEpisodePubDate, lastSeenId) {
            podcast.isLatestEpisodeNew(lastSeenId)
        }

    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(
        targetValue =
            if (isSelected) {
                1f
            } else if (isAnyPodcastSelected) {
                0.6f
            } else {
                1f
            },
        label = "alpha",
    )
    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 16.dp else 12.dp, label = "cornerRadius")
    val borderStrokeWidth by animateDpAsState(targetValue = if (isSelected) 3.dp else 0.dp, label = "borderStrokeWidth")

    Box(
        modifier =
            modifier
                .scale(scale),
    ) {
        // Inner Box to apply clipping and clickable to the cover image container
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .expressiveClickable(
                        shape = RoundedCornerShape(cornerRadius),
                        onClick = onClick,
                    ).clip(RoundedCornerShape(cornerRadius)),
        ) {
            OptimizedImage(
                url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
                proxyWidth = 120,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha),
            )

            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius)),
                )
            }
        }

        // New episode "NEW" text chip indicator (overlapping the top right corner) with a slow shimmer effect
        if (hasRecentNew && !isSelected) {
            NewEpisodeBadge(
                modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
            )
        }
    }
}

@Composable
internal fun DenseEpisodeRow(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    showPodcastTitle: Boolean = true,
    overrideStatus: EpisodeStatus? = null,
    overrideProgress: Float? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isEligibleForNewTag: Boolean = true,
) {
    val status = overrideStatus ?: if (podcast.latestEpisode?.id == episode.id) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = overrideProgress ?: if (podcast.latestEpisode?.id == episode.id) (podcast.resumeProgress ?: 0f) else 0f
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isInProgress = status == EpisodeStatus.IN_PROGRESS
    val isCurrentPlaying = currentPlayingEpisodeId == episode.id && isPlaying

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .expressiveClickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            OptimizedImage(
                url = (
                    episode.imageUrl?.takeIf { it.isNotEmpty() } ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                        ?: podcast.fallbackImageUrl
                ),
                proxyWidth = 112,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )

            // Transparent mini glassmorphic play button centered on artwork
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .expressiveClickable(onClick = onPlay),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector =
                            if (isCurrentPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                        contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (isCompleted) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
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

            if (isInProgress && progress > 0f) {
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
        }

        Spacer(modifier = Modifier.width(12.dp))

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showPodcastTitle) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val isNew =
                    isEligibleForNewTag &&
                        status == EpisodeStatus.UNPLAYED &&
                        podcast.subscribedAt > 0L &&
                        episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)
                if (isNew) {
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
                val dateText = formatRelativeDate(episode.publishedDate)
                if (dateText.isNotEmpty()) {
                    val prefix = if (showPodcastTitle || isNew) "• " else ""
                    Text(
                        text = "$prefix$dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                if (episode.duration > 0) {
                    val h = episode.duration / 3600
                    val m = (episode.duration % 3600) / 60
                    val displayText =
                        if (isInProgress && progress > 0f) {
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
                        color =
                            if (isInProgress) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                        fontWeight = if (isInProgress) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
