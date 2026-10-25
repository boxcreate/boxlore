package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import cx.aswin.boxlore.core.model.Episode

/** Reuses stored true-RSS episodeIds on refresh so history / downloads stay valid. */
object StickyRssEpisodeRemap {
    data class Catalog(val episodes: List<RssEpisodeEntity>, val latestEpisode: Episode?,)

    fun prepare(
        parsed: List<ParsedFeedItem>,
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

    /** [remapItems] as rows. */
    fun remap(parsed: List<ParsedFeedItem>, existing: List<RssEpisodeIdentity>,): List<RssEpisodeEntity> = remapItems(parsed, existing).map(ParsedFeedItem::episode)

    /**
     * [parsed] in its order, one item per catalog key (the first in the feed document wins), each
     * under the ID of the stored row it matches, else its minted ID. Stored rows are found by the
     * item's catalog key, then by the keys older builds used ([StoredRowMatch.resolve]). Two items
     * never end up under one ID: a later one in the document is dropped.
     *
     * Document order and the two-key lookup were added in P4 PR 8 (own-IDs plan section 4).
     * COMPAT(own-ids): keeps every stored RSS episode ID when keys or duplicate handling change. Permanent: never remove (MIGRATION.md §5).
     */
    fun remapItems(parsed: List<ParsedFeedItem>, existing: List<RssEpisodeIdentity>,): List<ParsedFeedItem> {
        val existingByKey = linkedMapOf<String, String>()
        for (row in existing) {
            val key = StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl) ?: continue
            existingByKey.putIfAbsent(key, row.episodeId)
        }
        val matches = StoredRowMatch.resolve(parsed, existingByKey)
        val remapped =
            parsed.mapIndexedNotNull { position, item ->
                val match = matches[position] ?: return@mapIndexedNotNull null
                val stickyId = match.storedId ?: item.episode.episodeId
                if (stickyId == item.episode.episodeId) item else item.copy(episode = item.episode.copy(episodeId = stickyId))
            }
        val firstPerId = FeedDocumentOrder.firstInDocument(remapped).toSet()
        return remapped.filter { it in firstPerId }
    }
}
