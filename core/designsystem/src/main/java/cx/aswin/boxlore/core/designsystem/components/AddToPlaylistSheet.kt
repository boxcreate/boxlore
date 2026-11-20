package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_NAME_MAX_LENGTH
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.normalizePlaylistName

/** The show page's Add to playlist sheet wording and its confirmations. */
object AddToPlaylistCopy {
    const val TITLE = "Add to playlist"
    const val NO_PLAYLISTS = "You don’t have any playlists yet."
    const val CREATE_AND_ADD = "Create and add"

    fun added(playlistName: String) = "Added to $playlistName"

    fun removed(playlistName: String) = "Removed from $playlistName"
}

/**
 * What the Add to playlist sheet shows: the playlist list, or the new playlist name field with its
 * draft. Hoist it above the sheet in saved state ([rememberAddToPlaylistSheetState]), so the draft
 * survives the sheet moving between its phone and tablet hosts and recreation.
 */
@Stable
class AddToPlaylistSheetState(newName: String? = null) {
    /** The new playlist's name being typed, or null while the list shows. */
    var newName by mutableStateOf(newName)

    val isCreating: Boolean get() = newName != null

    fun startCreating() {
        newName = ""
    }

    fun stopCreating() {
        newName = null
    }

    companion object {
        val Saver: Saver<AddToPlaylistSheetState, Any> = listSaver(
            save = { listOf(it.isCreating, it.newName.orEmpty()) },
            restore = { AddToPlaylistSheetState(if (it.getOrNull(0) == true) it.getOrNull(1) as? String ?: "" else null) },
        )
    }
}

@Composable
fun rememberAddToPlaylistSheetState(key: Any? = null): AddToPlaylistSheetState =
    rememberSaveable(key, saver = AddToPlaylistSheetState.Saver) { AddToPlaylistSheetState() }

/**
 * Puts the show [showTitle] in playlists or takes it out: each playlist shows a check when it holds
 * the show ([memberIds]) and a tap toggles it ([onToggle]); New playlist makes one holding the show
 * ([onCreate] gets the normalised name). Stateless: the caller owns visibility and [state], writes
 * the change and confirms it with [AddToPlaylistCopy].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun AddToPlaylistSheet(
    showTitle: String,
    playlists: List<Playlist>,
    memberIds: Set<String>,
    state: AddToPlaylistSheetState,
    onToggle: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    TrackScreenView("add_to_playlist")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BoxloreModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).testTag(ADD_TO_PLAYLIST_SHEET_TAG)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(AddToPlaylistCopy.TITLE, style = MaterialTheme.typography.titleLarge, fontWeight = GoogleSansWeight.bold)
                if (showTitle.isNotBlank()) {
                    Text(showTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val draft = state.newName
            if (draft != null) {
                NewPlaylistField(draft, onNameChange = { state.newName = it }, onCreate = onCreate, onCancel = state::stopCreating)
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    NewPlaylistRow(onClick = state::startCreating)
                    if (playlists.isEmpty()) {
                        Text(
                            AddToPlaylistCopy.NO_PLAYLISTS,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    playlists.forEach { playlist ->
                        PlaylistToggleRow(playlist, checked = playlist.id in memberIds, onToggle = { onToggle(playlist) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistField(name: String, onNameChange: (String) -> Unit, onCreate: (String) -> Unit, onCancel: () -> Unit) {
    val normalized = normalizePlaylistName(name)
    val create = { if (normalized.isNotEmpty()) onCreate(normalized) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it.take(PLAYLIST_NAME_MAX_LENGTH)) },
            label = { Text(PlaylistCopy.NAME_LABEL) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { create() }),
            modifier = Modifier.fillMaxWidth().testTag(PLAYLIST_NAME_FIELD_TAG),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(PlaylistCopy.CANCEL) }
            Button(onClick = create, enabled = normalized.isNotEmpty(), modifier = Modifier.padding(start = 8.dp).heightIn(min = 48.dp)) {
                Text(AddToPlaylistCopy.CREATE_AND_ADD)
            }
        }
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(PlaylistCopy.NEW_PLAYLIST, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A playlist with a check for whether it holds the show; the whole row toggles it. */
@Composable
private fun PlaylistToggleRow(playlist: Playlist, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaylistCover(playlist.coverUrls)
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(PlaylistCopy.showCount(playlist.showCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row is the toggle; the box only shows its state.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/**
 * A playlist's cover: its shows' artwork filling the whole tile ([playlistCoverTiles]: one cover,
 * two halves, a half and two quarters, or four quarters), or a playlist mark on a solid surface
 * when it has none.
 */
@Composable
fun PlaylistCover(imageUrls: List<String>, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val urls = imageUrls.filter(String::isNotBlank).take(PlaylistCoverTile.MAX)
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp))) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        urls.zip(playlistCoverTiles(urls.size)).forEach { (url, tile) ->
            OptimizedImage(
                url = url,
                contentDescription = null,
                modifier = Modifier.offset(size * tile.x, size * tile.y).size(size * tile.width, size * tile.height),
                contentScale = ContentScale.Crop,
                proxyWidth = 160,
                showLoadingPlaceholder = false,
            )
        }
    }
}

/** Where one cover sits in a [PlaylistCover], as fractions of the tile. */
internal data class PlaylistCoverTile(val x: Float, val y: Float, val width: Float, val height: Float) {
    companion object {
        /** Covers a tile shows at most. */
        const val MAX = 4
    }
}

/** How [count] covers fill the whole tile, with no gap: one, two halves, a half and two quarters, or four quarters. */
internal fun playlistCoverTiles(count: Int): List<PlaylistCoverTile> {
    val half = 0.5f
    return when (count.coerceIn(0, PlaylistCoverTile.MAX)) {
        0 -> emptyList()
        1 -> listOf(PlaylistCoverTile(0f, 0f, 1f, 1f))
        2 -> listOf(PlaylistCoverTile(0f, 0f, half, 1f), PlaylistCoverTile(half, 0f, half, 1f))
        3 -> listOf(PlaylistCoverTile(0f, 0f, half, 1f), PlaylistCoverTile(half, 0f, half, half), PlaylistCoverTile(half, half, half, half))
        else -> listOf(
            PlaylistCoverTile(0f, 0f, half, half),
            PlaylistCoverTile(half, 0f, half, half),
            PlaylistCoverTile(0f, half, half, half),
            PlaylistCoverTile(half, half, half, half),
        )
    }
}

/** The sheet's content, for tests. */
const val ADD_TO_PLAYLIST_SHEET_TAG = "add_to_playlist_sheet"
