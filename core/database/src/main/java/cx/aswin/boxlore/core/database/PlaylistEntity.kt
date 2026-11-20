package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A listener's playlist (Room v44). [playlistId] is a random UUID; the playlist's shows are
 * [PlaylistShowEntity] rows. [episodeOrder] is `newest` or `oldest` (`PlaylistOrder.stored`).
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val playlistId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'newest'")
    val episodeOrder: String = "newest",
)

/**
 * One show in a playlist (Room v44). A show is in a playlist at most once; deleting the playlist
 * deletes its rows. Rows of a show the listener stops following stay, so following it again brings it back.
 */
@Entity(
    tableName = "playlist_shows",
    primaryKeys = ["playlistId", "podcastId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["podcastId"])],
)
data class PlaylistShowEntity(
    val playlistId: String,
    val podcastId: String,
    val position: Int,
    val addedAt: Long,
)

/** A playlist with how many shows it holds, for list screens. */
data class PlaylistSummary(
    @androidx.room.Embedded val playlist: PlaylistEntity,
    val showCount: Int,
)

/** One of the first [PlaylistDao.COVER_LIMIT] shows' artwork of a playlist, in playlist order. */
data class PlaylistCover(
    val playlistId: String,
    val imageUrl: String?,
)

/** A playlist show with the stored show's title, artwork and follow state; null fields when the show isn't stored. */
data class PlaylistShowRow(
    val playlistId: String,
    val podcastId: String,
    val position: Int,
    val addedAt: Long,
    val title: String?,
    val imageUrl: String?,
    val author: String?,
    val genre: String?,
    val isSubscribed: Boolean,
)

/**
 * A stored episode a playlist can play, from `local_episodes` or `rss_episodes`. People and
 * transcripts are left out: the queue fetches them when the episode plays.
 */
data class PlaylistEpisodeRow(
    val episodeId: String,
    val podcastId: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val duration: Int,
    val publishedDate: Long,
    val chaptersUrl: String?,
    val transcriptUrl: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeType: String?,
    val enclosureType: String?,
)
