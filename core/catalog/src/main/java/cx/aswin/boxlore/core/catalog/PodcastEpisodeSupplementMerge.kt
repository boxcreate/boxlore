package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.rss.EpisodeSupplementListMerge

/**
 * Single PI + cached-feed merge for [PodcastRepository] episode lists.
 * True `rss:` library rows already own their catalog. Extras are injected only
 * on offset-0 pages so later PI pages do not duplicate feed-only items.
 */
internal object PodcastEpisodeSupplementMerge {
    fun mergePage(
        podcastId: String,
        piEpisodes: List<Episode>,
        supplements: List<Episode>,
        sort: EpisodeSupplementListMerge.Sort,
        injectExtras: Boolean,
    ): List<Episode> {
        if (!injectExtras || podcastId.startsWith("rss:") || supplements.isEmpty()) {
            return piEpisodes
        }
        return EpisodeSupplementListMerge.merge(piEpisodes, supplements, sort)
    }

    fun unionSearch(
        podcastId: String,
        networkResults: List<Episode>,
        supplementMatches: List<Episode>,
    ): List<Episode> {
        if (podcastId.startsWith("rss:") || supplementMatches.isEmpty()) {
            return networkResults
        }
        return EpisodeSupplementListMerge.unionSearchResults(networkResults, supplementMatches)
    }

    fun sortFromQuery(sort: String): EpisodeSupplementListMerge.Sort =
        if (sort == "oldest") {
            EpisodeSupplementListMerge.Sort.OLDEST
        } else {
            EpisodeSupplementListMerge.Sort.NEWEST
        }
}
