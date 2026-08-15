package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.Episode
import kotlin.math.abs

/**
 * Rematch a history / download / queue id to a catalog row **only** when the
 * stored id no longer resolves. Guid, then enclosure, then title+date.
 */
object LocalCatalogOrphanRematch {
    private const val DATE_WINDOW_MS = 48L * 60L * 60L * 1000L

    fun shouldRematch(resolved: Episode?): Boolean = resolved == null

    fun rematch(
        resolved: Episode?,
        candidates: List<Episode>,
        guid: String?,
        enclosureUrl: String?,
        title: String?,
        publishedDate: Long?,
    ): Episode? {
        if (!shouldRematch(resolved)) return null
        val key = StickyEpisodeIdentity.catalogKey(guid, enclosureUrl)
        if (!key.isNullOrBlank()) {
            candidates.firstOrNull { it.audioUrl.trim() == key }?.let { return it }
        }
        val enclosure = enclosureUrl?.trim().orEmpty()
        if (enclosure.isNotEmpty()) {
            candidates.firstOrNull { it.audioUrl.trim() == enclosure }?.let { return it }
        }
        val wantedTitle = title?.trim().orEmpty()
        val wantedDate = publishedDate ?: return null
        if (wantedTitle.isEmpty()) return null
        return candidates.firstOrNull { candidate ->
            candidate.title.trim() == wantedTitle &&
                abs(candidate.publishedDate - wantedDate) <= DATE_WINDOW_MS
        }
    }
}
