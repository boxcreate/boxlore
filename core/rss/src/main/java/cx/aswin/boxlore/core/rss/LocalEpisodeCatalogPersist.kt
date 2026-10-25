package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity

/**
 * Where a planned catalog row's ID came from, before [LocalEpisodeCollisionGuard] checks it.
 *
 * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs; section 5, aliases).
 */
enum class PlannedIdSource {
    /** A stored row of the show holds the item's key (or an older key of it). */
    STORED,

    /** No stored row, but a trusted alias of the same (show, key) kept the ID of an evicted row. */
    ALIAS,

    /**
     * No stored row or alias, but this show's listener rows or unresolved alias hold the item's own
     * phone mint ([ListenerIdAdoption]).
     */
    OWN_MINT,

    /**
     * No stored row or alias, but an ID only this show's listener rows hold matched the item, and
     * only it ([ListenerIdAdoption]).
     */
    LISTENER_MATCH,

    /** A new episode that follows the identity contract: its recipe ID. */
    RECIPE,

    /** A new item that takes no recipe ID (outside the contract, or a show without a namespace): a phone-minted negative ID. */
    LOCAL,
}

/**
 * One row a catalog read plans to write, with where its ID came from.
 *
 * @property keyFollowsContract the row is written under the key the shared identity contract gives
 *   the item ([ParsedFeedItem.keyFollowsContract], and not an older key a stored row keeps); only
 *   such rows get recipe IDs and aliases
 * @property fallbackIds IDs [LocalEpisodeCollisionGuard] tries, in order, when a reused ID
 *   ([PlannedIdSource.ALIAS], [PlannedIdSource.OWN_MINT], [PlannedIdSource.LISTENER_MATCH]) belongs to
 *   another episode by the time the row is written: the key's other trusted alias IDs, then its
 *   recipe ID. A reserved ID comes only after these.
 *
 * Added in P4 PR 9 (own-IDs plan section 4).
 */
data class PlannedEpisodeRow(
    val entity: LocalEpisodeEntity,
    val source: PlannedIdSource,
    val keyFollowsContract: Boolean,
    val fallbackIds: List<String> = emptyList(),
)

/** Maps a parsed publisher feed onto sticky local catalog rows. */
object LocalEpisodeCatalogPersist {
    /** [plan]'s rows without their sources. */
    fun toLocalEpisodes(
        podcastIndexId: String,
        rssNamespaceId: String,
        parsed: List<ParsedFeedItem>,
        existing: List<LocalEpisodeIdentity>,
        newIds: NewEpisodeIds,
        channelImageUrl: String?,
        showImageUrl: String?,
    ): List<LocalEpisodeEntity> = plan(podcastIndexId, rssNamespaceId, parsed, existing, newIds, channelImageUrl, showImageUrl).map { it.entity }

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
     * - **Evicted episodes keep their IDs.** An item no stored row holds takes the ID a trusted
     *   alias of its (show, key) keeps ([NewEpisodeIds]), first by its catalog key, then by an
     *   older key (written under that key, like a stored row found by it).
     * - **Listener state stays attached.** Otherwise an item that follows the identity contract
     *   keeps an ID only this show's history, queue, downloads or unresolved aliases hold, when it
     *   provably owns it ([ListenerIdAdoption]: its own phone mint, else a unique listener match).
     *   Before P4 PR 9 the item took the same ID through its phone mint or the Podcast Index match.
     * - **New episodes get recipe IDs.** Otherwise an item that follows the identity contract takes
     *   the recipe ID of (show, key) in [NewEpisodeIds.epNs]; any other item, and every item of a
     *   show without a namespace (`rss:`), keeps a phone-minted negative ID.
     *
     * The proposed IDs are not checked against other episodes here: the caller passes the rows
     * through [LocalEpisodeCollisionGuard] in the write's transaction, which reserves another ID
     * when one belongs to another episode.
     *
     * Document order and the two-key lookup were added in P4 PR 8; aliases and recipe IDs replaced
     * the Podcast Index match in P4 PR 9 (own-IDs plan section 4).
     * COMPAT(own-ids): keeps every stored episode ID and decides new ones. Permanent: never remove (MIGRATION.md §5).
     */
    fun plan(
        podcastIndexId: String,
        rssNamespaceId: String,
        parsed: List<ParsedFeedItem>,
        existing: List<LocalEpisodeIdentity>,
        newIds: NewEpisodeIds,
        channelImageUrl: String?,
        showImageUrl: String?,
    ): List<PlannedEpisodeRow> {
        val occupiedIds = existing.map { it.episodeId }.toMutableSet()
        val stored = StoredRowMatch.StoredRows.of(existing.map { Triple(it.episodeId, it.guid, it.audioUrl) })
        val matches = StoredRowMatch.resolve(parsed, stored)
        val aliasMatches = aliasMatches(parsed, matches, newIds, occupiedIds)
        val adopted = listenerMatches(parsed, matches, aliasMatches, newIds, rssNamespaceId, occupiedIds)
        val ctx =
            PersistContext(
                podcastIndexId = podcastIndexId,
                rssNamespaceId = rssNamespaceId,
                newIds = newIds,
                channelImageUrl = channelImageUrl,
                showImageUrl = showImageUrl,
            )
        return parsed.mapIndexedNotNull { position, item ->
            val match = matches[position] ?: return@mapIndexedNotNull null
            val aliasMatch = aliasMatches[position]
            val adoption = adopted[position]
            when {
                aliasMatch != null -> mapOne(ctx, item, aliasMatch, PlannedIdSource.ALIAS)
                adoption != null -> mapOne(ctx, item, StoredRowMatch(match.key, adoption.episodeId), adoption.kind.source)
                else -> mapOne(ctx, item, match, reusedFrom = null)
            }
        }
    }

    private val ListenerIdAdoption.Kind.source: PlannedIdSource
        get() =
            when (this) {
                ListenerIdAdoption.Kind.OWN_MINT -> PlannedIdSource.OWN_MINT
                ListenerIdAdoption.Kind.LISTENER_MATCH -> PlannedIdSource.LISTENER_MATCH
            }

    private data class PersistContext(
        val podcastIndexId: String,
        val rssNamespaceId: String,
        val newIds: NewEpisodeIds,
        val channelImageUrl: String?,
        val showImageUrl: String?,
    )

    /**
     * For each winning item no stored row holds, the trusted alias whose ID it keeps: by its
     * catalog key for every item first (document order), then by its older keys. An ID a stored row
     * or an earlier item holds is never taken twice.
     */
    private fun aliasMatches(
        parsed: List<ParsedFeedItem>,
        matches: Array<StoredRowMatch?>,
        newIds: NewEpisodeIds,
        occupiedIds: Set<String>,
    ): Array<StoredRowMatch?> {
        val result = arrayOfNulls<StoredRowMatch>(parsed.size)
        if (!newIds.hasAliases) return result
        val claimed = HashSet(occupiedIds)
        val open = parsed.indices.sortedBy { parsed[it].documentIndex }.filter { matches[it] != null && matches[it]?.storedId == null }
        for (position in open) {
            val key = matches[position]?.key ?: continue
            newIds.aliasIdsFor(key).firstOrNull { it !in claimed }?.let { id ->
                claimed += id
                result[position] = StoredRowMatch(key, id)
            }
        }
        for (position in open.filter { result[it] == null }) {
            val hit =
                parsed[position].legacyKeys.firstNotNullOfOrNull { legacyKey ->
                    newIds.aliasIdsFor(legacyKey).firstOrNull { it !in claimed }?.let { legacyKey to it }
                } ?: continue
            claimed += hit.second
            result[position] = StoredRowMatch(key = hit.first, storedId = hit.second)
        }
        return result
    }

    /**
     * For each winning item no stored row or trusted alias holds, the listener-held ID it keeps
     * ([ListenerIdAdoption]); only for shows that take recipe IDs and items that follow the
     * identity contract. Every winning item takes part in the matching, so a match must be unique
     * across the whole feed.
     */
    @Suppress("LongParameterList")
    private fun listenerMatches(
        parsed: List<ParsedFeedItem>,
        matches: Array<StoredRowMatch?>,
        aliasMatches: Array<StoredRowMatch?>,
        newIds: NewEpisodeIds,
        rssNamespaceId: String,
        occupiedIds: Set<String>,
    ): Map<Int, ListenerIdAdoption.Adopted> {
        if (newIds.epNs == null || newIds.listeners.isEmpty) return emptyMap()
        val claimed = HashSet(occupiedIds)
        aliasMatches.forEach { match -> match?.storedId?.let(claimed::add) }
        val candidates =
            parsed.indices
                .sortedBy { parsed[it].documentIndex }
                .mapNotNull { position ->
                    val match = matches[position] ?: return@mapNotNull null
                    val item = parsed[position]
                    val open =
                        match.storedId == null &&
                            aliasMatches[position] == null &&
                            item.keyFollowsContract &&
                            match.key == item.catalogKey
                    ListenerIdAdoption.Candidate(
                        position = position,
                        open = open,
                        mints = if (open) ownMints(item, match.key, rssNamespaceId) else emptyList(),
                        audioKeys =
                        (listOf(item.episode.audioUrl) + item.enclosureUrls + item.audioUrls)
                            .mapNotNullTo(HashSet()) { url -> url.trim().takeIf(String::isNotEmpty) },
                        title = item.episode.title,
                        publishedDate = item.episode.publishedDate,
                    )
                }
        return ListenerIdAdoption.adopt(candidates, newIds.listeners, claimed)
    }

    /** The IDs older builds could have minted for [item]: the parser's, then the mint of its key and of each older key. */
    private fun ownMints(item: ParsedFeedItem, key: String, rssNamespaceId: String,): List<String> {
        if (!rssNamespaceId.startsWith(RSS_NAMESPACE_PREFIX)) return listOf(item.episode.episodeId)
        val mints = (listOf(key) + item.legacyKeys).map { StickyEpisodeIdentity.ownMintedId(rssNamespaceId, it) }
        return (listOf(item.episode.episodeId) + mints).distinct()
    }

    private const val RSS_NAMESPACE_PREFIX = "rss:"

    private fun mapOne(ctx: PersistContext, item: ParsedFeedItem, match: StoredRowMatch, reusedFrom: PlannedIdSource?,): PlannedEpisodeRow? {
        val rss = item.episode
        val existingId = match.storedId
        // A row kept under an older key (a parser's audio pick) is not under the contract's key.
        val contractKey = item.keyFollowsContract && match.key == item.catalogKey
        val recipeId = if (contractKey && (existingId == null || reusedFrom != null)) ctx.newIds.recipeIdFor(match.key) else null
        val episodeId =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = existingId,
                recipeId = recipeId,
                rssNamespaceId = ctx.rssNamespaceId,
                guid = rss.guid,
                enclosureUrl = rss.audioUrl,
                publishedDate = rss.publishedDate,
                title = rss.title,
            ) ?: return null
        val source =
            when {
                existingId != null -> reusedFrom ?: PlannedIdSource.STORED
                recipeId != null -> PlannedIdSource.RECIPE
                else -> PlannedIdSource.LOCAL
            }
        val otherAliasIds = if (source == PlannedIdSource.ALIAS) ctx.newIds.aliasIdsFor(match.key) else emptyList()
        val fallbackIds =
            if (reusedFrom == null) {
                emptyList()
            } else {
                (otherAliasIds + listOfNotNull(recipeId?.toString())).filter { it != episodeId }.distinct()
            }
        val entity =
            LocalEpisodeEntity(
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
        return PlannedEpisodeRow(entity, source, contractKey, fallbackIds)
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
