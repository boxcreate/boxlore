package cx.aswin.boxlore.feature.library.subscriptions

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchField
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.library.R

/**
 * Modal bottom sheet allowing users to add or remove subscribed shows from a [folder]
 * using interactive checkboxes with already-added shows pre-checked at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderShowsSelectionSheet(
    folder: SubscriptionFolder,
    allSubscribedPodcasts: List<Podcast>,
    onSave: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("folder_show_selection")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by rememberSaveable(folder.id) {
        mutableStateOf(folder.podcastIds.toSet())
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Sort shows: already added to this folder first, then remainder by title
    val sortedPodcasts = remember(allSubscribedPodcasts, folder.podcastIds) {
        val initialMemberIds = folder.podcastIds.toSet()
        val podcastMap = allSubscribedPodcasts.associateBy { it.id }
        val members = folder.podcastIds.mapNotNull { podcastMap[it] }
        val nonMembers = allSubscribedPodcasts.filter { it.id !in initialMemberIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        members + nonMembers
    }

    val filteredPodcasts = remember(sortedPodcasts, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedPodcasts
        } else {
            val query = searchQuery.trim()
            sortedPodcasts.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // The sheet's own (lower) container, so the search pill and the rows stand out on it.
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            FolderShowsSelectionHeader(
                folderName = folder.name,
                selectedCount = selectedIds.size,
                onDismissRequest = onDismissRequest,
            )

            if (allSubscribedPodcasts.size > 5) {
                FolderShowsSelectionSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Shows List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp),
            ) {
                items(filteredPodcasts, key = { it.id }) { podcast ->
                    val isChecked = podcast.id in selectedIds
                    FolderShowCheckboxRow(
                        podcast = podcast,
                        isChecked = isChecked,
                        onToggle = {
                            selectedIds = if (isChecked) {
                                selectedIds - podcast.id
                            } else {
                                selectedIds + podcast.id
                            }
                        },
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
            )

            FolderShowsSelectionFooter(
                onDismissRequest = onDismissRequest,
                onSave = {
                    val finalOrderedIds = sortedPodcasts
                        .filter { it.id in selectedIds }
                        .map { it.id }
                    onSave(finalOrderedIds)
                    onDismissRequest()
                },
            )
        }
    }
}

@Composable
private fun FolderShowsSelectionHeader(
    folderName: String,
    selectedCount: Int,
    onDismissRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.library_folder_add_shows_to, folderName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.library_selected_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        IconButton(onClick = onDismissRequest) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.library_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** boxlore's shared search pill; a long hint ends with "…" instead of wrapping. */
@Composable
private fun FolderShowsSelectionSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    BoxloreSearchField(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        onSearch = { keyboard?.hide() },
        placeholder = stringResource(R.string.library_search_subscribed_shows),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        maxWidth = null,
    )
}

@Composable
private fun FolderShowsSelectionFooter(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismissRequest) {
            Text(stringResource(R.string.library_cancel))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSave,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.library_save))
        }
    }
}

/**
 * One show to tick: a solid row that ticks as a whole, so TalkBack reads it once with its state
 * ("ticked" or "not ticked"), like Add to folders' rows.
 */
@Composable
private fun FolderShowCheckboxRow(
    podcast: Podcast,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // The sheet's own colour (Material's bottom sheet container).
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(44.dp),
            ) {
                if (podcast.artworkUrls().isNotEmpty()) {
                    OptimizedImage(
                        proxyWidth = 140,
                        url = podcast.imageUrl,
                        fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
                        contentDescription = null,
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
                    text = podcast.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Checkbox(checked = isChecked, onCheckedChange = null)
        }
    }
}
