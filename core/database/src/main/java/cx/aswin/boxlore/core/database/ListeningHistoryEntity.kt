package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_history",
    indices = [
        Index(value = ["isDirty"]),
        Index(value = ["lastPlayedAt"]),
    ],
)
data class ListeningHistoryEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,

    // Cached Metadata (to avoid API calls for display)
    val episodeTitle: String,
    val episodeImageUrl: String?,
    val podcastImageUrl: String?, // Fallback image if episode image fails
    val episodeAudioUrl: String?,
    val podcastName: String,

    // Playback State
    val progressMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val isLiked: Boolean = false, // New: User "Like" status
    val likedAt: Long = 0L,
    val lastPlayedAt: Long,

    // Sync Status
    val isDirty: Boolean = true, // Needs to be pushed to Supabase
    val syncedAt: Long = 0,
    val enclosureType: String? = null,
    val isManualCompletion: Boolean = false,
    val isBulkCompletion: Boolean = false,
    val episodeDescription: String? = null,

    // Listening dates (Room v40; [ListeningDates]). null: not yet. 0: before boxlore recorded them
    // (every row stored before v40). Else epoch ms. The DAO's whole-row writers keep the earliest.
    /** When the episode was first played. */
    @ColumnInfo(defaultValue = "0")
    val firstPlayedAt: Long? = null,
    /** When the episode was first finished by listening (marking it played doesn't count). */
    @ColumnInfo(defaultValue = "0")
    val finishedAt: Long? = null,

    /**
     * The listener's archive decision (Room v45; the app calls it Archive, the code "dismissed"
     * because the archive already means a show's stored feed). 0: never decided. Positive: archived
     * at that epoch ms, so the episode leaves every list without counting as listened. Negative:
     * brought back at minus that time. One signed value keeps the decision and its time together, so
     * sync and backups compare decisions by time ([dismissDecisionAt]) as likes do with `likedAt`.
     */
    @ColumnInfo(defaultValue = "0")
    val dismissedAt: Long = 0L,
)

/** True when the listener archived this row's episode ([ListeningHistoryEntity.dismissedAt]). */
val ListeningHistoryEntity.isDismissed: Boolean get() = dismissedAt > 0L

/** When the row's archive decision was made (epoch ms), either way; 0 when it was never made. */
val ListeningHistoryEntity.dismissDecisionAt: Long get() = kotlin.math.abs(dismissedAt)

/** The stored [ListeningHistoryEntity.dismissedAt] for an archive decision ([dismissed] at [at]). */
fun dismissedAtValue(dismissed: Boolean, at: Long): Long = if (dismissed) at else -at

/**
 * [this] row cut down to its archive decision: nothing played, liked or finished, and nothing to
 * push. What clearing or removing history keeps of an archived episode, so it stays archived.
 */
fun ListeningHistoryEntity.archiveOnly(): ListeningHistoryEntity = copy(
    progressMs = 0L,
    isCompleted = false,
    isLiked = false,
    likedAt = 0L,
    lastPlayedAt = 0L,
    isDirty = false,
    isManualCompletion = false,
    isBulkCompletion = false,
    firstPlayedAt = null,
    finishedAt = null,
)
