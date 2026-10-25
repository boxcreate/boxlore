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
 * yet, and whole-row writers (`upsertFeed`) reset them to their defaults until they carry them
 * from the stored row.
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
    /** The show is followed. Seeded from `podcasts.isSubscribed` at the v39 upgrade. */
    @ColumnInfo(defaultValue = "0")
    val followed: Boolean = false,
    /** When the show page last opened this catalog (epoch ms); 0 when never. Drives cleanup of unfollowed shows. */
    @ColumnInfo(defaultValue = "0")
    val lastOpenedAt: Long = 0L,
    /** Every item of the feed has been read, not only the newest part. Separate from [ready]. */
    @ColumnInfo(defaultValue = "0")
    val archiveComplete: Boolean = false,
    /** Episode namespace for this catalog's recipe IDs; seeded as the show ID at the v39 upgrade. */
    val epNs: Long? = null,
    /** Show details (JSON) kept for shows that are cached but not followed, which may have no `podcasts` row. */
    val showMetadata: String? = null,
    /** When [showMetadata] was written (epoch ms); 0 when never. */
    @ColumnInfo(defaultValue = "0")
    val showMetadataAt: Long = 0L,
)

object LocalFeedOrder {
    const val NEWEST_FIRST = "newest_first"
    const val OLDEST_FIRST = "oldest_first"
    const val MIXED = "mixed"
}
