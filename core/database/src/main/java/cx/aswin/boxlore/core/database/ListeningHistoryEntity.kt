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
)
