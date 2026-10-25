package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-show metadata for a first-class local episode catalog owned by a
 * Podcast Index id (not an `rss:` library row).
 *
 * No foreign key to `podcasts` — unsubscribe must not CASCADE-wipe history
 * targets. [ttlExpiresAt] is set on unsubscribe; null while subscribed.
 * A non-null TTL means the catalog is not ready for live lists: unsubscribed
 * reads use Podcast Index until resubscribe clears the TTL.
 *
 * Room v39 adds the feed-first columns ([followed] through [showMetadataAt]). Nothing reads them
 * yet. [LocalEpisodeCatalogDao.upsertFeed] keeps them when it replaces a stored row, so they change
 * only through their own updates.
 */
@Entity(tableName = "local_episode_feeds")
data class LocalEpisodeFeedEntity(
    @PrimaryKey
    val podcastId: String,
    val feedUrl: String,
    val feedEtag: String?,
    val feedLastModified: String?,
    val fetchedAt: Long,
    val itemCount: Int,
    val feedOrder: String,
    val ttlExpiresAt: Long?,
    val needsFullBackfill: Boolean,
    val copiedExtrasCount: Int,
    val ready: Boolean,
    val feedUrlLookupAt: Long,
    /**
     * The show is followed. Seeded from `podcasts.isSubscribed` at the v39 upgrade; follow and
     * unfollow don't update it yet, so whatever reads it first must also write it.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): feed-first catalog state. Permanent: never remove (MIGRATION.md §5).
     */
    @ColumnInfo(defaultValue = "0")
    val followed: Boolean = false,
    /**
     * When the show page last opened this catalog (epoch ms); 0 when never. Drives cleanup of unfollowed shows.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): feed-first catalog state. Permanent: never remove (MIGRATION.md §5).
     */
    @ColumnInfo(defaultValue = "0")
    val lastOpenedAt: Long = 0L,
    /**
     * Every item of the feed has been read, not only the newest part. Separate from [ready].
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): feed-first catalog state. Permanent: never remove (MIGRATION.md §5).
     */
    @ColumnInfo(defaultValue = "0")
    val archiveComplete: Boolean = false,
    /**
     * Episode namespace for this catalog's recipe IDs; seeded as the show ID at the v39 upgrade and
     * never changed once an ID has been issued under it. Null when not set yet.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns; decision D12).
     * COMPAT(own-ids): issued recipe IDs depend on it. Permanent: never remove (MIGRATION.md §5).
     */
    val epNs: Long? = null,
    /**
     * Show details (JSON) kept for shows that are cached but not followed, which may have no `podcasts` row.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): feed-first catalog state. Permanent: never remove (MIGRATION.md §5).
     */
    val showMetadata: String? = null,
    /**
     * When [showMetadata] was written (epoch ms); 0 when never.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): feed-first catalog state. Permanent: never remove (MIGRATION.md §5).
     */
    @ColumnInfo(defaultValue = "0")
    val showMetadataAt: Long = 0L,
) {
    /**
     * This row with [stored]'s v39 feed-first columns, for [LocalEpisodeCatalogDao.upsertFeed]:
     * writers that rebuild the row from a feed read never set them.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): keeps the seeded namespace and follow state. Permanent: never remove (MIGRATION.md §5).
     */
    internal fun keepingFeedFirstColumnsOf(stored: LocalEpisodeFeedEntity): LocalEpisodeFeedEntity = copy(
        followed = stored.followed,
        lastOpenedAt = stored.lastOpenedAt,
        archiveComplete = stored.archiveComplete,
        epNs = stored.epNs,
        showMetadata = stored.showMetadata,
        showMetadataAt = stored.showMetadataAt,
    )
}

object LocalFeedOrder {
    const val NEWEST_FIRST = "newest_first"
    const val OLDEST_FIRST = "oldest_first"
    const val MIXED = "mixed"
}
