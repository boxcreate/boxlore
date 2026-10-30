package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** Folders to add [podcast] to and take it out of, from the Add to folders sheet's ticks. */
internal data class FolderMembershipChange(val addTo: List<String>, val removeFrom: List<String>)

/**
 * Only folders the listener fills can be ticked: a genre folder's shows come from its genre, and
 * a show added by hand would leave again at its next refill.
 */
internal fun folderMembershipChange(
    podcastId: String,
    folders: List<SubscriptionFolder>,
    tickedFolderIds: Set<String>,
): FolderMembershipChange {
    val pickable = folders.filterNot { it.isGenreLinked }
    return FolderMembershipChange(
        addTo = pickable.filter { it.id in tickedFolderIds && podcastId !in it.podcastIds }.map { it.id },
        removeFrom = pickable.filter { it.id !in tickedFolderIds && podcastId in it.podcastIds }.map { it.id },
    )
}

/** A show can sit in several folders: tick the ones it belongs in, then Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToFoldersSheet(
    podcast: Podcast,
    folders: List<SubscriptionFolder>,
    onDone: (FolderMembershipChange) -> Unit,
    onNewFolderWithShow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickable = folders.filterNot { it.isGenreLinked }
    val genreFolders = folders.filter { it.isGenreLinked }
    var ticked by rememberSaveable(podcast.id) {
        mutableStateOf(pickable.filter { podcast.id in it.podcastIds }.map { it.id })
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                SheetShowHeader(podcast = podcast, folderCount = folders.count { podcast.id in it.podcastIds })
                Surface(onClick = onNewFolderWithShow, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "New folder with ${podcast.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pickable.isNotEmpty()) {
                    SheetSectionLabel(text = "Your folders")
                    pickable.forEach { folder ->
                        val isTicked = folder.id in ticked
                        FolderTickRow(
                            folder = folder,
                            detail = showCountLabel(folder.podcastCount),
                            checked = isTicked,
                            enabled = true,
                            onToggle = { ticked = if (isTicked) ticked - folder.id else ticked + folder.id },
                        )
                    }
                }
                if (genreFolders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AutoFolderMark(size = 16.dp)
                        Text(text = "Genre folders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    genreFolders.forEach { folder ->
                        val member = podcast.id in folder.podcastIds
                        FolderTickRow(
                            folder = folder,
                            detail = if (member) "In it" else "Only ${folder.linkedGenre} shows",
                            checked = member,
                            enabled = false,
                            onToggle = {},
                        )
                    }
                    Text(
                        text = "To put ${podcast.title} in a genre folder, change its genre from its page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            Button(
                onClick = { onDone(folderMembershipChange(podcast.id, folders, ticked.toSet())) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).heightIn(min = 52.dp),
            ) {
                Text(text = "Done")
            }
        }
    }
}

@Composable
private fun SheetShowHeader(podcast: Podcast, folderCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) { PreviewCover(podcast) }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (folderCount) {
                    0 -> "Not in a folder yet"
                    1 -> "In 1 folder"
                    else -> "In $folderCount folders"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun FolderTickRow(
    folder: SubscriptionFolder,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.semantics { role = Role.Checkbox },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = GenreIcons.folderIconOrFallback(folder.icon),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}
