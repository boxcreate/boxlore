package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode

/**
 * `/episodes/match` as [Episode]s for [PodcastRepository.loadPiEpisodesForSupplement], or null to
 * use the page path: an HTTP or network error, or an empty list (the page path confirms a show with
 * no episodes, so a misbehaving route can never leave a feed compared with nothing).
 *
 * LEGACY(own-ids): the extras of the Podcast Index show page. Remove at sunset step S7
 * (MIGRATION.md §6) once show pages read feeds for every listener.
 */
internal fun PodcastRepository.loadMatchList(resolvedId: String): List<Episode>? = try {
    val response = api.getEpisodeMatchList(publicKey, resolvedId, MAX_MATCH_BASELINE).execute()
    val body = response.body()
    if (response.isSuccessful && body != null) {
        body.items.mapNotNull { it.toBaselineEpisode(resolvedId) }.takeIf { it.isNotEmpty() }
    } else {
        null
    }
} catch (e: java.io.IOException) {
    android.util.Log.w("PodcastRepository", "match list failed for $resolvedId; using the page path", e)
    null
} catch (e: IllegalArgumentException) {
    // An unreadable body (the JSON converter's SerializationException is one).
    android.util.Log.w("PodcastRepository", "match list unreadable for $resolvedId; using the page path", e)
    null
}
