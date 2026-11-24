package cx.aswin.boxlore.feature.library.subscriptions

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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.library.R

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
    /** Pins the show to Home, or unpins it. */
    val onTogglePin: (Podcast) -> Unit = {},
)

/** The actions a long-press on a show offers, in order; the same for every show apart from Remove from folder. */
internal enum class ShowContextAction { TogglePin, AddOrMoveToFolder, RemoveFromFolder, Reorder, Unsubscribe }

/** Every show gets Pin or Unpin, Add to or Move to folder, Reorder and, last, Unsubscribe; a show in a folder can also leave it. */
internal fun showContextActions(inFolder: Boolean): List<ShowContextAction> = buildList {
    add(ShowContextAction.TogglePin)
    add(ShowContextAction.AddOrMoveToFolder)
    if (inFolder) add(ShowContextAction.RemoveFromFolder)
    add(ShowContextAction.Reorder)
    add(ShowContextAction.Unsubscribe)
}

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
    pinnedPodcastIds: Set<String> = emptySet(),
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
                    PodcastContextMenuHeader(podcast = target.podcast, folderName = target.folder.name)
                    ShowContextActions(target.podcast, target.folder, target.podcast.id in pinnedPodcastIds, actions, onDismissRequest)
                }

                is ContextMenuTarget.RootPodcast -> {
                    PodcastContextMenuHeader(podcast = target.podcast)
                    ShowContextActions(target.podcast, null, target.podcast.id in pinnedPodcastIds, actions, onDismissRequest)
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
    MenuDivider()
    Spacer(modifier = Modifier.height(8.dp))

    ContextMenuActionRow(
        icon = Icons.Rounded.Edit,
        label = stringResource(R.string.library_folder_edit),
        onClick = {
            onDismissRequest()
            actions.onEditFolder(folder)
        },
    )
    ContextMenuActionRow(
        icon = Icons.Rounded.AddCircleOutline,
        label = stringResource(R.string.library_add_shows),
        onClick = {
            onDismissRequest()
            actions.onAddShowsToFolder(folder)
        },
    )
    ContextMenuActionRow(
        icon = Icons.Rounded.Reorder,
        label = stringResource(R.string.library_reorder_folders),
        onClick = {
            onDismissRequest()
            actions.onReorderFolders()
        },
    )

    Spacer(modifier = Modifier.height(4.dp))
    MenuDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

    ContextMenuActionRow(
        icon = Icons.Rounded.DeleteOutline,
        label = stringResource(R.string.library_folder_delete),
        isDestructive = true,
        onClick = {
            onDismissRequest()
            actions.onDeleteFolder(folder)
        },
    )
}

/** A show's actions ([showContextActions]): [folder] is the folder it was long-pressed in, or null. */
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per action, each with its pinned or in-folder wording.
private fun ShowContextActions(
    podcast: Podcast,
    folder: SubscriptionFolder?,
    pinned: Boolean,
    actions: SubscriptionContextMenuActions,
    onDismissRequest: () -> Unit,
) {
    MenuDivider()
    Spacer(modifier = Modifier.height(8.dp))
    showContextActions(inFolder = folder != null).forEach { action ->
        val run: (() -> Unit) -> () -> Unit = { block ->
            {
            onDismissRequest()
            block()
        }
        }
        when (action) {
            ShowContextAction.TogglePin -> ContextMenuActionRow(
                icon = if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                label = stringResource(if (pinned) R.string.library_unpin_home else R.string.library_pin_home),
                onClick = run { actions.onTogglePin(podcast) },
            )
            ShowContextAction.AddOrMoveToFolder -> ContextMenuActionRow(
                icon = if (folder == null) Icons.Rounded.CreateNewFolder else Icons.AutoMirrored.Rounded.DriveFileMove,
                label = stringResource(if (folder == null) R.string.library_folder_add_to else R.string.library_folder_move_to_another),
                onClick = run { actions.onMoveToAnotherFolder(folder, podcast) },
            )
            ShowContextAction.RemoveFromFolder -> ContextMenuActionRow(
                icon = Icons.Rounded.FolderOff,
                label = stringResource(R.string.library_folder_remove_from),
                onClick = run { folder?.let { actions.onRemoveFromFolder(it, podcast) } },
            )
            ShowContextAction.Reorder -> ContextMenuActionRow(
                icon = Icons.Rounded.Reorder,
                label = stringResource(if (folder == null) R.string.library_reorder_shows else R.string.library_reorder_shows_in_folder),
                onClick = run { if (folder == null) actions.onReorderRootShows() else actions.onReorderFolderShows(folder) },
            )
            ShowContextAction.Unsubscribe -> {
                Spacer(modifier = Modifier.height(4.dp))
                MenuDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                // Still asks first.
                ContextMenuActionRow(
                    icon = Icons.Rounded.RemoveCircleOutline,
                    label = stringResource(R.string.library_unsubscribe),
                    isDestructive = true,
                    onClick = run { actions.onUnsubscribePodcast(podcast) },
                )
            }
        }
    }
}

/** A solid hairline between the header and the actions, and before the red action. */
@Composable
private fun MenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = modifier)
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
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = folderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                text = pluralStringResource(R.plurals.library_folder_shows_and_dimensions, folder.podcastCount, folder.podcastCount, folder.displaySize.dimensionsLabel),
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
                stringResource(R.string.library_show_artist_in_folder, podcast.artist, folderName)
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
            .expressiveClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
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
