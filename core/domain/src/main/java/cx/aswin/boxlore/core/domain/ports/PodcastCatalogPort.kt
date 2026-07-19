package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Narrow podcast/episode catalog seam for Info ViewModels (and fakes in tests).
 *
 * Production: [cx.aswin.boxlore.core.catalog.PodcastRepository].
 */
interface PodcastCatalogPort {
    suspend fun getPodcastDetails(feedId: String): Podcast?

    suspend fun getEpisode(episodeId: String): Episode?

    suspend fun getEpisodes(feedId: String): List<Episode>
}
