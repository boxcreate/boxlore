package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** A page that changes folders, drawn over Subscriptions and the open folder. One at a time. */
internal sealed interface FolderEditor {
    data object NewFolder : FolderEditor
    data class Edit(val folderId: String) : FolderEditor
    data object AutoOrganize : FolderEditor

    companion object {
        fun encode(editor: FolderEditor): String = when (editor) {
            NewFolder -> "new"
            AutoOrganize -> "auto"
            is Edit -> "edit:${editor.folderId}"
        }

        fun decode(value: String): FolderEditor? = when {
            value == "new" -> NewFolder
            value == "auto" -> AutoOrganize
            value.startsWith("edit:") -> Edit(value.removePrefix("edit:"))
            else -> null
        }
    }
}

/** New folder opened from Move to another folder: the show leaves [fromFolderId] if it stays picked. */
internal data class FolderShowMove(val podcastId: String, val fromFolderId: String)

/**
 * Which folder pages are open over Subscriptions and what they hold: the open folder, the editor
 * on top of it, unsaved drafts, and the show the Add to folders sheet is for. All of it is saved
 * state, so it survives rotation, resizing, folding and recreation, and the open folder is still
 * there after Back from a show.
 */
@Stable
internal class FolderPagesState(
    openFolderId: String? = null,
    editor: FolderEditor? = null,
    newFolderDraft: NewFolderDraft = NewFolderDraft(),
    newFolderMove: FolderShowMove? = null,
    editDraft: EditFolderDraft? = null,
    addToFoldersPodcastId: String? = null,
) {
    var openFolderId by mutableStateOf(openFolderId)
        private set
    var editor by mutableStateOf(editor)
        private set
    var newFolderDraft by mutableStateOf(newFolderDraft)

    var newFolderMove by mutableStateOf(newFolderMove)
        private set
    var editDraft by mutableStateOf(editDraft)
    var addToFoldersPodcastId by mutableStateOf(addToFoldersPodcastId)

    fun openFolder(folderId: String) {
        openFolderId = folderId
    }

    fun closeFolder() {
        openFolderId = null
    }

    /** New folder starts fresh; [withShowId] arrives already picked. */
    fun openNewFolder(withShowId: String? = null, movesFrom: String? = null) {
        closeEditor()
        newFolderDraft = NewFolderDraft(pickedIds = listOfNotNull(withShowId))
        newFolderMove = if (withShowId != null && movesFrom != null) FolderShowMove(withShowId, movesFrom) else null
        editor = FolderEditor.NewFolder
    }

    fun openEdit(folder: SubscriptionFolder) {
        closeEditor()
        editDraft = EditFolderDraft.from(folder)
        editor = FolderEditor.Edit(folder.id)
    }

    fun openAutoOrganize() {
        closeEditor()
        editor = FolderEditor.AutoOrganize
    }

    /** Drafts belong to the editor that made them, so they go with it. */
    fun closeEditor() {
        editor = null
        editDraft = null
        newFolderMove = null
    }

    /** A folder opened from Auto-organize replaces it, so the folder isn't hidden behind it. */
    fun openFolderFromAutoOrganize(folderId: String) {
        closeEditor()
        openFolder(folderId)
    }

    /** Every page of a deleted folder closes. */
    fun onFolderDeleted(folderId: String) {
        if (openFolderId == folderId) openFolderId = null
        if (editor == FolderEditor.Edit(folderId)) closeEditor()
    }

    /** Before reordering folders or shows on the Shows tab, which these pages would cover. */
    fun closeAll() {
        closeEditor()
        openFolderId = null
    }

    companion object {
        val Saver: Saver<FolderPagesState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.openFolderId,
                    state.editor?.let(FolderEditor::encode),
                    with(NewFolderDraftSaver) { save(state.newFolderDraft) },
                    state.newFolderMove?.podcastId,
                    state.newFolderMove?.fromFolderId,
                    state.editDraft?.let { draft -> with(EditFolderDraftSaver) { save(draft) } },
                    state.addToFoldersPodcastId,
                )
            },
            restore = { saved ->
                FolderPagesState(
                    openFolderId = saved[0] as String?,
                    editor = (saved[1] as String?)?.let(FolderEditor::decode),
                    newFolderDraft = saved[2]?.let(NewFolderDraftSaver::restore) ?: NewFolderDraft(),
                    newFolderMove = (saved[3] as String?)?.let { podcastId ->
                        (saved[4] as String?)?.let { fromFolderId -> FolderShowMove(podcastId, fromFolderId) }
                    },
                    editDraft = saved[5]?.let(EditFolderDraftSaver::restore),
                    addToFoldersPodcastId = saved[6] as String?,
                )
            },
        )
    }
}

@Composable
internal fun rememberFolderPagesState(): FolderPagesState = rememberSaveable(saver = FolderPagesState.Saver) { FolderPagesState() }
