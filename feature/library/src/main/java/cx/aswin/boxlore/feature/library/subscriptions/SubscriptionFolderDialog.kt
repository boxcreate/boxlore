package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialog as Dialog
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.library.logic.SubscriptionManualOrderLogic
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * Callbacks and events for interactions within [SubscriptionFolderDialog].
 */
internal data class FolderDialogActions(
    val onDismissRequest: () -> Unit = {},
    val onPodcastClick: (String) -> Unit = {},
    val onPodcastLongClick: (Podcast) -> Unit = {},
    val onEditFolder: () -> Unit = {},
    val onSaveReorder: (List<String>) -> Unit = {},
    val onCancelReorder: () -> Unit = {},
)

/**
 * Centered floating dialog displaying all podcasts in a subscription folder with maximized viewing area.
 * Replaces modal bottom sheets and full-page navigation with a lightweight, contextual overlay.
 */
@Composable
internal fun SubscriptionFolderDialog(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    actions: FolderDialogActions,
    modifier: Modifier = Modifier,
    isReorderMode: Boolean = false,
) {
    Dialog(
        onDismissRequest = {
            if (isReorderMode) {
                actions.onCancelReorder()
            } else {
                actions.onDismissRequest()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = !isReorderMode,
        ),
    ) {
        val lastSeenEpisodes = LocalLastSeenEpisodes.current

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp)
                .heightIn(min = 200.dp, max = 700.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            ) {
                FolderDialogHeader(
                    folder = folder,
                    podcastsCount = podcasts.size,
                    onEditFolder = actions.onEditFolder,
                    onDismissRequest = {
                        if (isReorderMode) actions.onCancelReorder() else actions.onDismissRequest()
                    },
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.5.dp,
                )

                if (podcasts.isEmpty()) {
                    EmptyFolderDialogContent(
                        onEditFolder = actions.onEditFolder,
                    )
                } else {
                    FolderDialogGrid(
                        folder = folder,
                        podcasts = podcasts,
                        lastSeenEpisodes = lastSeenEpisodes,
                        isReorderMode = isReorderMode,
                        actions = actions,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderDialogHeader(
    folder: SubscriptionFolder,
    podcastsCount: Int,
    onEditFolder: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderIcon = GenreIcons.folderIconOrFallback(folder.icon)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = folderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (podcastsCount == 1) "1 show" else "$podcastsCount shows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onEditFolder) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onDismissRequest) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderDialogGrid(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    lastSeenEpisodes: Map<String, String>,
    isReorderMode: Boolean,
    actions: FolderDialogActions,
    modifier: Modifier = Modifier,
) {
    var localPodcasts by remember(podcasts, isReorderMode) { mutableStateOf(podcasts) }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
        val toId = to.key as? String ?: return@rememberReorderableLazyGridState
        val currentIds = localPodcasts.map { it.id }
        val newIds = SubscriptionManualOrderLogic.move(currentIds, fromId, toId)
        val podById = localPodcasts.associateBy { it.id }
        localPodcasts = newIds.mapNotNull(podById::get)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            items(localPodcasts, key = { it.id }) { podcast ->
                if (isReorderMode) {
                    ReorderableItem(
                        reorderableState,
                        key = podcast.id,
                        modifier = Modifier.animateItem(),
                    ) { isDragging ->
                        FolderDialogPodcastItem(
                            podcast = podcast,
                            lastSeenId = lastSeenEpisodes[podcast.id],
                            onClick = null,
                            onLongClick = null,
                            isDragging = isDragging,
                            dragModifier = Modifier.longPressDraggableHandle(),
                        )
                    }
                } else {
                    FolderDialogPodcastItem(
                        podcast = podcast,
                        lastSeenId = lastSeenEpisodes[podcast.id],
                        onClick = { actions.onPodcastClick(podcast.id) },
                        onLongClick = { actions.onPodcastLongClick(podcast) },
                    )
                }
            }
        }

        if (isReorderMode) {
            SubscriptionReorderBar(
                reorderMode = ReorderMode.FolderShows(folder.id, folder.name),
                onSave = { actions.onSaveReorder(localPodcasts.map { it.id }) },
                onCancel = actions.onCancelReorder,
            )
        }
    }
}

@Composable
private fun FolderDialogPodcastItem(
    podcast: Podcast,
    lastSeenId: String?,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(dragModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SubscriptionGridCard(
            podcast = podcast,
            lastSeenId = lastSeenId,
            onClick = onClick,
            onLongClick = onLongClick,
            isDragging = isDragging,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        )
    }
}

@Composable
private fun EmptyFolderDialogContent(
    onEditFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "No shows in this folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Edit folder to add shows or enable genre auto-sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(
            onClick = onEditFolder,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Edit folder")
        }
    }
}
