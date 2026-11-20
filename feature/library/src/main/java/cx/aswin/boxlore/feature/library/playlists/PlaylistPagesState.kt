package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** A dialog open over the Playlists or playlist page. One at a time. */
internal sealed interface PlaylistEditor {
    data class Rename(val playlistId: String) : PlaylistEditor
    data class Delete(val playlistId: String) : PlaylistEditor

    companion object {
        fun encode(editor: PlaylistEditor?): String = when (editor) {
            null -> ""
            is Rename -> "rename:${editor.playlistId}"
            is Delete -> "delete:${editor.playlistId}"
        }

        fun decode(value: String?): PlaylistEditor? = when {
            value?.startsWith("rename:") == true -> Rename(value.removePrefix("rename:"))
            value?.startsWith("delete:") == true -> Delete(value.removePrefix("delete:"))
            else -> null
        }
    }
}

/**
 * What the playlist pages hold that isn't saved yet: New playlist or Add shows in progress
 * ([newDraft], every step and pick), and the open dialog with the name being typed. All of it is
 * saved state, so it survives rotation, resizing, folding and recreation.
 */
@Stable
internal class PlaylistPagesState(editor: PlaylistEditor? = null, nameDraft: String = "", newDraft: NewPlaylistDraft? = null) {
    var editor by mutableStateOf(editor)
        private set
    var nameDraft by mutableStateOf(nameDraft)
    var newDraft by mutableStateOf(newDraft)

    fun openCreate() {
        newDraft = NewPlaylistFlowLogic.start()
    }

    fun openAddShows(playlistId: String) {
        newDraft = NewPlaylistFlowLogic.addShows(playlistId)
    }

    fun closeFlow() {
        newDraft = null
    }

    fun openRename(playlistId: String, currentName: String) {
        nameDraft = currentName
        editor = PlaylistEditor.Rename(playlistId)
    }

    fun openDelete(playlistId: String) {
        editor = PlaylistEditor.Delete(playlistId)
    }

    fun closeEditor() {
        editor = null
        nameDraft = ""
    }

    companion object {
        val Saver: Saver<PlaylistPagesState, Any> = listSaver(
            save = { state ->
                listOf(
                    PlaylistEditor.encode(state.editor),
                    state.nameDraft,
                    state.newDraft?.let { with(NewPlaylistDraftSaver) { save(it) } },
                )
            },
            restore = { values ->
                PlaylistPagesState(
                    editor = PlaylistEditor.decode(values.getOrNull(0) as? String),
                    nameDraft = values.getOrNull(1) as? String ?: "",
                    newDraft = values.getOrNull(2)?.let(NewPlaylistDraftSaver::restore),
                )
            },
        )
    }
}

@Composable
internal fun rememberPlaylistPagesState(): PlaylistPagesState = rememberSaveable(saver = PlaylistPagesState.Saver) { PlaylistPagesState() }
