package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_NAME_MAX_LENGTH
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.normalizePlaylistName

/** The Add to playlist sheet's wording and its result messages. */
object AddToPlaylistCopy {
    const val TITLE = "Add to playlist"
    const val NO_PLAYLISTS = "You don’t have any playlists yet."
    const val CREATE_AND_ADD = "Create and add"
    const val FULL = "Full"

    fun subtitle(episodeCount: Int): String? = if (episodeCount > 1) "$episodeCount episodes" else null

    fun playlistDetail(playlist: Playlist): String =
        if (playlist.isFull) "${PlaylistCopy.episodeCount(playlist.episodeCount)} · $FULL" else PlaylistCopy.episodeCount(playlist.episodeCount)

    /**
     * What happened after adding to [playlistName]: [added] new episodes, [alreadyIn] it already held,
     * [didNotFit] left out because it's full.
     */
    fun result(playlistName: String, added: Int, alreadyIn: Int = 0, didNotFit: Int = 0): String {
        val addedPart = if (added == 1) "Added to $playlistName" else "Added $added episodes to $playlistName"
        return when {
            added == 0 && didNotFit > 0 -> "$playlistName is full. Remove an episode to add more."
            added == 0 && alreadyIn > 0 -> "Already in $playlistName"
            added == 0 -> "Nothing to add"
            didNotFit > 0 -> "$addedPart. It’s full, so ${count(didNotFit)} ${if (didNotFit == 1) "wasn’t" else "weren’t"} added."
            alreadyIn > 0 -> "$addedPart. ${count(alreadyIn)} ${if (alreadyIn == 1) "was" else "were"} already in it."
            else -> addedPart
        }
    }

    private fun count(value: Int) = if (value == 1) "1" else "$value"
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
 * Picks a playlist to add [episodeCount] episodes to, or makes a new one. Stateless: the caller
 * owns visibility and [state], adds the episodes in [onAddTo] / [onCreate] (a normalised name),
 * then hides the sheet and confirms with [AddToPlaylistCopy.result]. A full playlist can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    episodeCount: Int,
    state: AddToPlaylistSheetState,
    onAddTo: (Playlist) -> Unit,
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
                AddToPlaylistCopy.subtitle(episodeCount)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val draft = state.newName
            if (draft != null) {
                NewPlaylistField(draft, onNameChange = { state.newName = it }, onCreate = onCreate, onCancel = state::stopCreating)
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    SheetRow(
                        title = PlaylistCopy.NEW_PLAYLIST,
                        detail = null,
                        enabled = true,
                        onClick = state::startCreating,
                        leading = { RowIcon { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) } },
                        titleColor = MaterialTheme.colorScheme.primary,
                    )
                    if (playlists.isEmpty()) {
                        Text(
                            AddToPlaylistCopy.NO_PLAYLISTS,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    playlists.forEach { playlist ->
                        SheetRow(
                            title = playlist.name,
                            detail = AddToPlaylistCopy.playlistDetail(playlist),
                            enabled = !playlist.isFull,
                            onClick = { onAddTo(playlist) },
                            leading = { PlaylistCover(playlist.coverUrls.firstOrNull()) },
                        )
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
@Suppress("LongParameterList")
private fun SheetRow(
    title: String,
    detail: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leading()
            Column(Modifier.weight(1f)) {
                val alpha = if (enabled) 1f else DISABLED_ALPHA
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = titleColor.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                }
            }
        }
    }
}

@Composable
private fun RowIcon(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A playlist's cover: its first episode's artwork, or a playlist mark on a solid surface. */
@Composable
fun PlaylistCover(imageUrl: String?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp))) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        if (!imageUrl.isNullOrBlank()) {
            OptimizedImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
                proxyWidth = 160,
                showLoadingPlaceholder = false,
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

/** The sheet's content, for tests. */
const val ADD_TO_PLAYLIST_SHEET_TAG = "add_to_playlist_sheet"
