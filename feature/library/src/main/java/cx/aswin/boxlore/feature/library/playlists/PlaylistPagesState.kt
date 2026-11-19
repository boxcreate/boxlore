package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** A playlist dialog over the Playlists or playlist page. One at a time. */
internal sealed interface PlaylistEditor {
    data object Create : PlaylistEditor
    data class Rename(val playlistId: String) : PlaylistEditor
    data class Delete(val playlistId: String) : PlaylistEditor

    companion object {
        fun encode(editor: PlaylistEditor?): String = when (editor) {
            null -> ""
            Create -> "create"
            is Rename -> "rename:${editor.playlistId}"
            is Delete -> "delete:${editor.playlistId}"
        }

        fun decode(value: String?): PlaylistEditor? = when {
            value == "create" -> Create
            value?.startsWith("rename:") == true -> Rename(value.removePrefix("rename:"))
            value?.startsWith("delete:") == true -> Delete(value.removePrefix("delete:"))
            else -> null
        }
    }
}

/**
 * What the playlist pages hold that isn't saved yet: the open dialog, the name being typed, and a
 * drag reorder not yet written (item IDs in their new order). All of it is saved state, so it survives
 * rotation, resizing, folding and recreation.
 */
@Stable
internal class PlaylistPagesState(editor: PlaylistEditor? = null, nameDraft: String = "", pendingOrder: List<Long>? = null,) {
    var editor by mutableStateOf(editor)
        private set
    var nameDraft by mutableStateOf(nameDraft)
    var pendingOrder by mutableStateOf(pendingOrder)

    fun openCreate() {
        nameDraft = ""
        editor = PlaylistEditor.Create
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

    /** A drag step: [fromId] moves to where [toId] is, starting from [currentOrder] when no drag is pending. */
    fun move(currentOrder: List<Long>, fromId: Long, toId: Long) {
        pendingOrder = PlaylistPlaybackLogic.move(pendingOrder ?: currentOrder, fromId, toId)
    }

    /** Clears the pending order once the stored order matches it (or its rows are gone). */
    fun settle(storedOrder: List<Long>) {
        val pending = pendingOrder ?: return
        if (pending == storedOrder || pending.toSet() != storedOrder.toSet()) pendingOrder = null
    }

    companion object {
        val Saver: Saver<PlaylistPagesState, Any> = listSaver(
            save = { state ->
                listOf(
                    PlaylistEditor.encode(state.editor),
                    state.nameDraft,
                    state.pendingOrder?.let { ArrayList(it) },
                )
            },
            restore = { values ->
                @Suppress("UNCHECKED_CAST")
                PlaylistPagesState(
                    editor = PlaylistEditor.decode(values.getOrNull(0) as? String),
                    nameDraft = values.getOrNull(1) as? String ?: "",
                    pendingOrder = (values.getOrNull(2) as? List<Long>)?.toList(),
                )
            },
        )
    }
}

@Composable
internal fun rememberPlaylistPagesState(): PlaylistPagesState = rememberSaveable(saver = PlaylistPagesState.Saver) { PlaylistPagesState() }
