package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.artworkUrls

/**
 * Identifies the target item that was long-pressed in the Subscriptions tab.
 */
sealed interface ContextMenuTarget {
    data class Folder(val folder: SubscriptionFolder) : ContextMenuTarget
    data class FolderPodcast(val folder: SubscriptionFolder, val podcast: Podcast) : ContextMenuTarget
    data class RootPodcast(val podcast: Podcast) : ContextMenuTarget
}

/**
 * Action callbacks for [SubscriptionContextMenuSheet].
 */
internal data class SubscriptionContextMenuActions(
    val onEditFolder: (SubscriptionFolder) -> Unit = {},
    val onAddShowsToFolder: (SubscriptionFolder) -> Unit = {},
    val onReorderFolders: () -> Unit = {},
    val onDeleteFolder: (SubscriptionFolder) -> Unit = {},
    val onRemoveFromFolder: (SubscriptionFolder, Podcast) -> Unit = { _, _ -> },
    val onMoveToAnotherFolder: (SubscriptionFolder?, Podcast) -> Unit = { _, _ -> },
    val onReorderFolderShows: (SubscriptionFolder) -> Unit = {},
    val onUnsubscribePodcast: (Podcast) -> Unit = {},
    val onReorderRootShows: () -> Unit = {},
)

/**
 * Modal bottom sheet displaying contextual options when long-pressing folders,
 * member shows inside folders, or unfiled root shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionContextMenuSheet(
    target: ContextMenuTarget,
    actions: SubscriptionContextMenuActions,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("subscription_context_menu")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth().tabletSheetScroll()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            when (target) {
                is ContextMenuTarget.Folder -> {
                    FolderContextActions(
                        folder = target.folder,
                        actions = actions,
                        onDismissRequest = onDismissRequest,
                    )
                }

                is ContextMenuTarget.FolderPodcast -> {
                    FolderPodcastContextActions(
                        target = target,
                        actions = actions,
                        onDismissRequest = onDismissRequest,
                    )
                }

                is ContextMenuTarget.RootPodcast -> {
                    RootPodcastContextActions(
                        podcast = target.podcast,
                        actions = actions,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderContextActions(
    folder: SubscriptionFolder,
    actions: SubscriptionContextMenuActions,
    onDismissRequest: () -> Unit,
) {
    FolderContextMenuHeader(folder = folder)
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp,
    )
    Spacer(modifier = Modifier.height(8.dp))

    ContextMenuActionRow(
        icon = Icons.Rounded.Edit,
        label = "Edit folder",
        onClick = {
            onDismissRequest()
            actions.onEditFolder(folder)
        },
    )
    ContextMenuActionRow(
        icon = Icons.Rounded.AddCircleOutline,
        label = "Add shows",
        onClick = {
            onDismissRequest()
            actions.onAddShowsToFolder(folder)
        },
    )
    ContextMenuActionRow(
        icon = Icons.Rounded.Reorder,
        label = "Reorder folders",
        onClick = {
            onDismissRequest()
            actions.onReorderFolders()
        },
    )

    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    ContextMenuActionRow(
        icon = Icons.Rounded.DeleteOutline,
        label = "Delete folder",
        isDestructive = true,
        onClick = {
            onDismissRequest()
            actions.onDeleteFolder(folder)
        },
    )
}

@Composable
private fun FolderPodcastContextActions(
    target: ContextMenuTarget.FolderPodcast,
    actions: SubscriptionContextMenuActions,
    onDismissRequest: () -> Unit,
) {
    PodcastContextMenuHeader(
        podcast = target.podcast,
        folderName = target.folder.name,
    )
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp,
    )
    Spacer(modifier = Modifier.height(8.dp))

    ContextMenuActionRow(
        icon = Icons.Rounded.FolderOff,
        label = "Remove from folder",
        onClick = {
            onDismissRequest()
            actions.onRemoveFromFolder(target.folder, target.podcast)
        },
    )
    ContextMenuActionRow(
        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
        label = "Move to another folder",
        onClick = {
            onDismissRequest()
            actions.onMoveToAnotherFolder(target.folder, target.podcast)
        },
    )
    ContextMenuActionRow(
        icon = Icons.Rounded.Reorder,
        label = "Reorder shows in folder",
        onClick = {
            onDismissRequest()
            actions.onReorderFolderShows(target.folder)
        },
    )

    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    ContextMenuActionRow(
        icon = Icons.Rounded.RemoveCircleOutline,
        label = "Unsubscribe",
        isDestructive = true,
        onClick = {
            onDismissRequest()
            actions.onUnsubscribePodcast(target.podcast)
        },
    )
}

@Composable
private fun RootPodcastContextActions(
    podcast: Podcast,
    actions: SubscriptionContextMenuActions,
    onDismissRequest: () -> Unit,
) {
    PodcastContextMenuHeader(podcast = podcast)
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp,
    )
    Spacer(modifier = Modifier.height(8.dp))

    ContextMenuActionRow(
        icon = Icons.Rounded.CreateNewFolder,
        label = "Add to folder",
        onClick = {
            onDismissRequest()
            actions.onMoveToAnotherFolder(null, podcast)
        },
    )
    ContextMenuActionRow(
        icon = Icons.Rounded.Reorder,
        label = "Reorder shows",
        onClick = {
            onDismissRequest()
            actions.onReorderRootShows()
        },
    )
}

@Composable
private fun FolderContextMenuHeader(
    folder: SubscriptionFolder,
    modifier: Modifier = Modifier,
) {
    val folderIcon = GenreIcons.folderIconOrFallback(folder.icon)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = folderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${folder.podcastCount} ${if (folder.podcastCount == 1) "show" else "shows"} • ${folder.displaySize.dimensionsLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PodcastContextMenuHeader(
    podcast: Podcast,
    folderName: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(48.dp),
        ) {
            if (podcast.artworkUrls().isNotEmpty()) {
                OptimizedImage(
            proxyWidth = 140,
            url = podcast.imageUrl,
                    fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
                    contentDescription = podcast.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (folderName != null) {
                "${podcast.artist} • In $folderName"
            } else {
                podcast.artist
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val tint = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isDestructive) FontWeight.Medium else FontWeight.Normal,
            color = tint,
        )
    }
}
