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
    /** The listening origin whose listening it sums (as [ListeningSessionEntity.deviceId]); [THIS_DEVICE] until claimed (Room v40). */
    @ColumnInfo(defaultValue = "''")
    val deviceId: String = THIS_DEVICE,
    /** Listening time at the playback speed; 0 when not known (Room v40). */
    @ColumnInfo(defaultValue = "0")
    val wallMs: Long = 0L,
) {
    /** This summary with nothing listened: what a push sends for a day this phone no longer holds. */
    fun zeroed() = copy(consumedMs = 0L, sessionCount = 0, completionCount = 0, morningMs = 0L, afternoonMs = 0L, eveningMs = 0L, nightMs = 0L, wallMs = 0L)

    fun isZero(): Boolean = consumedMs == 0L && sessionCount == 0

    companion object {
        /** [deviceId] of summaries recorded before Room v40 that no installation has claimed yet. */
        const val THIS_DEVICE = ""
    }
}
