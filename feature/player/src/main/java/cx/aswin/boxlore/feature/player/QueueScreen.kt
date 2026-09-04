package cx.aswin.boxlore.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.SameShowContinuationState
import cx.aswin.boxlore.feature.player.v2.logic.queueSourceLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class QueueSheetActions(
    val onPlayEpisode: (Episode) -> Unit,
    val onRemoveEpisode: (Episode) -> Unit,
    val onClose: () -> Unit,
    val onMove: (fromUiIndex: Int, toUiIndex: Int) -> Unit = { _, _ -> },
    val onDragEnd: (episodeId: String, fromUiIndex: Int, toUiIndex: Int) -> Unit = { _, _, _ -> },
    val onEnableSmartQueue: () -> Unit = {},
    val onAddSameShowEpisodes: () -> Unit = {},
    val onDismissSameShowBanner: () -> Unit = {},
    val onEpisodeInfoClick: (Episode) -> Unit = {},
)

data class QueueItemDisplay(
    val episode: Episode,
    val podcast: Podcast?,
    val sourceLabel: String? = null,
    val isDragging: Boolean = false,
    val dragHandleModifier: Modifier? = null
)

/**
 * Queue bottom sheet content: header with close button + drag-to-reorder queue list.
 *
 * Indices in [actions.onMove]/[actions.onDragEnd] are UI list indices — the currently playing episode
 * is hidden from this sheet, so callers must map them with QueueMath.uiIndexToQueueIndex.
 */
@Composable
fun QueueSheetContent(
    queue: List<Episode>,
    currentPodcast: Podcast?,
    colorScheme: ColorScheme,
    actions: QueueSheetActions,
    modifier: Modifier = Modifier,
    smartQueueEnabled: Boolean = true,
    sameShowContinuation: SameShowContinuationState = SameShowContinuationState.HIDDEN,
) {
    val lazyListState = rememberLazyListState()
    val dragStartIndex = remember { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header: "Up Next" + Close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${queue.size} episodes",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = actions.onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close queue",
                    tint = colorScheme.onSurface
                )
            }
        }

        HorizontalDivider(
            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (sameShowContinuation.visible && sameShowContinuation.availableCount > 0) {
            SameShowContinuationBanner(
                state = sameShowContinuation,
                onAddEpisodes = actions.onAddSameShowEpisodes,
                onDismiss = actions.onDismissSameShowBanner,
                colorScheme = colorScheme,
                onEpisodeClick = actions.onEpisodeInfoClick,
            )
        }

        if (queue.isEmpty()) {
            QueueEmptyState(
                smartQueueEnabled = smartQueueEnabled,
                colorScheme = colorScheme,
                onEnableSmartQueue = actions.onEnableSmartQueue,
            )
        } else {
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                actions.onMove(from.index, to.index)
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { _, episode -> episode.id }
                ) { index, episode ->
                    ReorderableItem(reorderableState, key = episode.id) { isDragging ->
                        QueueItemRow(
                            display = QueueItemDisplay(
                                episode = episode,
                                podcast = currentPodcast,
                                sourceLabel = queueSourceLabel(episode),
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { dragStartIndex.intValue = index },
                                    onDragStopped = {
                                        val from = dragStartIndex.intValue
                                        dragStartIndex.intValue = -1
                                        val to = queue.indexOfFirst { it.id == episode.id }
                                        if (from != -1 && to != -1) actions.onDragEnd(episode.id, from, to)
                                    }
                                )
                            ),
                            colorScheme = colorScheme,
                            onClick = { actions.onPlayEpisode(episode) },
                            onRemove = { actions.onRemoveEpisode(episode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEmptyState(
    smartQueueEnabled: Boolean,
    colorScheme: ColorScheme,
    onEnableSmartQueue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Queue is empty",
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )
        if (!smartQueueEnabled) {
            Text(
                text = "Smart queue is off, so nothing is added automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onEnableSmartQueue,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Turn on",
                    color = colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.medium,
                )
            }
        }
    }
}

@Composable
fun QueueItemRow(
    display: QueueItemDisplay,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val episode = display.episode
    val podcast = display.podcast
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (display.isDragging) {
                    colorScheme.surfaceVariant.copy(alpha = 0.6f)
                } else {
                    colorScheme.surface.copy(alpha = 0f)
                }
            )
            .expressiveClickable { onClick() }
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
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
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title.replace("+", " "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = GoogleSansWeight.medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = (episode.podcastTitle ?: podcast?.title ?: "Unknown Podcast").replace("+", " "),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            display.sourceLabel?.let { sourceLabel ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove from queue",
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        display.dragHandleModifier?.let { dragHandleModifier ->
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Reorder",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = dragHandleModifier
                    .size(40.dp)
                    .padding(8.dp)
            )
        }
    }
}

object SameShowContinuationBannerDefaults {
    fun buttonText(availableCount: Int): String = "Add next $availableCount from this show"

    fun titleText(podcastTitle: String): String = if (podcastTitle.isNotBlank()) "Continue $podcastTitle?" else "Continue this show?"

    const val EXPLANATION_TEXT =
        "We skipped newer episodes from this show as you played it from recommendations."

    fun previewToggleText(availableCount: Int, isExpanded: Boolean): String = if (isExpanded) {
        "Hide preview"
    } else if (availableCount == 1) {
        "Preview 1 upcoming episode"
    } else {
        "Preview $availableCount upcoming episodes"
    }

    fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        return if (hrs > 0) "${hrs}h ${mins}m" else "$mins min"
    }
}

@Composable
private fun SameShowContinuationBannerHeader(
    podcastTitle: String,
    colorScheme: ColorScheme,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = SameShowContinuationBannerDefaults.titleText(podcastTitle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = GoogleSansWeight.bold,
            color = colorScheme.onSurface,
            maxLines = 1,
            modifier =
            Modifier
                .weight(1f)
                .basicMarquee(),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SameShowContinuationPreviewList(
    episodes: List<Episode>,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onEpisodeClick: (Episode) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        episodes.forEach { episode ->
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        color = colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .expressiveClickable { onEpisodeClick(episode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model =
                    episode.imageUrl?.takeIf { it.isNotBlank() }
                        ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
                    contentDescription = null,
                    modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = episode.title.replace("+", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = GoogleSansWeight.medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val duration = SameShowContinuationBannerDefaults.formatDuration(episode.duration)
                if (duration.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SameShowContinuationAccordionToggle(
    availableCount: Int,
    isExpanded: Boolean,
    colorScheme: ColorScheme,
    onToggle: () -> Unit,
) {
    FilledTonalButton(
        onClick = onToggle,
        shape = CircleShape,
        colors =
        ButtonDefaults.filledTonalButtonColors(
            containerColor = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = SameShowContinuationBannerDefaults.previewToggleText(availableCount, isExpanded),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.semiBold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun SameShowContinuationBanner(
    state: SameShowContinuationState,
    onAddEpisodes: () -> Unit,
    onDismiss: () -> Unit,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onEpisodeClick: (Episode) -> Unit = {},
) {
    if (!state.visible || state.availableCount <= 0) return

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val buttonText = SameShowContinuationBannerDefaults.buttonText(state.availableCount)

    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            SameShowContinuationBannerHeader(
                podcastTitle = state.podcastTitle,
                colorScheme = colorScheme,
                onDismiss = onDismiss,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SameShowContinuationBannerDefaults.EXPLANATION_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            SameShowContinuationAccordionToggle(
                availableCount = state.availableCount,
                isExpanded = isExpanded,
                colorScheme = colorScheme,
                onToggle = { isExpanded = !isExpanded },
            )

            // Accordion preview content (full card width)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SameShowContinuationPreviewList(
                    episodes = state.nextEpisodes.take(state.availableCount),
                    colorScheme = colorScheme,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    onEpisodeClick = onEpisodeClick,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAddEpisodes,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        }
    }
}
