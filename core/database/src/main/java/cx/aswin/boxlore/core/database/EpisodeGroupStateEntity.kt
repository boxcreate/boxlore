package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Like and completion settings shared by every ID of one episode (docs/EPISODE_IDS.md §5).
 *
 * An episode can be stored under several IDs. A like or completion is written to every trusted
 * alias and recorded here, so an older alias row can't bring back a cleared like or completion.
 * The `*ClearedAt` times record the last clear; [version] increases with every write. The table
 * starts empty at the v39 upgrade: with no group row, an episode's state is its rows' state, as before.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, `episode_group_state`).
 * COMPAT(own-ids): keeps one listener state per episode across its IDs. Permanent: never remove (MIGRATION.md §5).
 */
@Entity(
    tableName = "episode_group_state",
    primaryKeys = ["podcastId", "catalogKey"],
)
data class EpisodeGroupStateEntity(
    val podcastId: String,
    val catalogKey: String,
    @ColumnInfo(defaultValue = "0")
    val likedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val likeClearedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val completedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val completeClearedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val version: Long = 0L,
)
