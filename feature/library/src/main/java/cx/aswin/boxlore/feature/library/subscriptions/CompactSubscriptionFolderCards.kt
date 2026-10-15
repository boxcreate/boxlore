package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.rememberNewEpisodeBadgeShimmerBrush
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.boxloreCombinedClickable as combinedClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes

/**
 * 1×1 compact folder card rendered in the Subscriptions grid, sharing the same 1:1 cell size
 * as standard podcast cards.
 *
 * Supports two distinct display styles:
 * 1. Folder Icon Mode (`effectiveShowPodcastGrid == false`): Clean folder icon with show count.
 * 2. Podcast Grid Mode (`effectiveShowPodcastGrid == true`): 2×2 mini-grid where shows are directly clickable,
 *    featuring an anchored bottom title pill for folder identity and direct folder expansion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Compact1x1FolderCard(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    actions: FolderCardActions,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val lastSeenEpisodes = LocalLastSeenEpisodes.current
    val showFolderBadge = remember(folder.effectiveShowPodcastGrid, podcasts, lastSeenEpisodes) {
        shouldShowFolderBadge(folder.effectiveShowPodcastGrid, podcasts, lastSeenEpisodes)
    }
    val shape = RoundedCornerShape(14.dp)
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        label = "compactFolderDragScale",
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "compactFolderDragElevation",
    )

    Box(
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Poster)
            .then(dragModifier)
            .fillMaxWidth()
            .aspectRatio(1f)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                this.shape = shape
                clip = false
            }
            .shadow(elevation = dragElevation, shape = shape, clip = false),
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (showFolderBadge) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!folder.effectiveShowPodcastGrid) {
                CompactFolderIconContent(
                    folder = folder,
                    podcastCount = podcasts.size,
                    onFolderClick = actions.onFolderClick,
                    onFolderLongClick = actions.onFolderLongClick,
                    isReordering = isReordering,
                )
            } else {
                CompactPodcastGridContent(
                    folder = folder,
                    podcasts = podcasts,
                    lastSeenEpisodes = lastSeenEpisodes,
                    actions = actions,
                    isReordering = isReordering,
                )
            }
        }

        CompactFolderBadgeOverlay(
            showBadge = showFolderBadge,
            isPodcastGrid = folder.effectiveShowPodcastGrid,
        )

        if (folder.effectiveShowPodcastGrid) {
            CompactFolderTitlePill(
                folder = folder,
                onFolderClick = actions.onFolderClick,
                onFolderLongClick = actions.onFolderLongClick,
                isReordering = isReordering,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 8.dp)
                    .zIndex(5f),
            )
        }
    }
}

private fun shouldShowFolderBadge(
    effectiveShowPodcastGrid: Boolean,
    podcasts: List<Podcast>,
    lastSeenEpisodes: Map<String, String>,
): Boolean = if (effectiveShowPodcastGrid) {
    podcasts.size > 4 && hasFolderOverflowNew(podcasts.drop(3), lastSeenEpisodes)
} else {
    hasAnyFolderShowNew(podcasts, lastSeenEpisodes)
}

@Composable
private fun BoxScope.CompactFolderBadgeOverlay(
    showBadge: Boolean,
    isPodcastGrid: Boolean,
) {
    if (!showBadge) return
    if (isPodcastGrid) {
        FolderFloatingBadge()
    } else {
        NewEpisodeBadge()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactFolderIconContent(
    folder: SubscriptionFolder,
    podcastCount: Int,
    onFolderClick: (String) -> Unit,
    onFolderLongClick: (SubscriptionFolder) -> Unit,
    isReordering: Boolean = false,
) {
    val folderIcon = GenreIcons.folderIconOrFallback(folder.icon)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isReordering) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClick = { onFolderClick(folder.id) },
                        onLongClick = { onFolderLongClick(folder) },
                    )
                }
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = folderIcon,
                contentDescription = folder.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "$podcastCount shows",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactPodcastGridContent(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    lastSeenEpisodes: Map<String, String>,
    actions: FolderCardActions,
    isReordering: Boolean = false,
) {
    val slots = remember(podcasts, lastSeenEpisodes) {
        (0..3).map { index ->
            val pod = podcasts.getOrNull(index)
            val isNew = pod?.let { it.isLatestEpisodeNew(lastSeenEpisodes[it.id]) } ?: false
            pod to isNew
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isReordering) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClick = { actions.onFolderClick(folder.id) },
                        onLongClick = { actions.onFolderLongClick(folder) },
                    )
                }
            )
            .padding(5.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val onFolderLongClick = { actions.onFolderLongClick(folder) }
                MiniPodcastSlot(
                    podcast = slots[0].first,
                    hasNewEpisode = slots[0].second,
                    onClick = actions.onPodcastClick,
                    onLongClick = onFolderLongClick,
                    onEmptyClick = { actions.onFolderClick(folder.id) },
                    isReordering = isReordering,
                    modifier = Modifier.weight(1f),
                )
                MiniPodcastSlot(
                    podcast = slots[1].first,
                    hasNewEpisode = slots[1].second,
                    onClick = actions.onPodcastClick,
                    onLongClick = onFolderLongClick,
                    onEmptyClick = { actions.onFolderClick(folder.id) },
                    isReordering = isReordering,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val onFolderLongClick = { actions.onFolderLongClick(folder) }
                MiniPodcastSlot(
                    podcast = slots[2].first,
                    hasNewEpisode = slots[2].second,
                    onClick = actions.onPodcastClick,
                    onLongClick = onFolderLongClick,
                    onEmptyClick = { actions.onFolderClick(folder.id) },
                    isReordering = isReordering,
                    modifier = Modifier.weight(1f),
                )
                if (podcasts.size > 4) {
                    CompactOverflowSlot(
                        count = podcasts.size - 3,
                        onClick = { actions.onFolderClick(folder.id) },
                        onLongClick = onFolderLongClick,
                        isReordering = isReordering,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MiniPodcastSlot(
                        podcast = slots[3].first,
                        hasNewEpisode = slots[3].second,
                        onClick = actions.onPodcastClick,
                        onLongClick = onFolderLongClick,
                        onEmptyClick = { actions.onFolderClick(folder.id) },
                        isReordering = isReordering,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactOverflowSlot(
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(
                if (isReordering) {
                    Modifier
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactFolderTitlePill(
    folder: SubscriptionFolder,
    onFolderClick: (String) -> Unit,
    onFolderLongClick: (SubscriptionFolder) -> Unit,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val folderIcon = GenreIcons.folderIconOrFallback(folder.icon)
    val displayName = remember(folder.name) {
        truncateCompactFolderName(folder.name)
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (isReordering) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = { onFolderClick(folder.id) },
                            onLongClick = { onFolderLongClick(folder) },
                        )
                    }
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .widthIn(max = 84.dp),
        ) {
            Icon(
                imageVector = folderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(10.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                ),
                fontWeight = GoogleSansWeight.bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPodcastSlot(
    podcast: Podcast?,
    onClick: (String) -> Unit,
    onLongClick: () -> Unit = {},
    onEmptyClick: () -> Unit = {},
    hasNewEpisode: Boolean = false,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val miniShape = RoundedCornerShape(6.dp)
    if (podcast != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(miniShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (isReordering) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = { onClick(podcast.id) },
                            onLongClick = onLongClick,
                        )
                    }
                ),
        ) {
            OptimizedImage(
                url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
                fallbackUrls = podcast.artworkUrls(),
                proxyWidth = 140,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (hasNewEpisode) {
                MiniNewEpisodeBadge()
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(miniShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (isReordering) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = onEmptyClick,
                            onClickLabel = "Open folder",
                            onLongClick = onLongClick,
                        )
                    }
                ),
        )
    }
}

@Composable
private fun BoxScope.MiniNewEpisodeBadge(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    val brush = rememberNewEpisodeBadgeShimmerBrush()
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(top = 2.dp, end = 2.dp)
            .background(brush, shape),
    ) {
        Text(
            text = "NEW",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 6.sp,
                fontWeight = GoogleSansWeight.extraBold,
                letterSpacing = 0.3.sp,
                lineHeight = 7.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 2.5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun BoxScope.FolderFloatingBadge(
    modifier: Modifier = Modifier,
) {
    NewEpisodeBadge(
        modifier = modifier
            .offset(x = 8.dp, y = (-8).dp)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(6.dp))
            .zIndex(10f),
    )
}

/**
 * Truncates a compact 1×1 folder display name if it exceeds [maxLength] characters, appending an ellipsis.
 */
internal fun truncateCompactFolderName(name: String, maxLength: Int = 10): String =
    if (name.length > maxLength) {
        "${name.take(maxLength)}…"
    } else {
        name
    }
