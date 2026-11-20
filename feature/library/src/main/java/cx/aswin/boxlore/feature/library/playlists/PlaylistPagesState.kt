package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** What is open over the Playlists or playlist page. One at a time. */
internal sealed interface PlaylistEditor {
    /** New playlist: a name and its shows. */
    data object Create : PlaylistEditor

    /** Add shows to [playlistId]. */
    data class AddShows(val playlistId: String) : PlaylistEditor
    data class Rename(val playlistId: String) : PlaylistEditor
    data class Delete(val playlistId: String) : PlaylistEditor

    val picksShows: Boolean get() = this == Create || this is AddShows

    companion object {
        fun encode(editor: PlaylistEditor?): String = when (editor) {
            null -> ""
            Create -> "create"
            is AddShows -> "add:${editor.playlistId}"
            is Rename -> "rename:${editor.playlistId}"
            is Delete -> "delete:${editor.playlistId}"
        }

        fun decode(value: String?): PlaylistEditor? = when {
            value == "create" -> Create
            value?.startsWith("add:") == true -> AddShows(value.removePrefix("add:"))
            value?.startsWith("rename:") == true -> Rename(value.removePrefix("rename:"))
            value?.startsWith("delete:") == true -> Delete(value.removePrefix("delete:"))
            else -> null
        }
    }
}

/**
 * What the playlist pages hold that isn't saved yet: the open editor, the name being typed and the
 * shows picked, in the order they were picked. All of it is saved state, so it survives rotation,
 * resizing, folding and recreation.
 */
@Stable
internal class PlaylistPagesState(editor: PlaylistEditor? = null, nameDraft: String = "", picked: List<String> = emptyList()) {
    var editor by mutableStateOf(editor)
        private set
    var nameDraft by mutableStateOf(nameDraft)
    var picked by mutableStateOf(picked)
        private set

    fun openCreate() {
        nameDraft = ""
        picked = emptyList()
        editor = PlaylistEditor.Create
    }

    fun openAddShows(playlistId: String) {
        picked = emptyList()
        editor = PlaylistEditor.AddShows(playlistId)
    }

    fun openRename(playlistId: String, currentName: String) {
        nameDraft = currentName
        editor = PlaylistEditor.Rename(playlistId)
    }

    fun openDelete(playlistId: String) {
        editor = PlaylistEditor.Delete(playlistId)
    }

    fun toggle(podcastId: String) {
        picked = if (podcastId in picked) picked - podcastId else picked + podcastId
    }

    /** Picks [podcastIds] too (a folder's shows), after the ones already picked. */
    fun pickAll(podcastIds: List<String>) {
        picked = (picked + podcastIds).distinct()
    }

    fun closeEditor() {
        editor = null
        nameDraft = ""
        picked = emptyList()
    }

    companion object {
        val Saver: Saver<PlaylistPagesState, Any> = listSaver(
            save = { state -> listOf(PlaylistEditor.encode(state.editor), state.nameDraft, ArrayList(state.picked)) },
            restore = { values ->
                PlaylistPagesState(
                    editor = PlaylistEditor.decode(values.getOrNull(0) as? String),
                    nameDraft = values.getOrNull(1) as? String ?: "",
                    picked = (values.getOrNull(2) as? List<*>)?.filterIsInstance<String>().orEmpty(),
                )
            },
        )
    }
}

@Composable
internal fun rememberPlaylistPagesState(): PlaylistPagesState = rememberSaveable(saver = PlaylistPagesState.Saver) { PlaylistPagesState() }
