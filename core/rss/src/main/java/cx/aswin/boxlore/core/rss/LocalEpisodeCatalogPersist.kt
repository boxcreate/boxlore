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
        val occupiedIds = existing.map { it.episodeId }.toMutableSet()
        for (row in existing) {
            val key = StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl) ?: continue
            existingByKey.putIfAbsent(key, row.episodeId)
        }
        val seen = mutableSetOf<String>()
        val ctx =
            PersistContext(
                podcastIndexId = podcastIndexId,
                rssNamespaceId = rssNamespaceId,
                existingByKey = existingByKey,
                occupiedIds = occupiedIds,
                seen = seen,
                piBaseline = piBaseline,
                channelImageUrl = channelImageUrl,
                showImageUrl = showImageUrl,
            )
        return parsed.mapNotNull { rss -> mapOne(ctx, rss) }
    }

    private data class PersistContext(
        val podcastIndexId: String,
        val rssNamespaceId: String,
        val existingByKey: Map<String, String>,
        val occupiedIds: MutableSet<String>,
        val seen: MutableSet<String>,
        val piBaseline: List<Episode>?,
        val channelImageUrl: String?,
        val showImageUrl: String?,
    )

    private fun mapOne(ctx: PersistContext, rss: RssEpisodeEntity,): LocalEpisodeEntity? {
        val key = StickyEpisodeIdentity.requireCatalogKey(rss.guid, rss.audioUrl) ?: return null
        if (!StickyEpisodeIdentity.firstWinsExisting(ctx.seen, key)) return null
        val existingId = ctx.existingByKey[key]
        val piMatchId =
            if (existingId == null && ctx.piBaseline != null) {
                EpisodeSupplementMatcher.findMatchingBaseline(rss, ctx.piBaseline)?.id
            } else {
                null
            }
        val usablePiId = piMatchId?.takeIf { it !in ctx.occupiedIds }
        val episodeId =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = existingId,
                piMatchId = usablePiId,
                rssNamespaceId = ctx.rssNamespaceId,
                guid = rss.guid,
                enclosureUrl = rss.audioUrl,
                publishedDate = rss.publishedDate,
                title = rss.title,
            ) ?: return null
        ctx.occupiedIds.add(episodeId)
        return LocalEpisodeEntity(
            episodeId = episodeId,
            podcastId = ctx.podcastIndexId,
            guid = key,
            title = rss.title,
            description = rss.description,
            audioUrl = rss.audioUrl,
            imageUrl =
            EpisodeSupplementArtworkLogic.resolvedImageUrl(
                itemImageUrl = rss.imageUrl,
                channelImageUrl = ctx.channelImageUrl,
                showImageUrl = ctx.showImageUrl,
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
