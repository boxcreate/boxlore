package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.database.PodcastEntity

/**
 * The address an OPML export gives each followed show: its saved publisher feed address, so other
 * podcast apps read the publisher's feed. A show without one is left out of the OPML rather than
 * given a boxlore address: never the app-internal transport address ([PodcastEntity.transportUrl]),
 * a `/v2/feed/...` read or the old `/episodes?id=` JSON fallback, none of which is a feed.
 *
 * Added in P4 PR 17 of the own-episode-IDs plan (plan section 8, OPML exports the publisher address).
 * COMPAT(own-ids): never exports transport addresses. Permanent: never remove (MIGRATION.md §5).
 */
internal object LibraryBackupOpml {
    private val FEED_SCHEMES = setOf("http", "https")
    private val BOXLORE_PATHS = listOf("/v2/feed/", "/episodes?id=")

    /** [entity]'s publisher feed address for OPML, or null when it has none ([apiBaseUrl] is boxlore's API). */
    fun publisherFeedUrl(entity: PodcastEntity, apiBaseUrl: String = BuildConfig.BOXLORE_API_BASE_URL,): String? {
        val url = entity.feedUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (url.substringBefore("://", missingDelimiterValue = "").lowercase() !in FEED_SCHEMES) return null
        if (url == entity.transportUrl?.trim()) return null
        val apiBase = apiBaseUrl.trim().trimEnd('/')
        if (apiBase.isNotEmpty() && url.startsWith(apiBase, ignoreCase = true)) return null
        if (BOXLORE_PATHS.any { url.contains(it, ignoreCase = true) }) return null
        return url
    }
}
