package cx.aswin.boxlore.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A listener's playlist (Room v43). [playlistId] is a random UUID; the playlist's episodes are [PlaylistItemEntity] rows. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val playlistId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A playlist with how many episodes it holds, for list screens. */
data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    val itemCount: Int,
)

/** One of the first [PlaylistDao.COVER_LIMIT] episodes' artwork of a playlist, in playlist order. */
data class PlaylistCover(
    val playlistId: String,
    val imageUrl: String?,
)
