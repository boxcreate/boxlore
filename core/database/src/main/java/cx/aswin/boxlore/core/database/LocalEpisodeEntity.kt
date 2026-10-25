package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
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
    /**
     * The row existed when Room v39 added episode aliases. For legacy rows only, a [guid] equal to
     * `"id:"` plus the row's own [episodeId] is a synthetic key, not a real one. A write that
     * replaces the row from a feed read clears the mark, because its key now comes from the feed.
     *
     * The column's schema default is 1, so rows that predate v39 read as legacy without the
     * upgrade rewriting them. Room binds this field on every insert and replace, so rows the app
     * writes store false; raw SQL that inserts into `local_episodes` must set it.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 4, synthetic keys; section 8, Room v39).
     * COMPAT(own-ids): tells synthetic legacy keys from real ones. Permanent: never remove (MIGRATION.md §5).
     */
    @ColumnInfo(defaultValue = "1")
    val legacy: Boolean = false,
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
data class LocalEpisodeIdentity(val episodeId: String, val guid: String, val audioUrl: String,)

/** Which (show, catalog key) holds a local episode ID; [guid] stores the catalog key. */
data class LocalEpisodeOwner(val episodeId: String, val podcastId: String, val guid: String,)
