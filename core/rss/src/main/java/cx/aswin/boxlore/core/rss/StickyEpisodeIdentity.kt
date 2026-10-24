package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Catalog identity for local and true-RSS episode rows.
 *
 * Hash is a **mint** algorithm, not a refresh algorithm. Once an episodeId is
 * assigned for a catalog key it never changes (I6). A publisher guid change is
 * a new row (I8). Duplicate guid on the same show is first-wins (I9).
 */
object StickyEpisodeIdentity {
    /**
     * Trimmed GUID, else trimmed enclosure URL. Uses the recipe's explicit trim set
     * ([EpisodeRecipeId.catalogKey]), which equals Kotlin's `trim()` on Android, so
     * keys already stored on devices are unchanged and stay hashable.
     */
    fun catalogKey(guid: String?, enclosureUrl: String?,): String? = EpisodeRecipeId.catalogKey(guid, enclosureUrl)

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
