package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.Episode
import kotlin.math.abs

/**
 * Rematch a history / download / queue id to a catalog row **only** when the
 * stored id no longer resolves. Guid, then enclosure, then title+date.
 */
object LocalCatalogOrphanRematch {
    private const val DATE_WINDOW_SECONDS = 48L * 60L * 60L

    data class Candidate(
        val episode: Episode,
        val guid: String?,
    )

    fun shouldRematch(resolved: Episode?): Boolean = resolved == null

    @JvmName("rematchEpisodes")
    fun rematch(
        resolved: Episode?,
        candidates: List<Episode>,
        guid: String?,
        enclosureUrl: String?,
        title: String?,
        publishedDate: Long?,
        candidateGuids: Map<String, String> = emptyMap(),
    ): Episode? {
        val keyed =
            candidates.map { episode ->
                Candidate(episode = episode, guid = candidateGuids[episode.id])
            }
        return rematch(resolved, keyed, guid, enclosureUrl, title, publishedDate)
    }

    fun rematch(
        resolved: Episode?,
        candidates: List<Candidate>,
        guid: String?,
        enclosureUrl: String?,
        title: String?,
        publishedDate: Long?,
    ): Episode? {
        if (!shouldRematch(resolved)) return null
        val wantedGuid = guid?.trim().orEmpty()
        if (wantedGuid.isNotEmpty()) {
            candidates
                .filter { it.guid?.trim() == wantedGuid }
                .singleOrNull()
                ?.let { return it.episode }
        }
        val enclosure = enclosureUrl?.trim().orEmpty()
        if (enclosure.isNotEmpty()) {
            candidates
                .filter { it.episode.audioUrl.trim() == enclosure }
                .singleOrNull()
                ?.let { return it.episode }
        }
        val wantedTitle = title?.trim().orEmpty()
        val wantedDate = publishedDate ?: return null
        if (wantedTitle.isEmpty()) return null
        return candidates
            .filter { candidate ->
                candidate.episode.title.trim() == wantedTitle &&
                    abs(candidate.episode.publishedDate - wantedDate) <= DATE_WINDOW_SECONDS
            }.singleOrNull()
            ?.episode
    }
}
