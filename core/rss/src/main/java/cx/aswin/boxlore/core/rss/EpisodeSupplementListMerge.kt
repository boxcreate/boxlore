package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.Episode

/**
 * Merges Podcast Index pages with feed-only supplement episodes (PI ownership preserved).
 * Catalog list reads merge through `PodcastRepository`; this helper stays shared
 * so Info search union and tests use the same rules as [EpisodeSupplementMatcher].
 */
object EpisodeSupplementListMerge {
    enum class Sort {
        NEWEST,
        OLDEST,
    }

    fun merge(
        piEpisodes: List<Episode>,
        supplements: List<Episode>,
        sort: Sort,
    ): List<Episode> {
        val extras = supplements.filterNot { supp ->
            piEpisodes.any { pi -> EpisodeSupplementMatcher.isDuplicateOf(supp, pi) }
        }

        val combined = piEpisodes + extras
        return when (sort) {
            Sort.NEWEST -> combined.sortedWith(
                compareByDescending<Episode> { it.publishedDate }.thenBy { it.id },
            )
            Sort.OLDEST -> combined.sortedWith(
                compareBy<Episode> { it.publishedDate }.thenByDescending { it.id },
            )
        }
    }

    /**
     * Unions two search result lists. [preferred] wins when the same episode appears
     * in both (id, audio URL, or dated title). Callers that want feed extras to win
     * should pass supplement matches as [preferred].
     */
    fun unionSearchResults(
        preferred: List<Episode>,
        fallback: List<Episode>,
    ): List<Episode> {
        val out = ArrayList<Episode>(preferred.size + fallback.size)
        for (episode in preferred + fallback) {
            if (out.none { EpisodeSupplementMatcher.isDuplicateOf(it, episode) }) {
                out.add(episode)
            }
        }
        return out.sortedWith(
            compareByDescending<Episode> { it.publishedDate }.thenBy { it.id },
        )
    }
}
