package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

/**
 * Pure rules for archived episodes on the show page. An archived episode leaves the list without
 * being marked played; the menu's Show archived brings archived episodes back into the list,
 * marked, and a search with a query always finds them.
 */
internal object EpisodeArchiveLogic {
    /**
     * The episodes the list shows: [raw] (the page or its search results), without finished
     * episodes when [hideCompleted] is on and without archived ones unless [showArchived]. A
     * search with a query ([searching]) shows every result, finished or archived.
     */
    fun listEpisodes(
        raw: List<Episode>,
        searching: Boolean,
        hideCompleted: Boolean,
        completedIds: Set<String>,
        showArchived: Boolean,
        archivedIds: Set<String>,
    ): List<Episode> {
        if (searching) return raw
        val hideArchived = !showArchived && archivedIds.isNotEmpty()
        if (!hideCompleted && !hideArchived) return raw
        return raw.filter { episode ->
            !(hideCompleted && episode.id in completedIds) && !(hideArchived && episode.id in archivedIds)
        }
    }

    /** True when the list leaves [episodeId] out, so a range or visible selection skips it too. */
    fun isHidden(episodeId: String, hideCompleted: Boolean, completedIds: Set<String>, showArchived: Boolean, archivedIds: Set<String>): Boolean =
        (hideCompleted && episodeId in completedIds) || (!showArchived && episodeId in archivedIds)

    /** The selection toolbar offers Unarchive only when every selected episode is archived. */
    fun shouldUnarchive(selected: List<Episode>, archivedIds: Set<String>): Boolean =
        selected.isNotEmpty() && selected.all { it.id in archivedIds }

    /** The episodes of [selected] that [archive] changes (once each): Undo puts back exactly these. */
    fun changedBy(selected: List<Episode>, archive: Boolean, archivedIds: Set<String>): List<Episode> =
        selected.distinctBy(Episode::id).filter { (it.id in archivedIds) != archive }

    /** How many of the page's loaded [episodes] are archived, for Show archived (N). */
    fun archivedCount(episodes: List<Episode>, archivedIds: Set<String>): Int =
        if (archivedIds.isEmpty()) 0 else episodes.distinctBy(Episode::id).count { it.id in archivedIds }
}
