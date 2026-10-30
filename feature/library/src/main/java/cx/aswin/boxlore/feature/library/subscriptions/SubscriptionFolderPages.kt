package cx.aswin.boxlore.feature.library.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialog
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.FolderSaveParams
import cx.aswin.boxlore.feature.library.SubscriptionSort

/** What the folder pages show; read from the Library state each time it changes. */
internal data class FolderPagesData(
    val podcasts: List<Podcast>,
    val folders: List<SubscriptionFolder>,
    val sort: SubscriptionSort?,
    val smartOrderIds: List<String>,
    val intraFolderSort: FolderIntraSort,
    val autoOrganizeOn: Boolean,
    val autoOrganizeLook: AutoOrganizeLook,
    val reorderMode: ReorderMode,
)

/** What the folder pages ask Subscriptions to do: open things it owns, or save a change. */
internal interface FolderPagesActions {
    fun openShow(podcastId: String)
    fun showMenu(target: ContextMenuTarget)
    fun addShows(folder: SubscriptionFolder)
    fun openSort()
    fun setReorderMode(mode: ReorderMode)
    fun saveFolderOrder(folderId: String, orderedIds: List<String>)

    /** Asks first; the shows stay in the library. */
    fun deleteFolder(folder: SubscriptionFolder)
    fun createFolder(params: FolderSaveParams, podcastIds: List<String>, move: FolderShowMove?)
    fun updateFolder(folder: SubscriptionFolder)
    fun changeMembership(podcastId: String, change: FolderMembershipChange)
    fun turnOnAutoOrganize(look: AutoOrganizeLook)

    /** Asks first, with what turning it off does and doesn't do. */
    fun turnOffAutoOrganize()
    fun setAutoOrganizeLook(look: AutoOrganizeLook)
    fun applyLookToGenreFolders(look: AutoOrganizeLook)
}

/**
 * The open folder, drawn over the Shows tab as part of Subscriptions so Back from a show returns
 * to it and the mini player stays reachable.
 */
@Composable
internal fun OpenFolderPage(state: FolderPagesState, data: FolderPagesData, actions: FolderPagesActions, bottomPadding: Dp) {
    val folder = data.folders.find { it.id == state.openFolderId } ?: return
    val shows = remember(folder, data.podcasts, data.intraFolderSort, data.sort, data.smartOrderIds) {
        resolveSortedFolderShows(
            folder = folder,
            podcasts = data.podcasts,
            intraFolderSort = data.intraFolderSort,
            sort = data.sort,
            smartOrderIds = data.smartOrderIds,
        )
    }
    val reordering = (data.reorderMode as? ReorderMode.FolderShows)?.folderId == folder.id
    val onBack = { if (reordering) actions.setReorderMode(ReorderMode.Inactive) else state.closeFolder() }
    BackHandler(onBack = onBack)
    SubscriptionFolderPage(
        folder = folder,
        podcasts = shows,
        config = FolderPageConfig(isReorderMode = reordering, orderLabel = data.intraFolderSort.label, bottomPadding = bottomPadding),
        actions = FolderPageActions(
            onBack = onBack,
            onPodcastClick = actions::openShow,
            onPodcastLongClick = { podcast -> actions.showMenu(ContextMenuTarget.FolderPodcast(folder, podcast)) },
            onEditFolder = { state.openEdit(folder) },
            onAddShows = { actions.addShows(folder) },
            onMoreOptions = { actions.showMenu(ContextMenuTarget.Folder(folder)) },
            onOrderClick = actions::openSort,
            onSaveReorder = { orderedIds -> actions.saveFolderOrder(folder.id, orderedIds) },
            onCancelReorder = { actions.setReorderMode(ReorderMode.Inactive) },
        ),
    )
}

/** New folder, Edit folder or Auto-organize: full screen on phones, a framed panel on tablets. */
@Composable
internal fun FolderEditorPage(state: FolderPagesState, data: FolderPagesData, actions: FolderPagesActions) {
    when (val editor = state.editor) {
        null -> Unit
        FolderEditor.NewFolder -> NewFolderEditor(state, data, actions)
        FolderEditor.AutoOrganize -> AutoOrganizeEditor(state, data, actions)
        is FolderEditor.Edit -> {
            val folder = data.folders.find { it.id == editor.folderId } ?: return
            val draft = state.editDraft ?: return
            EditFolderEditor(folder, draft, state, data, actions)
        }
    }
}

@Composable
private fun NewFolderEditor(state: FolderPagesState, data: FolderPagesData, actions: FolderPagesActions) {
    val draft = state.newFolderDraft
    val onBack = { NewFolderFlowLogic.back(draft)?.let { state.newFolderDraft = it } ?: state.closeEditor() }
    EditorDialog(onBack = onBack, dismissOnClickOutside = false) {
        NewFolderFlow(
            draft = draft,
            onDraftChange = { state.newFolderDraft = it },
            data = NewFolderFlowData(
                podcasts = data.podcasts,
                folders = data.folders,
                autoOrganizeOn = data.autoOrganizeOn,
            ),
            actions = NewFolderFlowActions(
                onClose = state::closeEditor,
                onCreate = { params, podcastIds ->
                    actions.createFolder(params, podcastIds, state.newFolderMove)
                    state.closeEditor()
                },
                onTurnOnAutoOrganize = { size, covers ->
                    actions.turnOnAutoOrganize(AutoOrganizeLook(fixedSize = size, tileShowsCovers = covers))
                    state.openAutoOrganize()
                },
                onManageAutoOrganize = state::openAutoOrganize,
            ),
        )
    }
}

@Composable
private fun EditFolderEditor(
    folder: SubscriptionFolder,
    draft: EditFolderDraft,
    state: FolderPagesState,
    data: FolderPagesData,
    actions: FolderPagesActions,
) {
    val shows = remember(folder, data.podcasts) {
        val byId = data.podcasts.associateBy { it.id }
        folder.podcastIds.mapNotNull(byId::get)
    }
    EditorDialog(onBack = state::closeEditor, dismissOnClickOutside = false) { bottomInset ->
        EditFolderPage(
            folder = folder,
            shows = EditFolderShows(inFolder = shows, library = data.podcasts),
            draft = draft,
            onDraftChange = { state.editDraft = it },
            actions = EditFolderActions(
                onClose = state::closeEditor,
                onSave = { saved ->
                    actions.updateFolder(saved)
                    state.closeEditor()
                },
                onAddShows = { actions.addShows(folder) },
                onRemoveShow = { podcast ->
                    actions.changeMembership(podcast.id, FolderMembershipChange(addTo = emptyList(), removeFrom = listOf(folder.id)))
                },
                onDelete = { actions.deleteFolder(folder) },
            ),
            bottomPadding = bottomInset,
        )
    }
}

@Composable
private fun AutoOrganizeEditor(state: FolderPagesState, data: FolderPagesData, actions: FolderPagesActions) {
    EditorDialog(onBack = state::closeEditor, dismissOnClickOutside = true) { bottomInset ->
        AutoOrganizePage(
            enabled = data.autoOrganizeOn,
            podcasts = data.podcasts,
            folders = data.folders,
            look = data.autoOrganizeLook,
            actions = AutoOrganizePageActions(
                onClose = state::closeEditor,
                onLookChange = actions::setAutoOrganizeLook,
                onTurnOn = { actions.turnOnAutoOrganize(data.autoOrganizeLook) },
                onTurnOff = actions::turnOffAutoOrganize,
                onOpenFolder = state::openFolderFromAutoOrganize,
                onApplyToAll = { actions.applyLookToGenreFolders(data.autoOrganizeLook) },
            ),
            bottomPadding = bottomInset,
        )
    }
}

/** Tick the folders a show belongs in; opened from Add to folder on a show outside folders. */
@Composable
internal fun AddToFoldersHost(state: FolderPagesState, data: FolderPagesData, actions: FolderPagesActions) {
    val podcastId = state.addToFoldersPodcastId ?: return
    val podcast = data.podcasts.find { it.id == podcastId } ?: return
    AddToFoldersSheet(
        podcast = podcast,
        folders = data.folders,
        onDone = { change ->
            actions.changeMembership(podcast.id, change)
            state.addToFoldersPodcastId = null
        },
        onNewFolderWithShow = {
            state.addToFoldersPodcastId = null
            state.openNewFolder(withShowId = podcast.id)
        },
        onDismissRequest = { state.addToFoldersPodcastId = null },
    )
}

/**
 * The shared boxlore dialog: it covers the navigation and mini player on phones, so a page's own
 * bottom bar stays reachable, and keeps the same content (drafts, scroll) when the window crosses
 * into or out of the tablet layout. Back goes one step back.
 */
@Composable
private fun EditorDialog(onBack: () -> Unit, dismissOnClickOutside: Boolean, content: @Composable (bottomInset: Dp) -> Unit) {
    BoxloreDialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        panelTag = "folder_editor_panel",
    ) {
        EditorSystemBars()
        val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
        content(if (tablet) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    }
}

/** Status and navigation bar icons that read on the page, without touching the activity's. */
@Composable
private fun EditorSystemBars() {
    val view = LocalView.current
    val darkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
