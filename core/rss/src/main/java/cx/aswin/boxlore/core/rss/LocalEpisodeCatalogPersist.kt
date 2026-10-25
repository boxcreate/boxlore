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
     *   then by its own minted ID, then by the keys older builds stored it under
     *   ([ParsedFeedItem.legacyKeys]: the audio address a parser picked, for GUID-less items). A row
     *   found by its ID or an older key keeps its key too, so the write updates it in place. Exact
     *   keys are matched for every item before any other lookup, so a row stays with the item that
     *   carries its exact key ([StoredRowMatch.resolve]).
     * - **Older duplicate rows stay as they are.** Older builds could store two rows for one GUID-less
     *   item (one per parser's audio address) or for one padded and one plain GUID. The item now
     *   updates one of them; the other is left in place, unchanged and no longer refreshed, with any
     *   listener state it holds. Joining such pairs is alias work (own-IDs plan, P4 PR 10).
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
        val occupiedIds = existing.map { it.episodeId }.toMutableSet()
        val stored = StoredRowMatch.StoredRows.of(existing.map { Triple(it.episodeId, it.guid, it.audioUrl) })
        val matches = StoredRowMatch.resolve(parsed, stored)
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
    /** A stored row's re-trimmed key and trimmed audio address. */
    internal data class StoredKey(val key: String, val audioKey: String?,)

    /**
     * A show's stored rows, by re-trimmed key and by ID. When two rows share a key, [idsByKey] keeps
     * the first; [keysById] has every row.
     */
    internal class StoredRows(val idsByKey: Map<String, String>, val keysById: Map<String, StoredKey>,) {
        internal companion object {
            /** Rows as (episode ID, guid, audio address); a row with no key is skipped. */
            fun of(rows: List<Triple<String, String?, String>>): StoredRows {
                val idsByKey = LinkedHashMap<String, String>()
                val keysById = LinkedHashMap<String, StoredKey>()
                for ((id, guid, audioUrl) in rows) {
                    val key = StickyEpisodeIdentity.catalogKey(guid, audioUrl) ?: continue
                    idsByKey.putIfAbsent(key, id)
                    keysById.putIfAbsent(id, StoredKey(key, StickyEpisodeIdentity.catalogKey(null, audioUrl)))
                }
                return StoredRows(idsByKey, keysById)
            }
        }
    }

    internal companion object {
        /**
         * One match per position of [items] that wins its catalog key (first in the feed document);
         * null for skipped items. Stored rows are matched in three passes in document order, never
         * taking an ID another item already matched:
         * 1. the exact catalog key (a stored row holding the item's own minted ID under that key is
         *    preferred when several rows share it);
         * 2. for a GUID-less item, its own minted ID, when the row holding it is keyed on one of the
         *    item's enclosures or plays one of its audio addresses: the same hash input and the same
         *    media, so the same item, stored while the other parser read the feed. Synthetic `id:`
         *    keys and rows keyed by a GUID are left to [LocalEpisodeCollisionGuard], which heals or
         *    separates them;
         * 3. the keys older builds used ([ParsedFeedItem.legacyKeys]).
         * A row found by pass 2 or 3 keeps its stored key.
         *
         * COMPAT(own-ids): finds rows stored under older keys so they keep their IDs. Permanent: never remove (MIGRATION.md §5).
         */
        fun resolve(items: List<ParsedFeedItem>, stored: StoredRows): Array<StoredRowMatch?> {
            val matches = arrayOfNulls<StoredRowMatch>(items.size)
            val seenKeys = HashSet<String>()
            // Positions in document order, each with its catalog key; later duplicates of a key are skipped.
            val winners =
                items.indices
                    .sortedBy { items[it].documentIndex }
                    .mapNotNull { position -> items[position].catalogKey?.takeIf(seenKeys::add)?.let { position to it } }
            val claimed = HashSet<String>()
            for ((position, key) in winners) {
                val ownId = items[position].episode.episodeId
                val storedId = ownId.takeIf { stored.keysById[it]?.key == key } ?: stored.idsByKey[key]
                if (storedId != null) claimed += storedId
                matches[position] = StoredRowMatch(key, storedId)
            }
            for ((position, _) in winners.filter { (position, _) -> matches[position]?.storedId == null }) {
                val item = items[position]
                val ownId = item.episode.episodeId
                val storedKey = stored.keysById[ownId]?.takeIf { ownId !in claimed && it.holdsMediaOf(item) } ?: continue
                claimed += ownId
                matches[position] = StoredRowMatch(key = storedKey.key, storedId = ownId)
            }
            for ((position, _) in winners.filter { (position, _) -> matches[position]?.storedId == null }) {
                val legacy =
                    items[position].legacyKeys.firstNotNullOfOrNull { legacyKey ->
                        stored.idsByKey[legacyKey]?.takeIf { it !in claimed }?.let { legacyKey to it }
                    } ?: continue
                claimed += legacy.second
                matches[position] = StoredRowMatch(key = legacy.first, storedId = legacy.second)
            }
            return matches
        }

        private fun StoredKey.holdsMediaOf(item: ParsedFeedItem): Boolean {
            if (StickyEpisodeIdentity.catalogKey(item.episode.guid, null) != null) return false
            if (key == "id:${item.episode.episodeId}") return false
            val enclosures = item.enclosureUrls.mapNotNull { StickyEpisodeIdentity.catalogKey(null, it) }
            val audio = item.audioUrls.mapNotNull { StickyEpisodeIdentity.catalogKey(null, it) }
            return key in enclosures || audioKey in audio
        }
    }
}
