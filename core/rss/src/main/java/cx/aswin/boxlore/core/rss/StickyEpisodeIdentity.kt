package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Catalog identity for local and true-RSS episode rows.
 *
 * Hash is a **mint** algorithm, not a refresh algorithm. Once an episodeId is
 * assigned for a catalog key it never changes (I6). A publisher guid change is
 * a new row (I8). Duplicate guid on the same show is first-wins (I9): the first
 * item in the feed document ([ParsedFeedItem.documentIndex]), not the newest one.
 */
object StickyEpisodeIdentity {
    /**
     * Trimmed GUID, else trimmed enclosure URL. Uses the recipe's explicit trim set
     * ([EpisodeRecipeId.catalogKey]), which equals Kotlin's `trim()` on Android, so
     * keys already stored on devices are unchanged and stay hashable.
     */
    fun catalogKey(guid: String?, enclosureUrl: String?,): String? = EpisodeRecipeId.catalogKey(guid, enclosureUrl)

    /**
     * Catalog key of a parsed feed item: trimmed GUID, else the trimmed raw `<enclosure url>` (the
     * caller passes the first playable one), else the trimmed audio address the parser picked.
     * Keying on the enclosure the feed declares, as the server does, keeps the key the same
     * whichever parser read the feed; null means the item has no identity.
     *
     * The audio-address fallback (items with no playable enclosure, such as `media:content` only) is
     * a phone-only key: docs/EPISODE_IDS.md §1 gives such items no identity. They keep negative IDs
     * and must never take a recipe ID ([ParsedFeedItem.keyFollowsContract]).
     *
     * Added in P4 PR 8 (own-IDs plan section 4, catalog key; GUID-less items).
     * COMPAT(own-ids): the key new local rows of GUID-less items are stored under. Permanent: never remove (MIGRATION.md §5).
     */
    fun feedItemKey(guid: String?, enclosureUrl: String?, audioUrl: String?,): String? = catalogKey(guid, enclosureUrl) ?: catalogKey(guid, audioUrl)

    /**
     * @return catalog key, or null when the item must be skipped (I3).
     */
    fun requireCatalogKey(guid: String?, enclosureUrl: String?,): String? = catalogKey(guid, enclosureUrl)

    /**
     * Assigns episodeId for a feed item.
     *
     * [existingId] from a prior row with the same catalog key is always reused
     * (I6 / I7 — never upgrade negative → PI).
     * [piMatchId] is used only when there is no existing row (I4).
     * Otherwise mint a negative id once (I5).
     */
    fun assignEpisodeId(
        existingId: String?,
        piMatchId: String?,
        rssNamespaceId: String,
        guid: String?,
        enclosureUrl: String?,
        publishedDate: Long,
        title: String,
    ): String? {
        if (requireCatalogKey(guid, enclosureUrl) == null) return null
        if (!existingId.isNullOrBlank()) return existingId
        val piId = piMatchId?.trim()?.takeIf { it.toLongOrNull()?.let { id -> id > 0L } == true }
        if (piId != null) return piId
        return RssIdGenerator.episodeIdForPodcast(
            podcastId = rssNamespaceId,
            guid = guid,
            enclosureUrl = enclosureUrl,
            publishedDate = publishedDate,
            title = title,
        )
    }

    /**
     * The negative ID [assignEpisodeId] mints for an item whose catalog key is [catalogKey], when
     * it has no stored row and no Podcast Index match. [LocalEpisodeCollisionGuard] looks it up
     * among a show's stored rows to find a legacy row (keyed `id:<ID>` or by audio address by Room
     * migration 31→32) of the same item: equal IDs mean the same hash input, so the same episode.
     * [rssNamespaceId] must be the feed's URL namespace, as for the original mint.
     *
     * Added in P4 PR 1 (own-IDs plan section 4: legacy and synthetic keys resolve when the feed is
     * read again).
     * COMPAT(own-ids): finds legacy rows by the ID they were minted with, so they keep it. Permanent: never remove (MIGRATION.md §5).
     */
    fun ownMintedId(rssNamespaceId: String, catalogKey: String,): String = RssIdGenerator.episodeIdForPodcast(
        podcastId = rssNamespaceId,
        guid = catalogKey,
        enclosureUrl = null,
        publishedDate = 0L,
        title = "",
    )

    /**
     * A local ID for a row whose usual ID another (show, key) already holds
     * ([LocalEpisodeCollisionGuard]). Negative like every minted ID, and the same for the same
     * (namespace, show, key, attempt), so a refresh that hits the same clash picks the same ID.
     */
    fun reservedEpisodeId(rssNamespaceId: String, podcastId: String, catalogKey: String, attempt: Int,): String =
        RssIdGenerator.episodeIdForPodcast(
            podcastId = rssNamespaceId,
            guid = "$catalogKey\u0000reserved:$podcastId:$attempt",
            enclosureUrl = null,
            publishedDate = 0L,
            title = "",
        )

    fun firstWinsExisting(seenKeys: MutableSet<String>, key: String,): Boolean {
        if (key in seenKeys) return false
        seenKeys += key
        return true
    }
}
