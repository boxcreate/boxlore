package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Like, completion and archive settings shared by every ID of one episode (docs/EPISODE_IDS.md §5).
 *
 * An episode can be stored under several IDs. A like, completion or archive is written to every trusted
 * alias and recorded here, so an older alias row can't bring back a cleared one.
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
    /** The last archive of the episode (Room v45; [ListeningHistoryEntity.dismissedAt]), 0 when none. */
    @ColumnInfo(defaultValue = "0")
    val dismissedAt: Long = 0L,
    /** The last time the episode was brought back from the archive, 0 when never. */
    @ColumnInfo(defaultValue = "0")
    val dismissClearedAt: Long = 0L,
)

/** [this] group settings with only the archive decision ([ListeningHistoryEntity.archiveOnly]). */
fun EpisodeGroupStateEntity.archiveOnly(): EpisodeGroupStateEntity =
    copy(likedAt = 0L, likeClearedAt = 0L, completedAt = 0L, completeClearedAt = 0L, version = version + 1L)
