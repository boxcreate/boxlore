package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** Summary on the Sort sheet's auto-organize row. */
internal fun autoOrganizeSummary(enabled: Boolean, genreFolderCount: Int): String = when {
    !enabled -> "Off · Sort every show into genre folders"
    genreFolderCount == 1 -> "On · 1 genre folder"
    else -> "On · $genreFolderCount genre folders"
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
internal fun folderPageSummary(showCount: Int, newCount: Int): String {
    val shows = if (showCount == 1) "1 show" else "$showCount shows"
    return when (newCount) {
        0 -> shows
        1 -> "$shows · 1 new episode"
        else -> "$shows · $newCount new episodes"
    }
}

/** The four looks a folder can pick, in the order they are offered. */
internal val FolderLookOptions: List<FolderDisplaySize> = listOf(
    FolderDisplaySize.COMPACT,
    FolderDisplaySize.SHELF,
    FolderDisplaySize.PANEL,
    FolderDisplaySize.SHOWCASE,
)

/** Listener-facing look names; older saved sizes keep their own titles. */
internal fun folderLookName(size: FolderDisplaySize): String = when (size) {
    FolderDisplaySize.COMPACT -> "Tile"
    FolderDisplaySize.SHELF -> "1 row"
    FolderDisplaySize.PANEL -> "2 rows"
    FolderDisplaySize.SHOWCASE -> "3 rows"
    else -> size.title
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
internal fun folderLookDescription(size: FolderDisplaySize): String {
    val place = if (size.isPinnedToTop) "Pinned on top." else "Among your shows."
    return "$place Tap up to ${folderSlotCount(size)} directly."
}

/** Mirrors the size auto-organize picks when the listener lets boxlore choose. */
internal const val AUTO_ORGANIZE_CHOOSE_DESCRIPTION =
    "Each folder gets the look that fits its size. 1–2 shows get a tile, 3–5 get 1 row, 6 or more get 2 rows."

/** Genre step of New folder: shows followed later join on their own. */
internal fun genreAutoAddNote(genre: String?): String =
    "Shows you follow later in ${genre?.trim()?.takeIf { it.isNotEmpty() } ?: "this genre"} are added automatically."

/** Auto: what auto-organize does, said once wherever it is set up. */
internal const val AUTO_ORGANIZE_NOTE =
    "Every genre gets a folder, and shows you follow later are added automatically. A folder you already have for a genre is filled too."
