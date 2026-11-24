package cx.aswin.boxlore.feature.library.subscriptions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState

/** Folder cards on the Shows grid, with the "Folders" / "Your shows" labels when they fit. */
internal fun LazyGridScope.folderGridItems(
    folderItems: ShowsFolderItems,
    gridConfig: ShowsGridConfig,
    reorderableGridState: ReorderableLazyGridState,
    folderActions: FolderCardActions,
    labels: ShowsSectionLabels?,
) {
    if (labels == null) {
        folderCardItems(folderItems.folders, folderItems, gridConfig, reorderableGridState, folderActions)
        return
    }
    showsSectionLabel(key = ShowsFoldersLabelKey, text = R.string.library_folders)
    folderCardItems(folderItems.folders.take(labels.yourShowsBeforeFolderIndex), folderItems, gridConfig, reorderableGridState, folderActions)
    showsSectionLabel(key = ShowsYourShowsLabelKey, text = R.string.library_your_shows)
    folderCardItems(folderItems.folders.drop(labels.yourShowsBeforeFolderIndex), folderItems, gridConfig, reorderableGridState, folderActions)
}

private const val ShowsFoldersLabelKey = "section_label_folders"
private const val ShowsYourShowsLabelKey = "section_label_your_shows"

private fun LazyGridScope.showsSectionLabel(key: String, @StringRes text: Int) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        // A clear section title, stronger than the folder names under it.
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).testTag(SHOWS_SECTION_LABEL_TAG),
        )
    }
}

private fun LazyGridScope.folderCardItems(
    folders: List<SubscriptionFolder>,
    folderItems: ShowsFolderItems,
    gridConfig: ShowsGridConfig,
    reorderableGridState: ReorderableLazyGridState,
    folderActions: FolderCardActions,
) {
    items(
        items = folders,
        key = { "folder_${it.id}" },
        span = { folder ->
            if (folder.displaySize == FolderDisplaySize.COMPACT) {
                GridItemSpan(1)
            } else {
                GridItemSpan(maxLineSpan)
            }
        },
    ) { folder ->
        val folderShows = folderItems.podcastsByFolderId[folder.id].orEmpty()
        ReorderableItem(
            reorderableGridState,
            key = "folder_${folder.id}",
            enabled = gridConfig.isFoldersReordering,
        ) { isDragging ->
            val dragModifier = if (gridConfig.isFoldersReordering) {
                Modifier.longPressDraggableHandle()
            } else {
                Modifier
            }
            if (folder.displaySize == FolderDisplaySize.COMPACT) {
                Compact1x1FolderCard(
                    folder = folder,
                    podcasts = folderShows,
                    actions = folderActions,
                    isDragging = isDragging,
                    dragModifier = dragModifier,
                    isReordering = gridConfig.isFoldersReordering,
                )
            } else {
                PinnedEnlargedFolderCard(
                    folder = folder,
                    podcasts = folderShows,
                    actions = folderActions,
                    isDragging = isDragging,
                    dragModifier = dragModifier,
                    isReordering = gridConfig.isFoldersReordering,
                )
            }
        }
    }
}

/** The Folders and Your shows titles in the Shows grid, for tests. */
internal const val SHOWS_SECTION_LABEL_TAG = "shows_section_label"
