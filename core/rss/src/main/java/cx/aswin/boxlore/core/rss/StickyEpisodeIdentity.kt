package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.EpisodeRecipeId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Catalog identity for local and true-RSS episode rows.
 *
 * Hash is a **mint** algorithm, not a refresh algorithm. Once an episodeId is
 * assigned for a catalog key it never changes (I6). A publisher guid change is
 * a new row (I8). Duplicate guid on the same show is first-wins (I9): the first
 * item in the feed document ([ParsedFeedItem.documentIndex]), not the newest one.
 */
object StickyEpisodeIdentity {
    /** Hash tag of [reservedEpisodeId]; never reused for another formula. */
    private const val RESERVED_TAG = "boxlore-reserved1"

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
     * [existingId] (a stored row or an alias of the same catalog key) is always reused (I6 / I7:
     * nothing is re-keyed). Otherwise [recipeId] is used when the caller computed one (the recipe
     * ID of (show, key) in the show's namespace, for items that follow the identity contract).
     * Otherwise a negative ID is minted once (I5).
     *
     * Changed in P4 PR 9 (own-IDs plan section 4): the Podcast Index match is gone; new episodes of
     * Podcast Index shows take recipe IDs instead.
     */
    fun assignEpisodeId(
        existingId: String?,
        recipeId: Long?,
        rssNamespaceId: String,
        guid: String?,
        enclosureUrl: String?,
        publishedDate: Long,
        title: String,
    ): String? {
        if (requireCatalogKey(guid, enclosureUrl) == null) return null
        if (!existingId.isNullOrBlank()) return existingId
        if (recipeId != null) return recipeId.toString()
        return RssIdGenerator.episodeIdForPodcast(
            podcastId = rssNamespaceId,
            guid = guid,
            enclosureUrl = enclosureUrl,
            publishedDate = publishedDate,
            title = title,
        )
    }

    /**
     * The negative ID [assignEpisodeId] mints for an item whose catalog key is [catalogKey] when it
     * takes no stored ID and no recipe ID. [LocalEpisodeCollisionGuard] looks it up among a show's
     * stored rows to find a legacy row (keyed `id:<ID>` or by audio address by Room migration
     * 31→32) of the same item: equal IDs mean the same hash input, so the same episode.
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
     * ([LocalEpisodeCollisionGuard]). Negative like every phone-minted ID, and the same for the
     * same (show, key, attempt), so a refresh that hits the same clash picks the same ID.
     *
     * The namespace is the show ID ([podcastId]), not the feed address, so a reserved ID stays the
     * same after the feed moves; the refresh also records an alias for it, so it survives eviction.
     * P4a minted reserved IDs under the feed address; those exist only on development installs and
     * keep their IDs through their stored rows.
     *
     * Changed in P4 PR 9 (own-IDs plan section 4, collisions: a distinct local ID in the same
     * transaction, with an alias).
     * COMPAT(own-ids): reserved IDs are stored IDs. Permanent: never remove or change (MIGRATION.md §5).
     */
    fun reservedEpisodeId(podcastId: String, catalogKey: String, attempt: Int,): String {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$RESERVED_TAG\u0000$podcastId\u0000$catalogKey\u0000$attempt".toByteArray(StandardCharsets.UTF_8))
        var positive = 0L
        repeat(Long.SIZE_BYTES) { index -> positive = (positive shl Byte.SIZE_BITS) or (digest[index].toLong() and 0xffL) }
        positive = positive and Long.MAX_VALUE
        if (positive == 0L) positive = 1L
        return (-positive).toString()
    }

    fun firstWinsExisting(seenKeys: MutableSet<String>, key: String,): Boolean {
        if (key in seenKeys) return false
        seenKeys += key
        return true
    }
}
