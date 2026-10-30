package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodcastGenres
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** One genre auto-organize would fill: a new folder, or one the listener already has. */
internal data class AutoOrganizePreviewRow(
    val genre: String,
    val showCount: Int,
    val existingFolderName: String?,
)

/** What turning auto-organize on would do, for the preview before it runs. */
internal data class AutoOrganizePreview(
    val rows: List<AutoOrganizePreviewRow>,
    /** Shows with no genre stay in the main grid. */
    val showsWithoutGenre: Int,
) {
    val newFolders: List<AutoOrganizePreviewRow> get() = rows.filter { it.existingFolderName == null }
    val existingFolders: List<AutoOrganizePreviewRow> get() = rows.filter { it.existingFolderName != null }
}

/**
 * Groups shows the way `RoomFolderRepository.autoOrganizeSubscribedShows` does: the custom genre,
 * else the first listed genre, canonicalised, with Technology saved as Tech; a genre that already
 * has a folder (by its genre link or name) fills that folder instead of making a new one.
 */
internal fun autoOrganizePreview(podcasts: List<Podcast>, folders: List<SubscriptionFolder>): AutoOrganizePreview {
    val counts = linkedMapOf<String, Int>()
    var withoutGenre = 0
    podcasts.forEach { podcast ->
        val genre = autoOrganizeGenre(podcast)
        if (genre == null) withoutGenre++ else counts[genre] = (counts[genre] ?: 0) + 1
    }
    val rows = counts.map { (genre, count) ->
        AutoOrganizePreviewRow(
            genre = genre,
            showCount = count,
            existingFolderName = folders.firstOrNull { folderMatchesAutoGenre(it, genre) }?.name,
        )
    }.sortedWith(compareByDescending<AutoOrganizePreviewRow> { it.showCount }.thenBy { it.genre })
    return AutoOrganizePreview(rows = rows, showsWithoutGenre = withoutGenre)
}

/** Same rule as `RoomFolderRepository` grouping: the placeholder genre gets no folder. */
private fun autoOrganizeGenre(podcast: Podcast): String? {
    val raw = podcast.customGenre?.trim()?.takeIf { it.isNotEmpty() }
        ?: podcast.genre.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && !PodcastGenres.isPlaceholder(it) }
        ?: return null
    val canonical = PodcastGenres.canonicalize(raw) ?: raw.replaceFirstChar { it.uppercase() }
    return if (canonical.equals("Technology", ignoreCase = true)) "Tech" else canonical
}

private fun folderMatchesAutoGenre(folder: SubscriptionFolder, genre: String): Boolean {
    val candidates = listOfNotNull(folder.linkedGenre, folder.name)
    return candidates.any { candidate ->
        val trimmed = candidate.trim()
        trimmed.equals(genre, ignoreCase = true) ||
            (genre == "Tech" && trimmed.equals("Technology", ignoreCase = true)) ||
            PodcastGenres.canonicalize(trimmed)?.equals(genre, ignoreCase = true) == true
    }
}

/** The look boxlore picks for a genre folder when the listener lets it choose (as the repository does). */
internal fun autoOrganizeSizeFor(showCount: Int): FolderDisplaySize = when {
    showCount <= 2 -> FolderDisplaySize.COMPACT
    showCount <= 5 -> FolderDisplaySize.SHELF
    else -> FolderDisplaySize.PANEL
}

/** The look "Apply to all" gives one genre folder: tiles chosen by size always show covers. */
internal fun autoOrganizeLookFor(showCount: Int, look: AutoOrganizeLook): Pair<FolderDisplaySize, Boolean> {
    val size = look.fixedSize ?: autoOrganizeSizeFor(showCount)
    val covers = size == FolderDisplaySize.COMPACT && (look.fixedSize == null || look.tileShowsCovers)
    return size to covers
}

/** Reads a saved look size; anything unknown means "let boxlore choose". */
internal fun autoOrganizeLookFromSaved(sizeName: String?, tileCovers: Boolean): AutoOrganizeLook = AutoOrganizeLook(
    fixedSize = sizeName?.let { name -> FolderDisplaySize.entries.firstOrNull { it.name == name } },
    tileShowsCovers = tileCovers,
)
