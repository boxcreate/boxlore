package cx.aswin.boxcast.feature.player.v2.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.player.v2.sheets.QueueSheetActions
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Queue overlay sheet for player V2 with staggered item entrance and tonal squircle rows.
 *
 * Indices in [actions.onMove]/[actions.onDragEnd] are UI list indices — the currently playing episode
 * is hidden from this sheet, so callers must map them with QueueMath.uiIndexToQueueIndex.
 */
@Composable
fun QueueSheetV2(
    queue: List<Episode>,
    currentPodcast: Podcast?,
    colorScheme: ColorScheme,
    actions: QueueSheetActions,
    modifier: Modifier = Modifier,
) {
    PlayerSheetScaffold(
        colorScheme = colorScheme,
        modifier = modifier,
    ) {
        val lazyListState = rememberLazyListState()
        val dragStartIndex = remember { mutableIntStateOf(-1) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${queue.size} episodes",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = actions.onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close queue",
                    tint = colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(
            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                actions.onMove(from.index, to.index)
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(queue, key = { _, episode -> episode.id }) { index, episode ->
                    ReorderableItem(reorderableState, key = episode.id) { isDragging ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(
                                animationSpec = tween(delayMillis = 50 * index),
                            ) + slideInVertically(
                                animationSpec = tween(delayMillis = 50 * index),
                                initialOffsetY = { it / 4 },
                            ),
                        ) {
                            QueueItemRowV2(
                                episode = episode,
                                podcast = currentPodcast,
                                sourceLabel = queueSourceLabel(episode),
                                isDragging = isDragging,
                                colorScheme = colorScheme,
                                onClick = { actions.onPlayEpisode(episode) },
                                onRemove = { actions.onRemoveEpisode(episode) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { dragStartIndex.intValue = index },
                                    onDragStopped = {
                                        val from = dragStartIndex.intValue
                                        dragStartIndex.intValue = -1
                                        val to = queue.indexOfFirst { it.id == episode.id }
                                        if (from != -1 && to != -1) {
                                            actions.onDragEnd(episode.id, from, to)
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRowV2(
    episode: Episode,
    podcast: Podcast?,
    sourceLabel: String?,
    isDragging: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val rowShape = playerSheetShape(
        topStart = PlayerChromeGeometry.QueueRowCorner,
        topEnd = PlayerChromeGeometry.QueueRowCorner,
        bottomStart = PlayerChromeGeometry.QueueRowCorner,
        bottomEnd = PlayerChromeGeometry.QueueRowCorner,
    )
    val backgroundColor = if (isDragging) {
        colorScheme.surfaceContainerHighest
    } else {
        colorScheme.surfaceContainerHigh
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(backgroundColor)
            .expressiveClickable { onClick() }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = episode.imageUrl?.takeIf { it.isNotBlank() }
                ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                ?: podcast?.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title.replace("+", " "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = (episode.podcastTitle ?: podcast?.title ?: "Unknown Podcast").replace("+", " "),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sourceLabel?.let { label ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove from queue",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }

        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = "Reorder",
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = dragHandleModifier
                .size(40.dp)
                .padding(8.dp),
        )
    }
}
