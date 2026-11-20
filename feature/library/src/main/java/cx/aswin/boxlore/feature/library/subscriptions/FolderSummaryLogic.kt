package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.library.R

/** Summary on the Sort sheet's auto-organize row. */
internal fun autoOrganizeSummary(enabled: Boolean, genreFolderCount: Int): UiText = if (enabled) {
    UiText.plural(R.plurals.library_auto_organize_summary_on, genreFolderCount, genreFolderCount)
} else {
    UiText.res(R.string.library_auto_organize_summary_off)
}

/** Folders that fill themselves from a genre (made by auto-organize or by "One genre"). */
internal fun genreFolders(folders: List<SubscriptionFolder>): List<SubscriptionFolder> = folders.filter { it.isGenreLinked }

/**
 * Where the "Folders" and "Your shows" labels go in the Shows grid, as positions in [folders]
 * (null when no labels show). Labels only show when pinned folders all come before the compact
 * ones, so a label never splits a row; never while folders are being dragged.
 */
internal data class ShowsSectionLabels(val yourShowsBeforeFolderIndex: Int)

internal fun showsSectionLabels(folders: List<SubscriptionFolder>, isFoldersReordering: Boolean): ShowsSectionLabels? {
    if (isFoldersReordering) return null
    val pinnedCount = folders.count { it.displaySize != FolderDisplaySize.COMPACT }
    if (pinnedCount == 0) return null
    val pinnedFirst = folders.take(pinnedCount).all { it.displaySize != FolderDisplaySize.COMPACT }
    return if (pinnedFirst) ShowsSectionLabels(yourShowsBeforeFolderIndex = pinnedCount) else null
}

/** "5 shows", plus "· 2 new episodes" when some have one. */
internal fun folderPageSummary(showCount: Int, newCount: Int): UiText {
    val shows = showCountLabel(showCount)
    return if (newCount == 0) {
        shows
    } else {
        UiText.res(
            R.string.library_folder_summary_with_new,
            shows,
            UiText.plural(R.plurals.library_new_episode_count, newCount, newCount),
        )
    }
}

/** "1 show" / "12 shows". */
internal fun showCountLabel(count: Int): UiText = UiText.plural(R.plurals.library_show_count, count, count)

/** A stored genre as shown: the translated catalog name, or the listener's own genre text. */
internal fun genreDisplayText(genre: String): UiText =
    SUBSCRIPTION_GENRE_CATALOG
        .find { it.value.equals(genre, ignoreCase = true) || it.label.equals(genre, ignoreCase = true) }
        ?.labelRes
        ?.let { UiText.res(it) }
        ?: UiText.raw(genre)

/** The four looks a folder can pick, in the order they are offered. */
internal val FolderLookOptions: List<FolderDisplaySize> = listOf(
    FolderDisplaySize.COMPACT,
    FolderDisplaySize.SHELF,
    FolderDisplaySize.PANEL,
    FolderDisplaySize.SHOWCASE,
)

/** Listener-facing look names; older saved sizes keep their own titles. */
internal fun folderLookName(size: FolderDisplaySize): UiText = when (size) {
    FolderDisplaySize.COMPACT -> UiText.res(R.string.library_folder_look_tile)
    FolderDisplaySize.SHELF -> UiText.plural(R.plurals.library_folder_look_rows, 1, 1)
    FolderDisplaySize.PANEL -> UiText.plural(R.plurals.library_folder_look_rows, 2, 2)
    FolderDisplaySize.SHOWCASE -> UiText.plural(R.plurals.library_folder_look_rows, 3, 3)
    else -> UiText.raw(size.title)
}

/** Covers a folder card shows; with more shows than this, the last spot becomes the +N tile. */
internal fun folderSlotCount(displaySize: FolderDisplaySize): Int = when (displaySize) {
    FolderDisplaySize.COMPACT -> 4
    FolderDisplaySize.WIDE -> 2
    FolderDisplaySize.FEATURED -> 4
    FolderDisplaySize.LARGE -> 6
    FolderDisplaySize.SHELF -> 3
    FolderDisplaySize.PANEL -> 6
    FolderDisplaySize.SHOWCASE -> 9
}

/** "Among your shows. Tap up to 4 directly." / "Pinned on top. Tap up to 3 directly." */
internal fun folderLookDescription(size: FolderDisplaySize): UiText {
    val slots = folderSlotCount(size)
    val id = if (size.isPinnedToTop) R.plurals.library_folder_look_pinned_description else R.plurals.library_folder_look_among_description
    return UiText.plural(id, slots, slots)
}

/** Mirrors the size auto-organize picks when the listener lets boxlore choose. */
internal val AUTO_ORGANIZE_CHOOSE_DESCRIPTION: Int = R.string.library_auto_organize_choose_description

/** Genre step of New folder: shows followed later join on their own. */
internal fun genreAutoAddNote(genre: String?): UiText =
    genre?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { UiText.res(R.string.library_genre_auto_add_note, genreDisplayText(it)) }
        ?: UiText.res(R.string.library_genre_auto_add_note_this_genre)

/** Auto: what auto-organize does, said once wherever it is set up. */
internal val AUTO_ORGANIZE_NOTE: Int = R.string.library_auto_organize_note
