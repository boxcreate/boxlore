package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode

/** Maps a parsed publisher feed onto sticky local catalog rows. */
object LocalEpisodeCatalogPersist {
    fun toLocalEpisodes(
        podcastIndexId: String,
        rssNamespaceId: String,
        parsed: List<RssEpisodeEntity>,
        existing: List<LocalEpisodeIdentity>,
        piBaseline: List<Episode>?,
        channelImageUrl: String?,
        showImageUrl: String?,
    ): List<LocalEpisodeEntity> {
        val existingByKey = linkedMapOf<String, String>()
        for (row in existing) {
            val key = StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl) ?: continue
            existingByKey.putIfAbsent(key, row.episodeId)
        }
        val seen = mutableSetOf<String>()
        return parsed.mapNotNull { rss ->
            mapOne(
                podcastIndexId = podcastIndexId,
                rssNamespaceId = rssNamespaceId,
                rss = rss,
                existingByKey = existingByKey,
                seen = seen,
                piBaseline = piBaseline,
                channelImageUrl = channelImageUrl,
                showImageUrl = showImageUrl,
            )
        }
    }

    private fun mapOne(
        podcastIndexId: String,
        rssNamespaceId: String,
        rss: RssEpisodeEntity,
        existingByKey: Map<String, String>,
        seen: MutableSet<String>,
        piBaseline: List<Episode>?,
        channelImageUrl: String?,
        showImageUrl: String?,
    ): LocalEpisodeEntity? {
        val key = StickyEpisodeIdentity.requireCatalogKey(rss.guid, rss.audioUrl) ?: return null
        if (!StickyEpisodeIdentity.firstWinsExisting(seen, key)) return null
        val existingId = existingByKey[key]
        val piMatchId =
            if (existingId == null && piBaseline != null) {
                EpisodeSupplementMatcher.findMatchingBaseline(rss, piBaseline)?.id
            } else {
                null
            }
        val episodeId =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = existingId,
                piMatchId = piMatchId,
                rssNamespaceId = rssNamespaceId,
                guid = rss.guid,
                enclosureUrl = rss.audioUrl,
                publishedDate = rss.publishedDate,
                title = rss.title,
            ) ?: return null
        return LocalEpisodeEntity(
            episodeId = episodeId,
            podcastId = podcastIndexId,
            guid = key,
            title = rss.title,
            description = rss.description,
            audioUrl = rss.audioUrl,
            imageUrl = EpisodeSupplementArtworkLogic.resolvedImageUrl(
                itemImageUrl = rss.imageUrl,
                channelImageUrl = channelImageUrl,
                showImageUrl = showImageUrl,
            ),
            duration = rss.duration,
            publishedDate = rss.publishedDate,
            chaptersUrl = rss.chaptersUrl,
            transcriptUrl = rss.transcriptUrl,
            transcripts = rss.transcripts,
            persons = rss.persons,
            seasonNumber = rss.seasonNumber,
            episodeNumber = rss.episodeNumber,
            episodeType = rss.episodeType,
            enclosureType = rss.enclosureType,
        )
    }
}
