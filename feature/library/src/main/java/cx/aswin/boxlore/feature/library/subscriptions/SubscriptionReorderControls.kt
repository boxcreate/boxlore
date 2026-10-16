package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * State representing which section is currently in reordering mode.
 */
sealed interface ReorderMode {
    data object Inactive : ReorderMode
    data object Folders : ReorderMode
    data class FolderShows(val folderId: String, val folderName: String = "") : ReorderMode
    data object RootShows : ReorderMode
}

internal data class ReorderBarContent(
    val title: String,
    val subtitle: String,
    val note: String,
    val icon: ImageVector,
)

private const val DRAG_TO_ARRANGE = "Drag to arrange"
private const val SWITCHES_TO_MANUAL_SORT = "Switches to manual sort"

internal fun resolveReorderBarContent(reorderMode: ReorderMode): ReorderBarContent = when (reorderMode) {
    ReorderMode.Folders -> ReorderBarContent(
        title = "Reordering: Folders",
        subtitle = DRAG_TO_ARRANGE,
        note = SWITCHES_TO_MANUAL_SORT,
        icon = Icons.Rounded.Folder,
    )
    is ReorderMode.FolderShows -> {
        val name = reorderMode.folderName.takeIf { it.isNotBlank() } ?: "Folder"
        ReorderBarContent(
            title = "Reordering: $name",
            subtitle = DRAG_TO_ARRANGE,
            note = SWITCHES_TO_MANUAL_SORT,
            icon = Icons.Rounded.FolderOpen,
        )
    }
    ReorderMode.RootShows -> ReorderBarContent(
        title = "Reordering: Shows",
        subtitle = DRAG_TO_ARRANGE,
        note = SWITCHES_TO_MANUAL_SORT,
        icon = Icons.Rounded.Subscriptions,
    )
    ReorderMode.Inactive -> ReorderBarContent(
        title = "",
        subtitle = "",
        note = "",
        icon = Icons.Rounded.Reorder,
    )
}

/**
 * Floating bar displayed during active [ReorderMode], providing a notice of what is being
 * reordered and that sort will switch to manual, along with floating Save and Cancel action buttons.
 */
@Composable
internal fun SubscriptionReorderBar(
    reorderMode: ReorderMode,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = reorderMode != ReorderMode.Inactive,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val content = remember(reorderMode) { resolveReorderBarContent(reorderMode) }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = content.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = content.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (content.subtitle.isNotBlank()) {
                            Text(
                                text = content.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (content.note.isNotBlank()) {
                            Text(
                                text = content.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                ReorderBarActions(
                    onSave = onSave,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun ReorderBarActions(
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(
            onClick = onCancel,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cancel reordering",
                modifier = Modifier.size(20.dp),
            )
        }

        FilledIconButton(
            onClick = onSave,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Save new order",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal const val ShowsGenreHeaderKey = "shows_genre_header"
internal val ShowsBlockedReorderKeys = setOf(ShowsGenreHeaderKey)

internal fun isFolderKey(key: String): Boolean =
    key.startsWith("folder_") ||
        key.startsWith("compact_folder_") ||
        key.startsWith("pinned_folder_") ||
        key.startsWith("list_folder_")

internal fun extractFolderId(key: String): String? {
    val id = key.removePrefix("folder_")
        .removePrefix("compact_folder_")
        .removePrefix("pinned_folder_")
        .removePrefix("list_folder_")
    return if (id != key) id else null
}

internal fun isReorderablePodcastKey(key: String): Boolean =
    !isFolderKey(key) && key != ShowsGenreHeaderKey

@Composable
internal fun rememberShowsOrderedKeys(
    unfiledPodcasts: List<cx.aswin.boxlore.core.model.Podcast>,
    manualOrder: List<String>,
    isManualSort: Boolean,
    isRootReordering: Boolean = false,
): androidx.compose.runtime.MutableState<List<String>> {
    val incomingKeys = androidx.compose.runtime.remember(unfiledPodcasts, manualOrder, isManualSort, isRootReordering) {
        if ((isManualSort || isRootReordering) && manualOrder.isNotEmpty()) {
            val podIds = unfiledPodcasts.map { it.id }.toSet()
            val ordered = manualOrder.filter { it in podIds }
            val remainder = unfiledPodcasts.map { it.id }.filter { it !in manualOrder }
            (ordered + remainder).distinct()
        } else {
            unfiledPodcasts.map { it.id }.distinct()
        }
    }
    val orderedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(incomingKeys) }
    androidx.compose.runtime.LaunchedEffect(incomingKeys) {
        orderedState.value = incomingKeys
    }
    return orderedState
}

internal fun resolveOrderedPodcasts(
    unfiledPodcasts: List<cx.aswin.boxlore.core.model.Podcast>,
    orderedKeys: List<String>,
    isManualSort: Boolean,
    isRootReordering: Boolean = false,
): List<cx.aswin.boxlore.core.model.Podcast> {
    // The grid and list key rows by podcast id, so a repeated id in the saved order or the
    // subscription rows must never reach them.
    if (!isManualSort && !isRootReordering) return unfiledPodcasts.distinctBy { it.id }
    val unfiledPodcastsById = unfiledPodcasts.associateBy { it.id }
    val mapped = orderedKeys.mapNotNull(unfiledPodcastsById::get)
    val missing = unfiledPodcasts.filter { it.id !in orderedKeys }
    return (mapped + missing).distinctBy { it.id }
}

@Composable
internal fun rememberShowsMoveHandler(
    isFoldersReordering: Boolean,
    isRootReordering: Boolean,
    orderedFolderKeys: List<String>,
    onFolderKeysChange: (List<String>) -> Unit,
    orderedKeys: List<String>,
    onKeysChange: (List<String>) -> Unit,
    actions: ShowsTabActions,
): (String, String) -> Unit = androidx.compose.runtime.remember(
    isFoldersReordering,
    isRootReordering,
    orderedFolderKeys,
    orderedKeys,
    actions,
) {
    { fromKey, toKey ->
        if (isFoldersReordering) {
            val fromFolderId = extractFolderId(fromKey)
            val toFolderId = extractFolderId(toKey)
            if (fromFolderId != null && toFolderId != null) {
                val moved = cx.aswin.boxlore.feature.library.logic.SubscriptionManualOrderLogic.move(
                    orderedFolderKeys,
                    fromFolderId,
                    toFolderId,
                )
                onFolderKeysChange(moved)
                actions.onReorderFolders(moved)
            }
        } else if (isRootReordering && isReorderablePodcastKey(fromKey) && isReorderablePodcastKey(toKey)) {
            val moved = cx.aswin.boxlore.feature.library.logic.SubscriptionManualOrderLogic.moveVisible(
                ids = orderedKeys,
                fromId = fromKey,
                toId = toKey,
                blockedKeys = ShowsBlockedReorderKeys,
            )
            if (moved != null) {
                onKeysChange(moved)
                actions.onReorder(moved)
            }
        }
    }
}

@Composable
internal fun rememberShowsFolderItems(
    partition: PartitionedSubscriptionItems,
    orderedFolderKeys: List<String>,
    isFoldersReordering: Boolean,
    folderSort: FolderInterSort,
): ShowsFolderItems = androidx.compose.runtime.remember(
    partition,
    orderedFolderKeys,
    isFoldersReordering,
    folderSort,
) {
    if (isFoldersReordering || folderSort == FolderInterSort.Manual) {
        val folderMap = partition.folders.associateBy { it.id }
        val reorderedFolders = orderedFolderKeys.mapNotNull { folderMap[it] }
        val missingFolders = partition.folders.filter { it.id !in orderedFolderKeys }
        ShowsFolderItems(
            folders = reorderedFolders + missingFolders,
            podcastsByFolderId = partition.podcastsByFolderId,
        )
    } else {
        ShowsFolderItems(
            folders = partition.folders,
            podcastsByFolderId = partition.podcastsByFolderId,
        )
    }
}
