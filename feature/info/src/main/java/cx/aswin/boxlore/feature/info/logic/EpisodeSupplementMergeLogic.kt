package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.rss.EpisodeSupplementListMerge
import cx.aswin.boxlore.feature.info.EpisodeSort

/**
 * Feature-facing wrapper around [EpisodeSupplementListMerge] using Info's [EpisodeSort].
 */
object EpisodeSupplementMergeLogic {
    fun merge(
        piEpisodes: List<Episode>,
        supplements: List<Episode>,
        sort: EpisodeSort,
    ): List<Episode> =
        EpisodeSupplementListMerge.merge(
            piEpisodes = piEpisodes,
            supplements = supplements,
            sort = when (sort) {
                EpisodeSort.NEWEST -> EpisodeSupplementListMerge.Sort.NEWEST
                EpisodeSort.OLDEST -> EpisodeSupplementListMerge.Sort.OLDEST
            },
        )

    fun unionSearchResults(
        networkResults: List<Episode>,
        supplementMatches: List<Episode>,
    ): List<Episode> =
        EpisodeSupplementListMerge.unionSearchResults(networkResults, supplementMatches)

    fun sorted(episodes: List<Episode>, sort: EpisodeSort): List<Episode> =
        merge(episodes, emptyList(), sort)
}
