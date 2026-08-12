package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import java.util.Locale
import kotlin.math.abs

/**
 * Dedupe heuristics for RSS-parsed episodes against a Podcast Index baseline.
 * Same priority as [RssSourceMatcher.findMatchingEpisode]: audio URL → unique title
 * (only when dates agree or one date is missing) → title + publishedDate within one day.
 * Empty normalized titles never match.
 */
internal object EpisodeSupplementMatcher {
    private const val ONE_DAY_SECONDS = 24L * 60L * 60L

    fun isPresentInBaseline(
        rssEpisode: RssEpisodeEntity,
        baseline: List<Episode>,
    ): Boolean = findMatchingBaseline(rssEpisode, baseline) != null

    /**
     * Returns the baseline episode that matches [rssEpisode], preferring audio URL,
     * then unique title with a date check, then title + publishedDate within one day.
     */
    fun findMatchingBaseline(
        rssEpisode: RssEpisodeEntity,
        baseline: List<Episode>,
    ): Episode? {
        val rssAudio = rssEpisode.audioUrl.trim().takeIf(String::isNotBlank)
        if (rssAudio != null) {
            baseline.firstOrNull { it.audioUrl.trim() == rssAudio }?.let { return it }
        }
        return matchByTitleAndDate(rssEpisode.title, rssEpisode.publishedDate, baseline)
    }

    fun isDuplicateOf(left: Episode, right: Episode): Boolean {
        if (left.id == right.id) return true
        val leftAudio = left.audioUrl.trim().takeIf(String::isNotBlank)
        val rightAudio = right.audioUrl.trim().takeIf(String::isNotBlank)
        if (leftAudio != null && leftAudio == rightAudio) return true
        return matchByTitleAndDate(left.title, left.publishedDate, listOf(right)) != null
    }

    fun matchByTitleAndDate(
        title: String,
        publishedDate: Long,
        pool: List<Episode>,
    ): Episode? {
        val key = normalizeText(title)
        if (key.isEmpty()) return null
        val titleMatches = pool.filter { normalizeText(it.title) == key }
        if (titleMatches.isEmpty()) return null
        if (titleMatches.size == 1) {
            val only = titleMatches.first()
            if (publishedDate <= 0L || only.publishedDate <= 0L) return only
            return only.takeIf { datesWithinOneDay(publishedDate, only.publishedDate) }
        }
        if (publishedDate <= 0L) return null
        return titleMatches.firstOrNull { datesWithinOneDay(publishedDate, it.publishedDate) }
    }

    private fun datesWithinOneDay(left: Long, right: Long): Boolean =
        left > 0L && right > 0L && abs(left - right) <= ONE_DAY_SECONDS

    private fun normalizeText(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
