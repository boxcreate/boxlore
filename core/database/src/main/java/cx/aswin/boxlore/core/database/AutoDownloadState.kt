package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local device activation boundary; cloud preference restores never replay the archive. */
@Entity(tableName = "auto_download_shows")
data class AutoDownloadShowEntity(@PrimaryKey val podcastId: String, val enabledAt: Long)

/** Survives removal of audio/Room download rows and process death around enqueue. */
@Entity(tableName = "auto_download_releases", indices = [Index(value = ["podcastId", "state"])])
data class AutoDownloadReleaseEntity(
    @PrimaryKey val episodeId: String,
    val podcastId: String,
    val state: String = PENDING,
) {
    companion object {
        const val PENDING = "pending"
        const val HANDLED = "handled"
        const val REMOVED = "removed"
    }
}
