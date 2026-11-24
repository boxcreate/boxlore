package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** What a long-press menu is for, by ID: a folder, a show in a folder, or a show outside folders. */
internal data class ContextMenuKey(val kind: Kind, val folderId: String?, val podcastId: String?) {
    enum class Kind { Folder, FolderPodcast, RootPodcast }

    /** The menu target, or null when its folder or show is gone. */
    fun resolve(folders: List<SubscriptionFolder>, podcasts: List<Podcast>): ContextMenuTarget? {
        val folder = folderId?.let { id -> folders.firstOrNull { it.id == id } }
        val podcast = podcastId?.let { id -> podcasts.firstOrNull { it.id == id } }
        return when (kind) {
            Kind.Folder -> folder?.let(ContextMenuTarget::Folder)
            Kind.FolderPodcast -> if (folder != null && podcast != null) ContextMenuTarget.FolderPodcast(folder, podcast) else null
            Kind.RootPodcast -> podcast?.let(ContextMenuTarget::RootPodcast)
        }
    }

    companion object {
        fun of(target: ContextMenuTarget): ContextMenuKey = when (target) {
            is ContextMenuTarget.Folder -> ContextMenuKey(Kind.Folder, target.folder.id, null)
            is ContextMenuTarget.FolderPodcast -> ContextMenuKey(Kind.FolderPodcast, target.folder.id, target.podcast.id)
            is ContextMenuTarget.RootPodcast -> ContextMenuKey(Kind.RootPodcast, null, target.podcast.id)
        }
    }
}

/** Move to another folder: the show, and the folder it leaves (null outside folders). */
internal data class MoveShowKey(val fromFolderId: String?, val podcastId: String)

/**
 * What Subscriptions is in the middle of: the search, an open sheet, menu or question, and a
 * reorder with its unsaved order. It holds IDs, not objects, and lives in saved state, so a text or
 * display size change, a move to another display or the app being closed in the background brings
 * it all back; the screen looks each ID up in the current data and drops one that is gone.
 */
@Stable
@Suppress("LongParameterList") // One parameter per thing the screen can have open.
internal class SubscriptionsScreenState(
    searchQuery: String = "",
    isSearchActive: Boolean = false,
    sortSheetOpen: Boolean = false,
    contextMenu: ContextMenuKey? = null,
    addShowsFolderId: String? = null,
    moveShow: MoveShowKey? = null,
    confirmDeleteFolderId: String? = null,
    confirmUnsubscribeId: String? = null,
    confirmAutoOrganizeOff: Boolean = false,
    reorderMode: ReorderMode = ReorderMode.Inactive,
    tempFolderOrder: List<String> = emptyList(),
    tempRootOrder: List<String> = emptyList(),
) {
    var searchQuery by mutableStateOf(searchQuery)
    var isSearchActive by mutableStateOf(isSearchActive)
    var sortSheetOpen by mutableStateOf(sortSheetOpen)
    var contextMenu by mutableStateOf(contextMenu)
    var addShowsFolderId by mutableStateOf(addShowsFolderId)
    var moveShow by mutableStateOf(moveShow)
    var confirmDeleteFolderId by mutableStateOf(confirmDeleteFolderId)
    var confirmUnsubscribeId by mutableStateOf(confirmUnsubscribeId)
    var confirmAutoOrganizeOff by mutableStateOf(confirmAutoOrganizeOff)
    var reorderMode by mutableStateOf(reorderMode)
    var tempFolderOrder by mutableStateOf(tempFolderOrder)
    var tempRootOrder by mutableStateOf(tempRootOrder)

    fun closeSearch() {
        isSearchActive = false
        searchQuery = ""
    }

    /** Ends a reorder without saving it. */
    fun endReorder() {
        reorderMode = ReorderMode.Inactive
        tempFolderOrder = emptyList()
        tempRootOrder = emptyList()
    }

    /**
     * Drops targets whose folder or show no longer exists, so their sheet or question closes quietly.
     * [folders] is null while the folders haven't loaded yet: then only the shows are checked, and
     * folder targets (and a folder's reorder with its unsaved order) wait.
     */
    fun dropMissing(folders: List<SubscriptionFolder>?, podcasts: List<Podcast>) {
        val podcastIds = podcasts.mapTo(HashSet()) { it.id }
        dropMissingShows(podcastIds)
        if (folders == null) return
        val folderIds = folders.mapTo(HashSet()) { it.id }
        if (contextMenu?.resolve(folders, podcasts) == null) contextMenu = null
        if (addShowsFolderId != null && addShowsFolderId !in folderIds) addShowsFolderId = null
        if (moveShow?.fromFolderId?.let { it !in folderIds } == true) moveShow = null
        if (confirmDeleteFolderId != null && confirmDeleteFolderId !in folderIds) confirmDeleteFolderId = null
        (reorderMode as? ReorderMode.FolderShows)?.let { mode -> if (mode.folderId !in folderIds) endReorder() }
    }

    /** Drops the targets whose show is gone; these don't need the folders. */
    private fun dropMissingShows(podcastIds: Set<String>) {
        if (contextMenu?.podcastId?.let { it !in podcastIds } == true) contextMenu = null
        if (moveShow?.podcastId?.let { it !in podcastIds } == true) moveShow = null
        if (confirmUnsubscribeId != null && confirmUnsubscribeId !in podcastIds) confirmUnsubscribeId = null
    }

    companion object {
        private const val INACTIVE = "inactive"
        private const val FOLDERS = "folders"
        private const val ROOT = "root"
        private const val FOLDER_SHOWS = "folder_shows"

        /** Reorder modes by name (never ordinal), plus the folder for a folder's shows. */
        private fun encode(mode: ReorderMode): List<String> = when (mode) {
            ReorderMode.Inactive -> listOf(INACTIVE)
            ReorderMode.Folders -> listOf(FOLDERS)
            ReorderMode.RootShows -> listOf(ROOT)
            is ReorderMode.FolderShows -> listOf(FOLDER_SHOWS, mode.folderId, mode.folderName)
        }

        private fun decode(saved: List<String>): ReorderMode = when (saved.firstOrNull()) {
            FOLDERS -> ReorderMode.Folders
            ROOT -> ReorderMode.RootShows
            FOLDER_SHOWS -> ReorderMode.FolderShows(saved.getOrElse(1) { "" }, saved.getOrElse(2) { "" })
            else -> ReorderMode.Inactive
        }

        @Suppress("UNCHECKED_CAST")
        val Saver: Saver<SubscriptionsScreenState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.searchQuery,
                    state.isSearchActive,
                    state.sortSheetOpen,
                    state.contextMenu?.let { listOf(it.kind.name, it.folderId, it.podcastId) },
                    state.addShowsFolderId,
                    state.moveShow?.let { listOf(it.fromFolderId, it.podcastId) },
                    state.confirmDeleteFolderId,
                    state.confirmUnsubscribeId,
                    state.confirmAutoOrganizeOff,
                    encode(state.reorderMode),
                    state.tempFolderOrder,
                    state.tempRootOrder,
                )
            },
            restore = { saved ->
                SubscriptionsScreenState(
                    searchQuery = saved[0] as String,
                    isSearchActive = saved[1] as Boolean,
                    sortSheetOpen = saved[2] as Boolean,
                    contextMenu = (saved[3] as List<String?>?)?.let { key ->
                        ContextMenuKey.Kind.entries.firstOrNull { it.name == key[0] }?.let { kind -> ContextMenuKey(kind, key[1], key[2]) }
                    },
                    addShowsFolderId = saved[4] as String?,
                    moveShow = (saved[5] as List<String?>?)?.let { key -> key[1]?.let { podcastId -> MoveShowKey(key[0], podcastId) } },
                    confirmDeleteFolderId = saved[6] as String?,
                    confirmUnsubscribeId = saved[7] as String?,
                    confirmAutoOrganizeOff = saved[8] as Boolean,
                    reorderMode = decode(saved[9] as List<String>),
                    tempFolderOrder = saved[10] as List<String>,
                    tempRootOrder = saved[11] as List<String>,
                )
            },
        )
    }
}

@Composable
internal fun rememberSubscriptionsScreenState(): SubscriptionsScreenState =
    rememberSaveable(saver = SubscriptionsScreenState.Saver) { SubscriptionsScreenState() }
