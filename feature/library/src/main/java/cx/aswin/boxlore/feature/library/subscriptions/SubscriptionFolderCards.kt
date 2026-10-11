package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.rememberNewEpisodeBadgeShimmerBrush
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes

/**
 * Callbacks for interacting with folder cards in the Subscriptions grid.
 */
internal data class FolderCardActions(
    val onPodcastClick: (String) -> Unit,
    val onFolderClick: (String) -> Unit,
    val onFolderLongClick: (SubscriptionFolder) -> Unit = {},
)

/**
 * Pinned enlarged folder card spanning full grid width (SHELF 3×1, PANEL 3×2, SHOWCASE 3×3).
 * Shows inside the card are directly clickable to open podcast details.
 * The overflow slot or header opens the full folder view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PinnedEnlargedFolderCard(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    actions: FolderCardActions,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val lastSeenEpisodes = LocalLastSeenEpisodes.current
    val shape = RoundedCornerShape(16.dp)
    val folderIcon = GenreIcons.folderIconOrFallback(folder.icon)
    val slots = calculateFolderSlots(podcasts, folder.displaySize)

    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        label = "pinnedFolderDragScale",
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "pinnedFolderDragElevation",
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = dragElevation,
        modifier = modifier
            .then(dragModifier)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                this.shape = shape
                clip = false
            }
            .then(
                if (isReordering) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClick = { actions.onFolderClick(folder.id) },
                        onLongClick = { actions.onFolderLongClick(folder) },
                    )
                }
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PinnedFolderHeader(
                folder = folder,
                podcastsCount = podcasts.size,
                folderIcon = folderIcon,
                onFolderClick = actions.onFolderClick,
                onFolderLongClick = actions.onFolderLongClick,
                isReordering = isReordering,
            )

            // Grid of directly clickable covers
            if (podcasts.isEmpty()) {
                EmptyFolderPlaceholder(
                    onAddShowsClick = { if (!isReordering) actions.onFolderClick(folder.id) },
                )
            } else {
                FolderCoversGrid(
                    slots = slots,
                    folder = folder,
                    actions = actions,
                    lastSeenEpisodes = lastSeenEpisodes,
                    isReordering = isReordering,
                )
            }
        }
    }
}

/**
 * Header row for pinned enlarged folders with title, icon, and show count.
 */
@Composable
private fun PinnedFolderHeader(
    folder: SubscriptionFolder,
    podcastsCount: Int,
    folderIcon: ImageVector,
    onFolderClick: (String) -> Unit,
    onFolderLongClick: (SubscriptionFolder) -> Unit,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = folderIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = if (podcastsCount == 1) "1 show" else "$podcastsCount shows",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Open folder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Grid of directly clickable covers inside an enlarged folder card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCoversGrid(
    slots: FolderSlots,
    folder: SubscriptionFolder,
    actions: FolderCardActions,
    lastSeenEpisodes: Map<String, String>,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val columns = folder.displaySize.spanCols
    val totalItems = slots.visibleShows.size + if (slots.hasOverflow) 1 else 0
    val rows = (totalItems + columns - 1) / columns

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (colIndex in 0 until columns) {
                    val itemIndex = rowIndex * columns + colIndex
                    FolderCoverSlot(
                        itemIndex = itemIndex,
                        slots = slots,
                        folder = folder,
                        actions = actions,
                        lastSeenEpisodes = lastSeenEpisodes,
                        isReordering = isReordering,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.FolderCoverSlot(
    itemIndex: Int,
    slots: FolderSlots,
    folder: SubscriptionFolder,
    actions: FolderCardActions,
    lastSeenEpisodes: Map<String, String>,
    isReordering: Boolean,
) {
    if (itemIndex < slots.visibleShows.size) {
        val podcast = slots.visibleShows[itemIndex]
        val isNew = remember(podcast, lastSeenEpisodes) {
            podcast.isLatestEpisodeNew(lastSeenEpisodes[podcast.id])
        }
        DirectClickableShowCover(
            podcast = podcast,
            hasNewEpisode = isNew,
            onClick = { actions.onPodcastClick(podcast.id) },
            onLongClick = { actions.onFolderLongClick(folder) },
            modifier = Modifier.weight(1f),
            isReordering = isReordering,
        )
    } else if (itemIndex == slots.visibleShows.size && slots.hasOverflow) {
        val overflowNewCount = remember(slots.overflowShows, lastSeenEpisodes) {
            countFolderOverflowNew(slots.overflowShows, lastSeenEpisodes)
        }
        FolderOverflowSlotCard(
            overflowShows = slots.overflowShows,
            overflowCount = slots.overflowCount,
            newEpisodesCount = overflowNewCount,
            onClick = { actions.onFolderClick(folder.id) },
            onLongClick = { actions.onFolderLongClick(folder) },
            modifier = Modifier.weight(1f),
            isReordering = isReordering,
        )
    } else {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .then(
                    if (isReordering) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = { actions.onFolderClick(folder.id) },
                            onClickLabel = "Open folder",
                            onLongClick = { actions.onFolderLongClick(folder) },
                        )
                    },
                ),
        )
    }
}

/**
 * Single directly clickable podcast cover within a folder card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectClickableShowCover(
    podcast: Podcast,
    hasNewEpisode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .then(
                if (isReordering) {
                    Modifier
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            ),
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 240,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (hasNewEpisode) {
            NewEpisodeBadge()
        }
    }
}

/**
 * Renders the overflow slot with a 2×2 mini collage, dark scrim, bold `+N` count,
 * and an optional "N new episodes" chip below `+N` when unplayed new episodes exist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderOverflowSlotCard(
    overflowShows: List<Podcast>,
    overflowCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    newEpisodesCount: Int = 0,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (newEpisodesCount > 0) {
                    Modifier.border(
                        BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        shape,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (isReordering) {
                    Modifier
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 2×2 mini collage preview of overflow shows
        val previewShows = overflowShows.take(4)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                previewShows.getOrNull(0)?.let { MiniCover(it, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
                previewShows.getOrNull(1)?.let { MiniCover(it, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                previewShows.getOrNull(2)?.let { MiniCover(it, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
                previewShows.getOrNull(3)?.let { MiniCover(it, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
            }
        }

        // Dark scrim + bold count and new episodes chip
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    text = "+$overflowCount",
                    style = if (newEpisodesCount > 0) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = GoogleSansWeight.bold,
                    color = Color.White,
                )
                if (newEpisodesCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OverflowNewEpisodesChip(newCount = newEpisodesCount)
                }
            }
        }
    }
}

@Composable
private fun OverflowNewEpisodesChip(
    newCount: Int,
    modifier: Modifier = Modifier,
) {
    val text = if (newCount == 1) "1 NEW EPISODE" else "$newCount NEW EPISODES"
    val shape = RoundedCornerShape(100.dp)
    val brush = rememberNewEpisodeBadgeShimmerBrush()
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        modifier = modifier.background(brush, shape),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.5.sp,
                fontWeight = GoogleSansWeight.bold,
                letterSpacing = 0.3.sp,
                lineHeight = 9.sp,
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MiniCover(podcast: Podcast, modifier: Modifier = Modifier) {
    OptimizedImage(
        url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
        fallbackUrls = podcast.artworkUrls(),
        proxyWidth = 100,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyFolderPlaceholder(
    onAddShowsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
            .clickable(onClick = onAddShowsClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Folder is empty • Tap to add shows",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = GoogleSansWeight.medium,
            )
        }
    }
}
