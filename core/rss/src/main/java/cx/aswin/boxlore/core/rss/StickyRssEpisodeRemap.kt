package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import cx.aswin.boxlore.core.model.Episode

/** Reuses stored true-RSS episodeIds on refresh so history / downloads stay valid. */
object StickyRssEpisodeRemap {
    data class Catalog(
        val episodes: List<RssEpisodeEntity>,
        val latestEpisode: Episode?,
    )

    fun prepare(
        parsed: List<RssEpisodeEntity>,
        existing: List<RssEpisodeIdentity>,
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): Catalog {
        val episodes = remap(parsed, existing)
        return Catalog(
            episodes = episodes,
            latestEpisode =
                episodes.firstOrNull()?.toEpisode(
                    podcastTitle = podcastTitle,
                    podcastImageUrl = podcastImageUrl,
                    podcastGenre = podcastGenre,
                    podcastArtist = podcastArtist,
                ),
        )
    }

    fun remap(
        parsed: List<RssEpisodeEntity>,
        existing: List<RssEpisodeIdentity>,
    ): List<RssEpisodeEntity> {
        val existingByKey = linkedMapOf<String, String>()
        for (row in existing) {
            val key = StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl) ?: continue
            existingByKey.putIfAbsent(key, row.episodeId)
        }
        val seen = mutableSetOf<String>()
        return parsed.mapNotNull { episode ->
            val key =
                StickyEpisodeIdentity.requireCatalogKey(episode.guid, episode.audioUrl)
                    ?: return@mapNotNull null
            if (!StickyEpisodeIdentity.firstWinsExisting(seen, key)) return@mapNotNull null
            val stickyId = existingByKey[key] ?: episode.episodeId
            episode.copy(episodeId = stickyId)
        }
    }
}
