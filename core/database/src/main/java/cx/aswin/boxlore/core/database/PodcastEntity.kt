package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cx.aswin.boxlore.core.model.PodcastGenres

@Entity(
    tableName = "podcasts",
    indices = [
        Index(value = ["linkedPodcastIndexId"]),
        Index(value = ["isDirty"]),
    ],
)
data class PodcastEntity(
    @PrimaryKey
    val podcastId: String,

    val title: String,
    val author: String,
    val imageUrl: String,
    val description: String?,

    // Subscription State
    val isSubscribed: Boolean = false,
    val subscribedAt: Long = 0L,
    val unsubscribedAt: Long = 0L,
    val isDirty: Boolean = false,
    val syncedAt: Long = 0L,

    val genre: String? = null,
    val type: String = "episodic",

    val lastRefreshed: Long = 0,

    // Cached latest episode for instantaneous home screen rendering
    val latestEpisode: cx.aswin.boxlore.core.model.Episode? = null,

    // Podcasting 2.0 Fields
    val podcastGuid: String? = null,
    val fundingUrl: String? = null,
    val fundingMessage: String? = null,
    val medium: String? = null,
    val hasValue: Boolean = false,
    val updateFrequency: String? = null,
    val location: String? = null,
    val license: String? = null,
    val isLocked: Boolean = false,

    // User listening style preference: "newest" / "oldest" / null (use type-based default)
    val preferredSort: String? = null,
    val notificationsEnabled: Boolean = false,
    val autoDownloadEnabled: Boolean = false,
    // Null inherits the global playback setting; zero explicitly disables trimming.
    val skipBeginningOverrideMs: Long? = null,
    val skipEndingOverrideMs: Long? = null,

    // Catalog source. Podcast Index remains the default for existing rows.
    val sourceType: String = SOURCE_PODCAST_INDEX,
    val feedUrl: String? = null,
    val feedEtag: String? = null,
    val feedLastModified: String? = null,
    val feedDeclaredUpdatedAt: Long? = null,
    val rssRefreshCapability: String = RSS_REFRESH_MANUAL,
    val lastRssSyncAt: Long = 0L,
    val rssCatalogStale: Boolean = false,
    val rssHasNewEpisodes: Boolean = false,
    val linkedPodcastIndexId: String? = null,
    val customGenre: String? = null,
    val customGenreIcon: String? = null,

    // Feed identity and address bookkeeping (Room v39). Whole-row writers must carry these from
    // the stored row once something reads them; until then they are written but not read.
    /** Episode namespace recipe IDs are minted in; seeded as the show ID at the v39 upgrade and never changed once IDs exist. */
    val epNs: Long? = null,
    /** Where [feedUrl] came from (for example the registry or the publisher); null when unrecorded. */
    val feedUrlSource: String? = null,
    /** When [feedUrl] was last confirmed against the address registry (epoch ms); 0 when never. */
    @ColumnInfo(defaultValue = "0")
    val feedUrlCheckedAt: Long = 0L,
    /** Address to fetch the feed through when the publisher's can't be read directly; null when not needed. */
    val transportUrl: String? = null,
    /** The address [feedUrl] replaced in its last automatic correction, kept so it can be undone. */
    val previousFeedUrl: String? = null,
) {
    val isRss: Boolean
        get() = sourceType == SOURCE_RSS

    val effectiveGenre: String
        get() = if (isSubscribed) {
            customGenre?.takeIf { it.isNotBlank() } ?: (genre ?: "Podcast")
        } else {
            genre ?: "Podcast"
        }

    val recommendationGenre: String
        get() = if (isSubscribed) {
            PodcastGenres.canonicalize(customGenre) ?: (genre ?: "Podcast")
        } else {
            genre ?: "Podcast"
        }

    companion object {
        const val SOURCE_PODCAST_INDEX = "podcast_index"
        const val SOURCE_RSS = "rss"
        const val RSS_REFRESH_HEAD_VALIDATORS = "head_validators"
        const val RSS_REFRESH_MANUAL = "manual"
        const val RSS_REFRESH_AUTOMATIC = "automatic"
    }
}
