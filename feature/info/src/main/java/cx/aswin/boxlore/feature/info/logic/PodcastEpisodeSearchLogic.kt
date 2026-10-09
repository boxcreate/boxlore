package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

/** Opening or clearing search retains the same loaded cards; only an active query can replace them. */
internal fun podcastSearchDisplayEpisodes(active: Boolean, query: String, results: List<Episode>?, episodes: List<Episode>): List<Episode> =
    if (active && query.isNotBlank()) results ?: episodes else episodes

internal fun podcastSearchHasNoResults(active: Boolean, query: String, searching: Boolean, results: List<Episode>?): Boolean =
    active && query.isNotBlank() && !searching && results?.isEmpty() == true

/** Late responses must not replace a cleared query or a different show's episodes. */
internal fun podcastSearchResponseIsCurrent(requestQuery: String, requestFeedId: String, currentQuery: String, currentFeedId: String): Boolean =
    requestQuery.isNotBlank() && requestQuery == currentQuery && requestFeedId == currentFeedId
