package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PlaylistItemEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.PlaylistEpisode
import cx.aswin.boxlore.core.model.Transcript
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The episode copy a playlist row keeps, the same fields the queue keeps, so a playlist plays an
 * episode that isn't in the catalog. People and transcripts are stored as the queue's JSON arrays.
 */
internal object PlaylistEpisodeSnapshot {
    private val json = Json { ignoreUnknownKeys = true }
    private val persons = ListSerializer(Person.serializer())
    private val transcripts = ListSerializer(Transcript.serializer())

    fun toEntity(
        playlistId: String,
        episode: Episode,
        addedAt: Long,
        position: Int = 0,
        itemId: Long = 0,
    ): PlaylistItemEntity = PlaylistItemEntity(
        id = itemId,
        playlistId = playlistId,
        position = position,
        addedAt = addedAt,
        episodeId = episode.id,
        title = episode.title,
        podcastId = episode.podcastId.orEmpty(),
        podcastTitle = episode.podcastTitle.orEmpty(),
        podcastGenre = episode.podcastGenre.orEmpty(),
        podcastArtist = episode.podcastArtist.orEmpty(),
        podcastImageUrl = episode.podcastImageUrl?.takeIf(String::isNotBlank),
        imageUrl = episode.imageUrl?.takeIf(String::isNotBlank),
        audioUrl = episode.audioUrl,
        duration = episode.duration,
        pubDate = episode.publishedDate,
        description = episode.description.takeIf(String::isNotEmpty),
        chaptersUrl = episode.chaptersUrl,
        transcriptUrl = episode.transcriptUrl,
        personsJson = episode.persons?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(persons, it) },
        transcriptsJson = episode.transcripts?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(transcripts, it) },
        episodeType = episode.episodeType,
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
        enclosureType = episode.enclosureType,
    )

    fun toEpisode(entity: PlaylistItemEntity): Episode = Episode(
        id = entity.episodeId,
        title = entity.title,
        description = entity.description.orEmpty(),
        audioUrl = entity.audioUrl,
        imageUrl = entity.imageUrl,
        podcastImageUrl = entity.podcastImageUrl ?: entity.imageUrl,
        podcastTitle = entity.podcastTitle.takeIf(String::isNotEmpty),
        podcastId = entity.podcastId.takeIf(String::isNotEmpty),
        podcastGenre = entity.podcastGenre.takeIf(String::isNotEmpty),
        podcastArtist = entity.podcastArtist.takeIf(String::isNotEmpty),
        duration = entity.duration,
        publishedDate = entity.pubDate,
        chaptersUrl = entity.chaptersUrl,
        transcriptUrl = entity.transcriptUrl,
        persons = decode(entity.personsJson) { json.decodeFromString(persons, it) },
        transcripts = decode(entity.transcriptsJson) { json.decodeFromString(transcripts, it) },
        seasonNumber = entity.seasonNumber,
        episodeNumber = entity.episodeNumber,
        episodeType = entity.episodeType,
        enclosureType = entity.enclosureType,
    )

    fun toPlaylistEpisode(entity: PlaylistItemEntity): PlaylistEpisode =
        PlaylistEpisode(
            itemId = entity.id,
            playlistId = entity.playlistId,
            position = entity.position,
            addedAt = entity.addedAt,
            episode = toEpisode(entity),
        )

    fun toEntity(row: PlaylistEpisode): PlaylistItemEntity =
        toEntity(row.playlistId, row.episode, row.addedAt, row.position, row.itemId)

    private fun <T> decode(value: String?, read: (String) -> List<T>): List<T>? =
        value?.takeIf(String::isNotBlank)?.let { runCatching { read(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
}
