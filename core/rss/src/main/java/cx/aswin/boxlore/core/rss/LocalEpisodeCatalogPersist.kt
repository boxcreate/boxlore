package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.model.Episode

/** Maps a parsed publisher feed onto sticky local catalog rows. */
object LocalEpisodeCatalogPersist {
    /**
     * One local row per catalog key of [parsed], in [parsed]'s order.
     *
     * - **Duplicates:** when items share a catalog key, the first in the feed document
     *   ([ParsedFeedItem.documentIndex]) is written and the others are skipped, whatever their dates.
     * - **Stored rows keep their IDs.** An item first looks up a stored row by its catalog key,
     *   then by the keys older builds stored it under ([ParsedFeedItem.legacyKeys]: the audio address a
     *   parser picked, for GUID-less items). A row found by an older key keeps that key too, so the
     *   write updates it in place. Exact keys are matched for every item before any older key, so a
     *   row stays with the item that carries its exact key.
     * - Otherwise the ID comes from [StickyEpisodeIdentity.assignEpisodeId], as before.
     *
     * Document order and the two-key lookup were added in P4 PR 8 (own-IDs plan section 4).
     * COMPAT(own-ids): keeps every stored episode ID when keys or duplicate handling change. Permanent: never remove (MIGRATION.md §5).
     */
    fun toLocalEpisodes(
        podcastIndexId: String,
        rssNamespaceId: String,
        parsed: List<ParsedFeedItem>,
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
        val matches = StoredRowMatch.resolve(parsed, existingByKey)
        val ctx =
            PersistContext(
                podcastIndexId = podcastIndexId,
                rssNamespaceId = rssNamespaceId,
                occupiedIds = occupiedIds,
                piBaseline = piBaseline,
                channelImageUrl = channelImageUrl,
                showImageUrl = showImageUrl,
            )
        return parsed.mapIndexedNotNull { position, item -> matches[position]?.let { mapOne(ctx, item, it) } }
    }

    private data class PersistContext(
        val podcastIndexId: String,
        val rssNamespaceId: String,
        val occupiedIds: MutableSet<String>,
        val piBaseline: List<Episode>?,
        val channelImageUrl: String?,
        val showImageUrl: String?,
    )

    private fun mapOne(ctx: PersistContext, item: ParsedFeedItem, match: StoredRowMatch,): LocalEpisodeEntity? {
        val rss = item.episode
        val existingId = match.storedId
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
            guid = match.key,
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

/**
 * The key an item is written under and the stored ID it keeps, if any.
 *
 * Added in P4 PR 8 (own-IDs plan section 4).
 */
internal data class StoredRowMatch(val key: String, val storedId: String?,) {
    internal companion object {
        /**
         * One match per position of [items] that wins its catalog key (first in the feed document);
         * null for skipped items. Stored rows ([storedIdsByKey]: re-trimmed stored key to ID) are
         * matched in two passes in document order: every exact catalog key first, then the keys
         * older builds used ([ParsedFeedItem.legacyKeys]) for items still unmatched, never taking an ID
         * another item already matched.
         *
         * COMPAT(own-ids): finds rows stored under older keys so they keep their IDs. Permanent: never remove (MIGRATION.md §5).
         */
        fun resolve(items: List<ParsedFeedItem>, storedIdsByKey: Map<String, String>): Array<StoredRowMatch?> {
            val matches = arrayOfNulls<StoredRowMatch>(items.size)
            val seenKeys = HashSet<String>()
            // Positions in document order, each with its catalog key; later duplicates of a key are skipped.
            val winners =
                items.indices
                    .sortedBy { items[it].documentIndex }
                    .mapNotNull { position -> items[position].catalogKey?.takeIf(seenKeys::add)?.let { position to it } }
            val claimed = HashSet<String>()
            for ((position, key) in winners) {
                val storedId = storedIdsByKey[key]
                if (storedId != null) claimed += storedId
                matches[position] = StoredRowMatch(key, storedId)
            }
            for ((position, _) in winners.filter { (position, _) -> matches[position]?.storedId == null }) {
                val legacy =
                    items[position].legacyKeys.firstNotNullOfOrNull { legacyKey ->
                        storedIdsByKey[legacyKey]?.takeIf { it !in claimed }?.let { legacyKey to it }
                    } ?: continue
                claimed += legacy.second
                matches[position] = StoredRowMatch(key = legacy.first, storedId = legacy.second)
            }
            return matches
        }
    }
}
