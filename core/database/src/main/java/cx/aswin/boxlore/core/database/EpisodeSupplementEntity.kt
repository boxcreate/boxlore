package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-podcast metadata for a Podcast Index episode supplement (cached RSS feed
 * used to fill episode lists for PI-backed shows that are not RSS subscriptions).
 *
 * [podcastId] is the Podcast Index id. This table is **not** the RSS subscription
 * catalog (`rss_episodes` / subscribed feeds).
 */
@Entity(tableName = "episode_supplements")
data class EpisodeSupplementEntity(
    @PrimaryKey
    val podcastId: String,
    val feedUrl: String,
    /** Deterministic `rss:sha…` namespace id for supplement episode identity. */
    val rssNamespaceId: String,
    val feedEtag: String?,
    val feedLastModified: String?,
    val fetchedAt: Long,
)
