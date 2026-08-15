package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-show metadata for a first-class local episode catalog owned by a
 * Podcast Index id (not an `rss:` library row).
 *
 * No foreign key to `podcasts` — unsubscribe must not CASCADE-wipe history
 * targets. [ttlExpiresAt] is set on unsubscribe; null while subscribed.
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
)

object LocalFeedOrder {
    const val NEWEST_FIRST = "newest_first"
    const val OLDEST_FIRST = "oldest_first"
    const val MIXED = "mixed"
}
