package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** Unsaved changes on the Edit folder page; kept in saved state across rotation, resize and fold. */
internal data class EditFolderDraft(
    val name: String,
    val icon: String?,
    val size: FolderDisplaySize,
    val tileShowsCovers: Boolean,
    /** The genre a genre folder fills from; null for a folder the listener fills. */
    val genre: String?,
) {
    companion object {
        fun from(folder: SubscriptionFolder) = EditFolderDraft(
            name = folder.name,
            icon = folder.icon,
            size = folder.displaySize,
            tileShowsCovers = folder.effectiveShowPodcastGrid,
            genre = folder.linkedGenre?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun canSave(): Boolean = name.isNotBlank()

    /** The folder as saved: the repository still renames Technology and refills genre folders. */
    fun applyTo(folder: SubscriptionFolder): SubscriptionFolder {
        val icon = icon?.trim()?.takeIf { it.isNotEmpty() }
        return folder.copy(
            name = name.trim(),
            icon = icon,
            displaySize = size,
            showPodcastGrid = icon == null || tileShowsCovers,
            linkedGenre = genre,
        )
    }
}

internal val EditFolderDraftSaver: Saver<EditFolderDraft, Any> = listSaver(
    save = { listOf(it.name, it.icon, it.size.name, it.tileShowsCovers, it.genre) },
    restore = { saved ->
        EditFolderDraft(
            name = saved[0] as String,
            icon = saved[1] as String?,
            size = FolderDisplaySize.valueOf(saved[2] as String),
            tileShowsCovers = saved[3] as Boolean,
            genre = saved[4] as String?,
        )
    },
)

/** The folder's shows, and the whole library its genre pills count from. */
internal data class EditFolderShows(val inFolder: List<Podcast>, val library: List<Podcast>)

internal data class EditFolderActions(
    val onClose: () -> Unit,
    val onSave: (SubscriptionFolder) -> Unit,
    val onAddShows: () -> Unit,
    val onRemoveShow: (Podcast) -> Unit,
    val onDelete: () -> Unit,
)

/**
 * Edit folder, one page: name and icon, where the shows come from, how it looks, and Delete.
 * Name, icon, genre and look save with Save; removing a show takes effect at once and keeps the
 * show in the library, like Remove from folder.
 */
@Composable
internal fun EditFolderPage(
    folder: SubscriptionFolder,
    shows: EditFolderShows,
    draft: EditFolderDraft,
    onDraftChange: (EditFolderDraft) -> Unit,
    actions: EditFolderActions,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("edit_folder")
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            FolderFlowTopBar(
                title = "Edit folder",
                onBack = actions.onClose,
                showClose = true,
                action = {
                    Button(onClick = { actions.onSave(draft.applyTo(folder)) }, enabled = draft.canSave()) {
                        Text(text = "Save")
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = bottomPadding + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FolderNameField(
                    name = draft.name,
                    onNameChange = { onDraftChange(draft.copy(name = it)) },
                    iconKey = draft.icon,
                    helper = NewFolderFlowLogic.savedNameNote(draft.name),
                )
                if (draft.genre != null) {
                    EditGenreSection(draft, onDraftChange, shows.library)
                } else {
                    EditShowsSection(shows.inFolder, actions)
                }
                Text(text = "How it looks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                FolderLookRow(selected = draft.size, onSelect = { onDraftChange(draft.copy(size = it)) })
                if (draft.size == FolderDisplaySize.COMPACT) {
                    TileCoverChoice(
                        showsCovers = draft.tileShowsCovers,
                        onChange = { onDraftChange(draft.copy(tileShowsCovers = it)) },
                        iconKey = draft.icon,
                    )
                }
                Text(
                    text = folderLookDescription(draft.size) + " With more shows, the last spot becomes +N and opens the folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = "Icon", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                FolderIconGrid(selectedKey = draft.icon, onSelect = { onDraftChange(draft.copy(icon = it)) })
                DeleteFolderRow(onDelete = actions.onDelete)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditGenreSection(
    draft: EditFolderDraft,
    onDraftChange: (EditFolderDraft) -> Unit,
    libraryPodcasts: List<Podcast>,
) {
    val genres = remember(libraryPodcasts) { extractDistinctGenres(libraryPodcasts) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AutoFolderMark(size = 16.dp)
        Text(text = "Genre", style = MaterialTheme.typography.titleSmall)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEach { genre ->
            val item = resolveSubscriptionGenreItem(genre, libraryPodcasts)
            SubscriptionChip(
                label = item.label,
                selected = draft.genre.equals(item.label, ignoreCase = true),
                onClick = { onDraftChange(draft.copy(genre = item.label)) },
                icon = item.icon,
            )
        }
    }
}

@Composable
private fun EditShowsSection(shows: List<Podcast>, actions: EditFolderActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Shows · ${shows.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        SubscriptionActionChip(label = "Add shows", onClick = actions.onAddShows, icon = Icons.Rounded.Add)
    }
    shows.forEach { show ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            ) { PreviewCover(show) }
            Text(
                text = show.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            IconButton(onClick = { actions.onRemoveShow(show) }) {
                Icon(imageVector = Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove ${show.title} from this folder")
            }
        }
    }
    Text(
        text = "Removing a show here keeps it in your library.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeleteFolderRow(onDelete: () -> Unit) {
    Surface(
        onClick = onDelete,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Delete folder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Its shows stay in your library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
