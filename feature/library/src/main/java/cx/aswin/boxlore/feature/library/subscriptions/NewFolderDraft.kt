package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.PodcastGenres
import cx.aswin.boxlore.feature.library.FolderEditLogic
import cx.aswin.boxlore.feature.library.FolderSaveParams

/** What goes in a new folder: shows the listener picks, one genre, or every genre (auto-organize). */
internal enum class NewFolderMode { PickShows, OneGenre, AllGenres }

/**
 * A folder being made in the New folder flow. It lives in saved state, so the step and every pick
 * survive rotation, resizing and folding. Pick shows has three steps (shows, name, look); One genre
 * has two (genre and name, look); All genres has one, which turns on auto-organize.
 */
internal data class NewFolderDraft(
    val mode: NewFolderMode = NewFolderMode.PickShows,
    val step: Int = 0,
    val pickedIds: List<String> = emptyList(),
    val search: String = "",
    val genre: String? = null,
    val name: String = "",
    val nameEdited: Boolean = false,
    val icon: String? = null,
    val iconChosen: Boolean = false,
    val size: FolderDisplaySize = FolderDisplaySize.SHELF,
    val tileShowsCovers: Boolean = true,
    /** All genres: null lets boxlore choose each folder's look; otherwise every genre folder gets this one. */
    val autoFixedLook: FolderDisplaySize? = null,
) {
    val stepCount: Int
        get() = when (mode) {
            NewFolderMode.PickShows -> 3
            NewFolderMode.OneGenre -> 2
            NewFolderMode.AllGenres -> 1
        }

    val isLastStep: Boolean get() = step >= stepCount - 1
}

internal object NewFolderFlowLogic {
    fun withMode(draft: NewFolderDraft, mode: NewFolderMode): NewFolderDraft =
        if (draft.mode == mode) draft else draft.copy(mode = mode, step = 0)

    fun togglePick(draft: NewFolderDraft, podcastId: String): NewFolderDraft =
        draft.copy(pickedIds = if (podcastId in draft.pickedIds) draft.pickedIds - podcastId else draft.pickedIds + podcastId)

    /** Picking a genre names the folder after it and takes its icon, unless the listener set those. */
    fun withGenre(draft: NewFolderDraft, genre: String, genreIconKey: String?): NewFolderDraft = draft.copy(
        genre = genre,
        name = if (draft.nameEdited) draft.name else genre,
        icon = if (draft.iconChosen) draft.icon else genreIconKey,
    )

    /** A typed name that matches a genre suggests its icon, unless the listener chose one. */
    fun withName(draft: NewFolderDraft, name: String, matchingIconKey: String?): NewFolderDraft = draft.copy(
        name = name,
        nameEdited = true,
        icon = if (draft.iconChosen) draft.icon else matchingIconKey,
    )

    fun withIcon(draft: NewFolderDraft, iconKey: String): NewFolderDraft = draft.copy(icon = iconKey, iconChosen = true)

    fun canGoNext(draft: NewFolderDraft): Boolean = when (draft.mode) {
        NewFolderMode.PickShows -> when (draft.step) {
            0 -> draft.pickedIds.isNotEmpty()
            1 -> draft.name.isNotBlank()
            else -> true
        }
        NewFolderMode.OneGenre -> draft.step > 0 || (draft.genre != null && draft.name.isNotBlank())
        NewFolderMode.AllGenres -> true
    }

    fun next(draft: NewFolderDraft): NewFolderDraft =
        if (draft.isLastStep || !canGoNext(draft)) draft else draft.copy(step = draft.step + 1)

    /** One step back, or null on the first step (Back then closes the flow). */
    fun back(draft: NewFolderDraft): NewFolderDraft? = if (draft.step == 0) null else draft.copy(step = draft.step - 1)

    /** Shows a genre folder fills from; hand-picked folders have none, so nothing joins on its own. */
    fun saveParams(draft: NewFolderDraft): FolderSaveParams = FolderEditLogic.resolveFinalSaveParams(
        name = draft.name,
        icon = draft.icon,
        displaySize = draft.size,
        autoSync = draft.mode == NewFolderMode.OneGenre,
        linkedGenre = draft.genre.orEmpty(),
        showPodcastGrid = draft.tileShowsCovers,
    )

    /** Folders named after a genre fill themselves from it (a folder rule kept as is). */
    fun nameIsGenre(name: String): Boolean = PodcastGenres.canonicalize(name.trim()) != null ||
        name.trim().equals("Tech", ignoreCase = true)

    /** "Technology" is saved as "Tech". */
    fun savedNameNote(name: String): String? = if (FolderEditLogic.isTechnologyDisallowed(name)) "Saved as Tech" else null
}

internal val NewFolderDraftSaver: Saver<NewFolderDraft, Any> = listSaver(
    save = { draft ->
        listOf(
            draft.mode.name,
            draft.step,
            ArrayList(draft.pickedIds),
            draft.search,
            draft.genre,
            draft.name,
            draft.nameEdited,
            draft.icon,
            draft.iconChosen,
            draft.size.name,
            draft.tileShowsCovers,
            draft.autoFixedLook?.name,
        )
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        NewFolderDraft(
            mode = NewFolderMode.valueOf(saved[0] as String),
            step = saved[1] as Int,
            pickedIds = (saved[2] as ArrayList<String>).toList(),
            search = saved[3] as String,
            genre = saved[4] as String?,
            name = saved[5] as String,
            nameEdited = saved[6] as Boolean,
            icon = saved[7] as String?,
            iconChosen = saved[8] as Boolean,
            size = FolderDisplaySize.valueOf(saved[9] as String),
            tileShowsCovers = saved[10] as Boolean,
            autoFixedLook = (saved[11] as String?)?.let(FolderDisplaySize::valueOf),
        )
    },
)
