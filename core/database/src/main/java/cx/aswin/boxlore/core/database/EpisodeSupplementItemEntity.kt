package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript

/**
 * Cached episode rows for a PI show's RSS supplement.
 *
 * Mirrors [RssEpisodeEntity] fields, but [podcastId] is the Podcast Index owner id
 * and is **not** a foreign key to `podcasts` (no CASCADE on unsubscribe).
 */
@Entity(
    tableName = "episode_supplement_items",
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["podcastId", "publishedDate"]),
    ],
)
data class EpisodeSupplementItemEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,
    val guid: String?,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val duration: Int,
    val publishedDate: Long,
    val chaptersUrl: String?,
    val transcriptUrl: String?,
    val transcripts: List<Transcript>?,
    val persons: List<Person>?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeType: String?,
    val enclosureType: String?,
) {
    init {
        require(episodeId.toLongOrNull()?.let { it < 0L } == true) {
            "Supplement episode IDs must be negative Long values"
        }
    }

    fun toEpisode(
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): Episode =
        Episode(
            id = episodeId,
            title = title,
            description = description,
            audioUrl = audioUrl,
            imageUrl = imageUrl?.takeIf { it.isNotBlank() } ?: podcastImageUrl?.takeIf { it.isNotBlank() },
            podcastImageUrl = podcastImageUrl?.takeIf { it.isNotBlank() },
            podcastTitle = podcastTitle,
            podcastId = podcastId,
            podcastGenre = podcastGenre,
            podcastArtist = podcastArtist,
            duration = duration,
            publishedDate = publishedDate,
            chaptersUrl = chaptersUrl,
            transcriptUrl = transcriptUrl,
            transcripts = transcripts,
            persons = persons,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeType = episodeType,
            enclosureType = enclosureType,
        )
}
