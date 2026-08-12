package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.Episode
import java.util.Locale

/**
 * Merges Podcast Index pages with feed-only supplement episodes (PI ownership preserved).
 * Catalog list reads merge through `PodcastRepository`; this helper stays shared
 * so Info search union and tests use the same rules.
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
        val piIds = piEpisodes.map { it.id }.toSet()
        val piAudio = piEpisodes.mapNotNull { it.audioUrl.trim().takeIf(String::isNotBlank) }.toSet()
        val piTitles = piEpisodes
            .groupBy { normalizeText(it.title) }
            .mapValues { (_, eps) -> eps }

        val extras = supplements.filterNot { supp ->
            supp.id in piIds ||
                supp.audioUrl.trim().takeIf(String::isNotBlank)?.let { it in piAudio } == true ||
                matchesUniqueOrDatedTitle(supp, piTitles)
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

    fun unionSearchResults(
        networkResults: List<Episode>,
        supplementMatches: List<Episode>,
    ): List<Episode> {
        val seen = linkedSetOf<String>()
        val out = ArrayList<Episode>(networkResults.size + supplementMatches.size)
        for (episode in networkResults + supplementMatches) {
            if (seen.add(episode.id)) out.add(episode)
        }
        return out.sortedWith(
            compareByDescending<Episode> { it.publishedDate }.thenBy { it.id },
        )
    }

    private fun matchesUniqueOrDatedTitle(
        supp: Episode,
        piByTitle: Map<String, List<Episode>>,
    ): Boolean {
        val key = normalizeText(supp.title)
        val titleMatches = piByTitle[key].orEmpty()
        if (titleMatches.size == 1) return true
        if (supp.publishedDate <= 0L) return false
        return titleMatches.any { pi ->
            pi.publishedDate > 0L &&
                kotlin.math.abs(pi.publishedDate - supp.publishedDate) <= ONE_DAY_SECONDS
        }
    }

    private fun normalizeText(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private const val ONE_DAY_SECONDS = 24L * 60L * 60L
}
