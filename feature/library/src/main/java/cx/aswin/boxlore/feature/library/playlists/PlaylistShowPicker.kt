package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PLAYLIST_NAME_FIELD_TAG
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_NAME_MAX_LENGTH
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.normalizePlaylistName
import cx.aswin.boxlore.feature.library.R

/** The show picker's wording. */
internal object PlaylistPickerCopy {
    val ADD_SHOWS: String @Composable get() = stringResource(R.string.library_add_shows)
    val ADD: String @Composable get() = stringResource(R.string.library_add)
    val FROM_FOLDERS: String @Composable get() = stringResource(R.string.library_playlist_from_folders)
    val SHOWS_YOU_FOLLOW: String @Composable get() = stringResource(R.string.library_shows_you_follow)
    val NO_FOLLOWED: String @Composable get() = stringResource(R.string.library_playlist_no_followed)
    val ALL_ADDED: String @Composable get() = stringResource(R.string.library_playlist_all_added)

    @Composable
    fun picked(count: Int): String = pluralStringResource(R.plurals.library_shows_picked, count, count)
}

/** Pure rules for the show picker, unit-tested without Compose. */
internal object PlaylistShowPickerLogic {
    /** The followed shows that can be picked: all of them, less [inPlaylist], in their order. */
    fun pickable(followed: List<Podcast>, inPlaylist: Set<String>): List<Podcast> = followed.filter { it.id !in inPlaylist }

    /** Folders with at least one pickable show, each with those shows' IDs; the chips pre-fill the pick and keep no link to the folder. */
    fun folderChips(folders: List<SubscriptionFolder>, pickable: List<Podcast>): List<Pair<SubscriptionFolder, List<String>>> {
        val ids = pickable.map { it.id }.toSet()
        return folders.mapNotNull { folder -> folder.podcastIds.filter { it in ids }.takeIf { it.isNotEmpty() }?.let { folder to it } }
    }

    /** A new playlist needs a name; adding needs at least one show. */
    fun canConfirm(editor: PlaylistEditor, name: String, picked: List<String>): Boolean = when (editor) {
        PlaylistEditor.Create -> normalizePlaylistName(name).isNotEmpty()
        is PlaylistEditor.AddShows -> picked.isNotEmpty()
        else -> false
    }
}

/**
 * New playlist or Add shows: an optional name field, Library folder chips and the followed shows
 * with a check each. Stateless over [state] (saved), so the name and picks survive the sheet moving
 * between its phone and tablet hosts and recreation. [onConfirm] gets the name (Create only) and the picked IDs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun PlaylistShowPicker(
    state: PlaylistPagesState,
    followed: List<Podcast>,
    folders: List<SubscriptionFolder>,
    inPlaylist: Set<String>,
    onConfirm: (name: String, podcastIds: List<String>) -> Unit,
) {
    val editor = state.editor ?: return
    TrackScreenView(if (editor == PlaylistEditor.Create) "new_playlist" else "playlist_add_shows")
    val creating = editor == PlaylistEditor.Create
    val pickable = PlaylistShowPickerLogic.pickable(followed, inPlaylist)
    val chips = PlaylistShowPickerLogic.folderChips(folders, pickable)
    val canConfirm = PlaylistShowPickerLogic.canConfirm(editor, state.nameDraft, state.picked)
    val confirm = { if (canConfirm) onConfirm(normalizePlaylistName(state.nameDraft), state.picked) }
    BoxloreModalBottomSheet(
        onDismissRequest = state::closeEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag(PLAYLIST_SHOW_PICKER_TAG)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (creating) stringResource(PlaylistCopy.NEW_PLAYLIST) else PlaylistPickerCopy.ADD_SHOWS, style = MaterialTheme.typography.titleLarge, fontWeight = GoogleSansWeight.bold)
                    Text(PlaylistPickerCopy.picked(state.picked.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = state::closeEditor) { Text(stringResource(PlaylistCopy.CANCEL)) }
                Button(onClick = confirm, enabled = canConfirm, modifier = Modifier.padding(start = 4.dp).heightIn(min = 48.dp)) {
                    Text(if (creating) stringResource(PlaylistCopy.CREATE) else PlaylistPickerCopy.ADD)
                }
            }
            if (creating) {
                OutlinedTextField(
                    value = state.nameDraft,
                    onValueChange = { state.nameDraft = it.take(PLAYLIST_NAME_MAX_LENGTH) },
                    label = { Text(stringResource(PlaylistCopy.NAME_LABEL)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag(PLAYLIST_NAME_FIELD_TAG),
                )
            }
            if (chips.isNotEmpty()) {
                SectionLabel(PlaylistPickerCopy.FROM_FOLDERS)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { (folder, ids) ->
                        AssistChip(
                            onClick = { state.pickAll(ids) },
                            label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                }
            }
            SectionLabel(PlaylistPickerCopy.SHOWS_YOU_FOLLOW)
            when {
                followed.isEmpty() -> EmptyLine(PlaylistPickerCopy.NO_FOLLOWED)
                pickable.isEmpty() -> EmptyLine(PlaylistPickerCopy.ALL_ADDED)
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(pickable, key = { it.id }) { show ->
                        ShowPickRow(show, checked = show.id in state.picked, onToggle = { state.toggle(show.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}

@Composable
private fun ShowPickRow(show: Podcast, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OptimizedImage(
            url = show.imageUrl,
            contentDescription = null,
            proxyWidth = 120,
            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
        Text(show.title, style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // The row is the toggle; the box only shows its state.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** The show picker's content, for tests. */
internal const val PLAYLIST_SHOW_PICKER_TAG = "playlist_show_picker"
