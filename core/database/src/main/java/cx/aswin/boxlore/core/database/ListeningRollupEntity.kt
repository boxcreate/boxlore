package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Compact daily per-episode summary created when raw sessions age past the retention window.
 * Keyed by phone + local day + episode so the same show can still have fresh raw sessions today,
 * and another phone's day (a cloud sync pull or a restored backup) never merges into this phone's.
 */
@Entity(
    tableName = "listening_rollups",
    primaryKeys = ["deviceId", "localDay", "episodeId"],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["localDay"]),
        Index(value = ["episodeId"]),
    ],
)
data class ListeningRollupEntity(
    val localDay: Long,
    val episodeId: String,
    val podcastId: String,
    val consumedMs: Long,
    val sessionCount: Int,
    val completionCount: Int,
    val lastListenedAt: Long,
    val morningMs: Long = 0L,
    val afternoonMs: Long = 0L,
    val eveningMs: Long = 0L,
    val nightMs: Long = 0L,
    /** The phone whose listening it sums: [THIS_DEVICE] for this phone, else that phone's sync device ID (Room v40). */
    @ColumnInfo(defaultValue = "''")
    val deviceId: String = THIS_DEVICE,
    /** Listening time at the playback speed; 0 when not known (Room v40). */
    @ColumnInfo(defaultValue = "0")
    val wallMs: Long = 0L,
) {
    companion object {
        /** [deviceId] of this phone's own summaries. */
        const val THIS_DEVICE = ""
    }
}
