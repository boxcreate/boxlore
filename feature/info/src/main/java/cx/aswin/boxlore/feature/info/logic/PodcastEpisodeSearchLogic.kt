package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

/** Opening or clearing search retains the same loaded cards; only an active query can replace them. */
internal fun podcastSearchDisplayEpisodes(active: Boolean, query: String, results: List<Episode>?, episodes: List<Episode>): List<Episode> =
    (if (active && query.isNotBlank()) results ?: episodes else episodes).distinctBy { it.id }

internal fun podcastSearchHasNoResults(active: Boolean, query: String, searching: Boolean, results: List<Episode>?, failed: Boolean = false): Boolean =
    active && query.isNotBlank() && !searching && !failed && results?.isEmpty() == true

/** A search that couldn't read the show's episodes: shown with Try again instead of "no matches". */
internal fun podcastSearchShowsFailure(active: Boolean, query: String, searching: Boolean, failed: Boolean): Boolean =
    active && query.isNotBlank() && !searching && failed

/**
 * How many of the newest episodes a finished search covered, to say so above its results, when the
 * show lists more than the server pages through; null when it covered every episode or isn't shown.
 */
internal fun podcastSearchCoverage(active: Boolean, query: String, searching: Boolean, failed: Boolean, searchedNewest: Int?): Int? =
    searchedNewest.takeIf { active && query.isNotBlank() && !searching && !failed }

/** Late responses must not replace a cleared query or a different show's episodes. */
internal fun podcastSearchResponseIsCurrent(requestQuery: String, requestFeedId: String, currentQuery: String, currentFeedId: String): Boolean =
    requestQuery.isNotBlank() && requestQuery == currentQuery && requestFeedId == currentFeedId
