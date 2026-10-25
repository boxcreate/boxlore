package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity

/**
 * One parsed feed item, with where it sat in the feed document and the keys that identify it.
 *
 * The parsers sort episodes newest first, which loses the feed's own order. Identity needs that
 * order: when two items share a catalog key, the first one in the raw feed document wins
 * (docs/EPISODE_IDS.md §3), whatever their publish dates say.
 *
 * [episode] keeps the ID minted exactly as before (the GUID, else the audio address the parser
 * picked). The [catalogKey] for a GUID-less item is the raw `<enclosure url>` instead, so it no
 * longer depends on which parser read the feed (the library parser runs only up to 4 MiB).
 *
 * Added in P4 PR 8 (own-IDs plan section 4: the first item in the feed document wins a duplicate
 * key; GUID-less items are keyed on the raw enclosure URL).
 * COMPAT(own-ids): every stored and new local episode ID is decided through these keys. Permanent: never remove (MIGRATION.md §5).
 *
 * @property documentIndex the item's 0-based position among the feed's `<item>`/`<entry>` elements.
 * @property enclosureUrls every `<enclosure url>` (or Atom `<link rel="enclosure">`) of the item
 *   that the parser saw, raw and in document order. The library parser sees only the last one.
 * @property audioUrls the audio addresses the parsers picked for this item; older builds keyed
 *   GUID-less rows on these.
 */
data class ParsedFeedItem(
    val episode: RssEpisodeEntity,
    val documentIndex: Int,
    val enclosureUrls: List<String> = emptyList(),
    val audioUrls: List<String> = listOf(episode.audioUrl),
) {
    /** Trimmed GUID, else trimmed first enclosure URL, else trimmed audio address; null: no identity. */
    val catalogKey: String? = StickyEpisodeIdentity.feedItemKey(episode.guid, enclosureUrls.firstOrNull(), episode.audioUrl)

    /**
     * Keys older builds stored this item under (GUID, else the audio address a parser picked),
     * other than [catalogKey]. Stored rows are looked up by these after [catalogKey], so no stored
     * ID moves when the key changes.
     *
     * COMPAT(own-ids): finds rows keyed by older builds so they keep their IDs. Permanent: never remove (MIGRATION.md §5).
     */
    val legacyKeys: List<String> =
        audioUrls
            .mapNotNull { StickyEpisodeIdentity.catalogKey(episode.guid, it) }
            .distinct()
            .filter { it != catalogKey }
}

/** Wraps rows that carry no feed position (stored rows, hand-built feeds): list order is document order. */
fun List<RssEpisodeEntity>.asParsedFeedItems(firstIndex: Int = 0): List<ParsedFeedItem> = mapIndexed { index, episode -> ParsedFeedItem(episode, firstIndex + index) }

/**
 * Document-order rules shared by both feed parsers and by every caller that writes episode rows.
 *
 * Added in P4 PR 8 (own-IDs plan section 4).
 * COMPAT(own-ids): decides which duplicate item keeps an episode identity. Permanent: never remove (MIGRATION.md §5).
 */
internal object FeedDocumentOrder {
    /** [items] by [ParsedFeedItem.documentIndex]; items at the same position keep their list order. */
    fun inDocumentOrder(items: List<ParsedFeedItem>): List<ParsedFeedItem> = items.sortedBy(ParsedFeedItem::documentIndex)

    /**
     * One item per catalog key and per episode ID, the first in the feed document winning, in
     * document order. An item without a catalog key is kept (callers that need a key skip it).
     */
    fun firstInDocument(items: List<ParsedFeedItem>): List<ParsedFeedItem> {
        val keys = HashSet<String>()
        val ids = HashSet<String>()
        return inDocumentOrder(items).filter { item ->
            val key = item.catalogKey
            val id = item.episode.episodeId
            if ((key != null && key in keys) || id in ids) {
                false
            } else {
                key?.let(keys::add)
                ids += id
                true
            }
        }
    }

    /** Newest first by publish date; items published at the same time keep their document order. */
    fun newestFirst(items: List<ParsedFeedItem>): List<ParsedFeedItem> = inDocumentOrder(items).sortedByDescending { it.episode.publishedDate }

    /**
     * Joins the library parser's items with the streaming parser's items for the same feed.
     *
     * Items are joined by catalog key, then by minted ID (the join before P4 PR 8), then, for
     * GUID-less items, by their last enclosure (the only one the library parser reports). A joined
     * item takes the streaming parser's enclosures, so its key is the feed's first enclosure, and
     * keeps the library item's ID and audio address, as before. The result has one item per key,
     * the first in the document winning, in document order.
     */
    fun merge(library: List<ParsedFeedItem>, custom: List<ParsedFeedItem>): List<ParsedFeedItem> {
        val libraryItems = firstInDocument(library)
        val customItems = firstInDocument(custom)
        // Positions in customItems; firstInDocument made keys and IDs unique.
        val byKey = HashMap<String, Int>()
        val byId = HashMap<String, Int>()
        val byLastEnclosure = HashMap<String, Int?>()
        customItems.forEachIndexed { position, item ->
            item.catalogKey?.let { byKey[it] = position }
            byId[item.episode.episodeId] = position
            if (item.isGuidless() && item.enclosureUrls.isNotEmpty()) {
                val last = item.enclosureUrls.last()
                // Two GUID-less items ending in the same enclosure can't be told apart: join neither.
                byLastEnclosure[last] = if (last in byLastEnclosure) null else position
            }
        }
        val joined = BooleanArray(customItems.size)

        fun partnerOf(item: ParsedFeedItem): Int? = listOfNotNull(
            item.catalogKey?.let(byKey::get),
            byId[item.episode.episodeId],
            item.enclosureUrls.lastOrNull()?.takeIf { item.isGuidless() }?.let(byLastEnclosure::get),
        ).firstOrNull { !joined[it] }

        val merged =
            libraryItems.map { libraryItem ->
                val position = partnerOf(libraryItem) ?: return@map libraryItem
                joined[position] = true
                val customItem = customItems[position]
                ParsedFeedItem(
                    episode = mergeEpisode(libraryItem.episode, customItem.episode),
                    documentIndex = minOf(libraryItem.documentIndex, customItem.documentIndex),
                    enclosureUrls = customItem.enclosureUrls.ifEmpty { libraryItem.enclosureUrls },
                    audioUrls = (libraryItem.audioUrls + customItem.audioUrls).distinct(),
                )
            } + customItems.filterIndexed { position, _ -> !joined[position] }
        return firstInDocument(merged)
    }

    private fun ParsedFeedItem.isGuidless(): Boolean = StickyEpisodeIdentity.catalogKey(episode.guid, null) == null

    /** The library parser's row, filled with what the streaming parser read better. */
    private fun mergeEpisode(library: RssEpisodeEntity, custom: RssEpisodeEntity): RssEpisodeEntity = library.copy(
        guid = custom.guid ?: library.guid,
        title = custom.title.takeIf(String::isNotBlank) ?: library.title,
        description = listOf(library.description, custom.description).maxByOrNull(String::length).orEmpty(),
        imageUrl = custom.imageUrl ?: library.imageUrl,
        duration = custom.duration.takeIf { it > 0 } ?: library.duration,
        publishedDate = custom.publishedDate.takeIf { it > 0L } ?: library.publishedDate,
        chaptersUrl = custom.chaptersUrl,
        transcriptUrl = custom.transcriptUrl,
        transcripts = custom.transcripts,
        persons = custom.persons,
        seasonNumber = custom.seasonNumber ?: library.seasonNumber,
        episodeNumber = custom.episodeNumber ?: library.episodeNumber,
        episodeType = custom.episodeType ?: library.episodeType,
        enclosureType = library.enclosureType ?: custom.enclosureType,
    )
}
