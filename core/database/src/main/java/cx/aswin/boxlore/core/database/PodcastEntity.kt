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

    // Feed identity and address bookkeeping (Room v39). PodcastDao.upsert keeps them when it
    // replaces a stored row (keepingFeedIdentityColumnsOf), so they change only through their own updates.
    /**
     * Episode namespace recipe IDs are minted in; seeded as the show ID at the v39 upgrade and never
     * changed once an ID has been issued under it. Null when not set yet (and always for `rss:` shows).
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns; decision D12).
     * COMPAT(own-ids): issued recipe IDs depend on it. Permanent: never remove (MIGRATION.md §5).
     */
    val epNs: Long? = null,
    /**
     * Where [feedUrl] came from (for example the registry or the publisher); null when unrecorded.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns; section 11, address repair).
     * COMPAT(own-ids): feed address bookkeeping. Permanent: never remove (MIGRATION.md §5).
     */
    val feedUrlSource: String? = null,
    /**
     * When [feedUrl] was last confirmed against the address registry (epoch ms); 0 when never.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns; section 11, address repair).
     * COMPAT(own-ids): feed address bookkeeping. Permanent: never remove (MIGRATION.md §5).
     */
    @ColumnInfo(defaultValue = "0")
    val feedUrlCheckedAt: Long = 0L,
    /**
     * Address to fetch the feed through when the publisher's can't be read directly; null when not needed.
     * Never exported in backups.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns; section 11, address repair).
     * COMPAT(own-ids): feed address bookkeeping. Permanent: never remove (MIGRATION.md §5).
     */
    val transportUrl: String? = null,
    /**
     * The address [feedUrl] replaced in its last automatic correction, kept so it can be undone.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns; section 11, address repair).
     * COMPAT(own-ids): feed address bookkeeping. Permanent: never remove (MIGRATION.md §5).
     */
    val previousFeedUrl: String? = null,
) {
    /**
     * This row with [stored]'s v39 columns, for [PodcastDao.upsert]: subscribe, restore and sync
     * build the row from scratch and never set them.
     * - [epNs] is always kept: an issued namespace never changes.
     * - The address bookkeeping ([feedUrlSource], [feedUrlCheckedAt], [transportUrl],
     *   [previousFeedUrl]) describes [feedUrl], so it is kept only while this write leaves
     *   [feedUrl] unchanged. A write that changes the address writes its own values.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns).
     * COMPAT(own-ids): keeps the seeded namespace. Permanent: never remove (MIGRATION.md §5).
     */
    internal fun keepingFeedIdentityColumnsOf(stored: PodcastEntity): PodcastEntity {
        val sameAddress = feedUrl == stored.feedUrl
        return copy(
            epNs = stored.epNs,
            feedUrlSource = if (sameAddress) stored.feedUrlSource else feedUrlSource,
            feedUrlCheckedAt = if (sameAddress) stored.feedUrlCheckedAt else feedUrlCheckedAt,
            transportUrl = if (sameAddress) stored.transportUrl else transportUrl,
            previousFeedUrl = if (sameAddress) stored.previousFeedUrl else previousFeedUrl,
        )
    }

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
