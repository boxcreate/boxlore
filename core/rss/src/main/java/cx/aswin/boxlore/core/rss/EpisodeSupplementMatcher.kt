package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import java.util.Locale
import kotlin.math.abs

/**
 * Dedupe heuristics for RSS-parsed episodes against a Podcast Index baseline.
 * Same priority as [RssSourceMatcher.findMatchingEpisode]: audio URL → unique title →
 * title + publishedDate within one day.
 */
internal object EpisodeSupplementMatcher {
    private const val ONE_DAY_SECONDS = 24L * 60L * 60L

    fun isPresentInBaseline(
        rssEpisode: RssEpisodeEntity,
        baseline: List<Episode>,
    ): Boolean {
        val rssAudio = rssEpisode.audioUrl.trim().takeIf(String::isNotBlank)
        if (rssAudio != null) {
            if (baseline.any { it.audioUrl.trim() == rssAudio }) return true
        }
        val titleMatches = baseline.filter {
            normalizeText(it.title) == normalizeText(rssEpisode.title)
        }
        if (titleMatches.size == 1) return true
        if (rssEpisode.publishedDate > 0L) {
            val closest = titleMatches.minByOrNull {
                abs(it.publishedDate - rssEpisode.publishedDate)
            }
            if (closest != null &&
                closest.publishedDate > 0L &&
                abs(closest.publishedDate - rssEpisode.publishedDate) <= ONE_DAY_SECONDS
            ) {
                return true
            }
        }
        return false
    }

    private fun normalizeText(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
