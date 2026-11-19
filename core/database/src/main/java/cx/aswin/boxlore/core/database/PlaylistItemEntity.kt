package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import cx.aswin.boxlore.core.network.model.EpisodeItem

/**
 * One episode in a playlist (Room v43), with the same episode snapshot as the queue's
 * [cx.aswin.boxlore.core.database.entities.QueueItem], so a playlist plays even when the episode is not
 * in the catalog. An episode is in a playlist at most once; deleting the playlist deletes its rows.
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "episodeId"], unique = true),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: String,
    val position: Int,
    val addedAt: Long,

    // Episode snapshot, as in queue_items.
    val episodeId: String,
    val title: String,
    val podcastId: String,
    val podcastTitle: String,
    val podcastGenre: String = "",
    val podcastArtist: String = "",
    val podcastImageUrl: String? = null,
    val imageUrl: String?,
    val audioUrl: String,
    val duration: Int,
    val pubDate: Long,
    val description: String?,
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val personsJson: String? = null, // JSON-serialized List<Person>
    val transcriptsJson: String? = null, // JSON-serialized List<Transcript>
    val episodeType: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val enclosureType: String? = null,
) {
    fun toEpisodeItem(): EpisodeItem = EpisodeItem(
        id = episodeId.toLongOrNull() ?: 0L,
        title = title,
        enclosureUrl = audioUrl,
        description = description ?: "",
        duration = duration,
        datePublished = pubDate,
        image = imageUrl ?: "",
        feedImage = podcastImageUrl, // Podcast artwork fallback
        episodeType = episodeType,
        season = seasonNumber,
        episodeNumber = episodeNumber,
        enclosureType = enclosureType,
    )
}
