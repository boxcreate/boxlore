package cx.aswin.boxlore.core.model

import kotlinx.serialization.Serializable

enum class EpisodeStatus { UNPLAYED, IN_PROGRESS, COMPLETED }

@Serializable
data class Podcast(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val type: String = "episodic", // "episodic" or "serial"
    val description: String? = null,
    val genre: String = "Podcast",
    val colorHex: String? = null, // For dynamic theming storage
    val fallbackImageUrl: String? = null, // Logic: Episode Art -> Fallback Podcast Art
    val latestEpisode: Episode? = null,
    val resumeProgress: Float? = null, // 0.0 - 1.0
    val episodeStatus: EpisodeStatus = EpisodeStatus.UNPLAYED,
    val subscribedAt: Long = 0L,
    // --- Podcast 2.0 ---
    val fundingUrl: String? = null,
    val fundingMessage: String? = null,
    val podcastGuid: String? = null,
    val medium: String? = null, // "podcast", "music", "video"
    val ownerName: String? = null,
    val hasValue: Boolean = false, // true if V4V value tag exists
    val updateFrequency: String? = null,
    val location: String? = null,
    val license: String? = null,
    val isLocked: Boolean = false,
    val podroll: List<PodrollItem>? = null,
    // User listening style: "newest" / "oldest" / null (default by type)
    val preferredSort: String? = null,
    val notificationsEnabled: Boolean = false,
    val autoDownloadEnabled: Boolean = false,
    val skipBeginningOverrideMs: Long? = null,
    val skipEndingOverrideMs: Long? = null,
    val sourceType: String = SOURCE_PODCAST_INDEX,
    val feedUrl: String? = null,
    val rssRefreshCapability: String = RSS_REFRESH_MANUAL,
    val rssCatalogStale: Boolean = false,
    val rssHasNewEpisodes: Boolean = false,
    /** Podcast Index row represented by this local RSS subscription, when known. */
    val linkedPodcastIndexId: String? = null,
    val customGenre: String? = null,
    val customGenreIcon: String? = null,
) {
    val isRss: Boolean
        get() = sourceType == SOURCE_RSS

    val effectiveGenre: String
        get() = customGenre?.takeIf { it.isNotBlank() } ?: genre

    val recommendationGenre: String
        get() = PodcastGenres.canonicalize(customGenre) ?: genre

    companion object {
        const val SOURCE_PODCAST_INDEX = "podcast_index"
        const val SOURCE_RSS = "rss"
        const val RSS_REFRESH_HEAD_VALIDATORS = "head_validators"
        const val RSS_REFRESH_MANUAL = "manual"
        const val RSS_REFRESH_AUTOMATIC = "automatic"
    }
}

fun Podcast.isLatestEpisodeNew(lastSeenId: String?): Boolean {
    // Shared badge for true-RSS freshness checks and PI direct-feed tip promotes.
    if (rssHasNewEpisodes) return true
    if (episodeStatus != EpisodeStatus.UNPLAYED) return false
    val ep = latestEpisode ?: return false
    if (subscribedAt <= 0L) return false
    if (ep.publishedDate <= (subscribedAt / 1000L)) return false
    if (ep.id == lastSeenId) return false
    val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
    return hoursSinceRelease <= 48.0
}

/**
 * The shows whose listener rows (history, downloads, queue) count as this show's: the show itself
 * and, for an RSS show the listener switched to from a Podcast Index show, that Podcast Index show.
 * The switch keeps every row under its stored show and ID (docs/EPISODE_IDS.md §5), so per-show
 * reads (show scores, Most listened, serial next-up, download limits) match rows by these IDs.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 * COMPAT(own-ids): a switched show reads its old show's listening. Permanent: never remove (MIGRATION.md §5).
 */
val Podcast.listenerShowIds: Set<String>
    get() = ListenerShows.idsOf(id, isRss, linkedPodcastIndexId)

/**
 * [Podcast.listenerShowIds] for a show stored as plain fields.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 * COMPAT(own-ids): a switched show reads its old show's listening. Permanent: never remove (MIGRATION.md §5).
 */
object ListenerShows {
    /** [showId], plus [linkedPodcastIndexId] when [showId] is an RSS show that replaced it. */
    fun idsOf(showId: String, isRss: Boolean, linkedPodcastIndexId: String?,): Set<String> {
        val linked = linkedPodcastIndexId?.takeIf { isRss && it.isNotBlank() && it != showId } ?: return setOf(showId)
        return setOf(showId, linked)
    }
}
