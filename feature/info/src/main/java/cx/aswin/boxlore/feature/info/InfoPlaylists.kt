package cx.aswin.boxlore.feature.info

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistSheet
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistSheetState
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * The show page's Add to playlist: puts the show in playlists, takes it out, or makes a playlist
 * holding it. The page's ViewModel owns it, so a change finishes in the ViewModel's scope;
 * [onMessage] gets the confirmation to show ([AddToPlaylistCopy]). Sends `playlist_modified`
 * (create, add_show, remove_show) with source `show_page`. [followId] turns the shown show's ID into
 * the ID its follow is stored under (its followed `rss:` feed, or the number the page was opened
 * as), which is the ID a playlist plays and checks as followed.
 */
class InfoPlaylists internal constructor(
    private val repository: PlaylistRepository,
    private val scope: CoroutineScope,
    private val followId: suspend (String) -> String = { it },
) {
    val playlists: Flow<List<Playlist>> get() = repository.playlists

    /** The IDs of the playlists holding show [podcastId] (under the ID its follow is stored under). */
    fun memberIds(podcastId: String): Flow<Set<String>> = flow { emitAll(repository.playlistIdsForShow(followId(podcastId))) }

    /** Adds show [shownId] to [playlist], or removes it when [isMember]. */
    fun toggle(playlist: Playlist, shownId: String, isMember: Boolean, onMessage: (String) -> Unit) {
        scope.launch {
            val podcastId = followId(shownId)
            if (isMember) {
                if (repository.removeShow(playlist.id, podcastId) == null) return@launch
                AnalyticsHelper.trackPlaylistModified("remove_show", SOURCE_SHOW_PAGE, playlistSize = (playlist.showCount - 1).coerceAtLeast(0))
                onMessage(AddToPlaylistCopy.removed(playlist.name))
            } else {
                if (repository.addShows(playlist.id, listOf(podcastId)) == 0) return@launch
                AnalyticsHelper.trackPlaylistModified("add_show", SOURCE_SHOW_PAGE, playlistSize = playlist.showCount + 1)
                onMessage(AddToPlaylistCopy.added(playlist.name))
            }
        }
    }

    /** Makes a playlist named [name] holding show [shownId]. */
    fun create(name: String, shownId: String, onMessage: (String) -> Unit) {
        scope.launch {
            val created = repository.createPlaylist(name, listOf(followId(shownId)))
            AnalyticsHelper.trackPlaylistModified("create", SOURCE_SHOW_PAGE, playlistSize = created.showCount)
            onMessage(AddToPlaylistCopy.added(created.name))
        }
    }

    internal companion object {
        const val SOURCE_SHOW_PAGE = "show_page"

        /** Null without a repository (tests, previews), which hides Add to playlist. */
        fun create(repository: PlaylistRepository?, scope: CoroutineScope, followId: suspend (String) -> String = { it }): InfoPlaylists? =
            repository?.let { InfoPlaylists(it, scope, followId) }
    }
}

/** Whether the Add to playlist sheet is open and what it shows; saved, so it survives resizing and recreation. */
@Stable
internal class InfoPlaylistPicker(open: Boolean = false, sheet: AddToPlaylistSheetState = AddToPlaylistSheetState()) {
    var open by mutableStateOf(open)
    val sheet = sheet

    fun close() {
        open = false
        sheet.stopCreating()
    }

    companion object {
        val Saver: Saver<InfoPlaylistPicker, Any> = Saver(
            save = { picker -> listOf(picker.open, picker.sheet.isCreating, picker.sheet.newName.orEmpty()) },
            restore = { saved ->
                val values = saved as List<*>
                InfoPlaylistPicker(
                    open = values.getOrNull(0) == true,
                    sheet = AddToPlaylistSheetState(if (values.getOrNull(1) == true) values.getOrNull(2) as? String ?: "" else null),
                )
            },
        )
    }
}

@Composable
internal fun rememberInfoPlaylistPicker(key: Any? = null): InfoPlaylistPicker =
    rememberSaveable(key, saver = InfoPlaylistPicker.Saver) { InfoPlaylistPicker() }

/**
 * The Add to playlist sheet for [show] while [picker] is open. [show] is null while the show isn't
 * followed, which keeps the sheet away. A tap on a playlist toggles the show and keeps the sheet
 * open; a new playlist closes it.
 */
@Composable
internal fun InfoPlaylistPickerHost(picker: InfoPlaylistPicker, playlists: InfoPlaylists?, show: Podcast?, snackbarHostState: SnackbarHostState) {
    // Remembered before the sheet, so a confirmation still shows after the sheet closes.
    val scope = rememberCoroutineScope()
    if (!picker.open || playlists == null || show == null) return
    val all by playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val members by remember(playlists, show.id) { playlists.memberIds(show.id) }.collectAsStateWithLifecycle(initialValue = emptySet())
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }
    AddToPlaylistSheet(
        showTitle = show.title,
        playlists = all,
        memberIds = members,
        state = picker.sheet,
        onToggle = { playlist -> playlists.toggle(playlist, show.id, playlist.id in members, showMessage) },
        onCreate = { name ->
            playlists.create(name, show.id, showMessage)
            picker.close()
        },
        onDismissRequest = picker::close,
    )
}
