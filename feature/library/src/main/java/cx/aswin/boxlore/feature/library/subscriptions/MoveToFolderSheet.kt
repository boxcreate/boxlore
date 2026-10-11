package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder

/**
 * Modal bottom sheet displaying available subscription folders to move or add a [podcast].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToFolderSheet(
    podcast: Podcast,
    availableFolders: List<SubscriptionFolder>,
    onSelectFolder: (folderId: String) -> Unit,
    onCreateNewFolder: (Podcast) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    currentFolderId: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val candidateFolders = remember(availableFolders, currentFolderId) {
        if (currentFolderId == null) {
            availableFolders
        } else {
            availableFolders.filter { it.id != currentFolderId }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            MoveToFolderHeader(
                podcast = podcast,
                isMoving = currentFolderId != null,
                onDismissRequest = onDismissRequest,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 6.dp),
            )

            MoveToFolderList(
                candidateFolders = candidateFolders,
                onSelectFolder = onSelectFolder,
                onDismissRequest = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
            )

            CreateNewFolderActionRow(
                podcast = podcast,
                onCreateNewFolder = onCreateNewFolder,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun MoveToFolderHeader(
    podcast: Podcast,
    isMoving: Boolean,
    onDismissRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(44.dp),
        ) {
            if (podcast.imageUrl.isNotBlank()) {
                OptimizedImage(
            proxyWidth = 140,
            url = podcast.imageUrl,
                    fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
                    contentDescription = podcast.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMoving) "Move to Folder" else "Add to Folder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun MoveToFolderList(
    candidateFolders: List<SubscriptionFolder>,
    onSelectFolder: (folderId: String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidateFolders.isEmpty()) {
        Box(
            modifier = modifier.padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No other folders yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.heightIn(max = 380.dp),
        ) {
            items(candidateFolders, key = { it.id }) { folder ->
                FolderPickRow(
                    folder = folder,
                    onClick = {
                        onDismissRequest()
                        onSelectFolder(folder.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateNewFolderActionRow(
    podcast: Podcast,
    onCreateNewFolder: (Podcast) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        FilledTonalButton(
            onClick = {
                onDismissRequest()
                onCreateNewFolder(podcast)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create new folder")
        }
    }
}

@Composable
private fun FolderPickRow(
    folder: SubscriptionFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderIcon = GenreIcons.folderIconOrFallback(folder.icon)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = folderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${folder.podcastCount} shows • ${folder.displaySize.dimensionsLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
