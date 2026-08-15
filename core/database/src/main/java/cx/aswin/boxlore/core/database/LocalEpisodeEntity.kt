package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript

/**
 * Full local episode row for a subscribed Podcast Index show.
 *
 * [episodeId] is immutable after first insert (positive PI id or minted
 * negative). [guid] is the catalog key (publisher guid, else a stable
 * synthetic from the enclosure URL).
 *
 * No FK CASCADE — history / downloads / queue keep resolving after unsubscribe.
 */
@Entity(
    tableName = "local_episodes",
    indices = [
        Index(value = ["podcastId", "guid"], unique = true),
        Index(value = ["podcastId", "publishedDate", "episodeId"]),
    ],
)
data class LocalEpisodeEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,
    val guid: String,
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
    fun toEpisode(
        podcastTitle: String? = null,
        podcastImageUrl: String? = null,
        podcastGenre: String? = null,
        podcastArtist: String? = null,
    ): Episode = Episode(
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

/** Lightweight identity map for sticky upserts (no payload). */
data class LocalEpisodeIdentity(
    val episodeId: String,
    val guid: String,
    val audioUrl: String,
)
