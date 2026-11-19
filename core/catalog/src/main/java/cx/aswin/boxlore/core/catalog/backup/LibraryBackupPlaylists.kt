package cx.aswin.boxlore.core.catalog.backup

import android.util.Log
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import cx.aswin.boxlore.core.model.Transcript
import kotlinx.coroutines.CancellationException

/**
 * A playlist in a JSON library backup (version 11+), with its episodes in order. Fields a file may
 * leave out are nullable, since Gson leaves them null.
 */
data class PlaylistBackup(
    val id: String?,
    val name: String?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val episodes: List<PlaylistEpisodeBackup>? = null,
)

/** One playlist episode: the same episode copy the playlist keeps, so it plays without the catalog. */
data class PlaylistEpisodeBackup(
    val episodeId: String?,
    val title: String?,
    val audioUrl: String?,
    val addedAt: Long = 0L,
    val podcastId: String? = null,
    val podcastTitle: String? = null,
    val podcastImageUrl: String? = null,
    val podcastGenre: String? = null,
    val podcastArtist: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val duration: Int = 0,
    val publishedDate: Long = 0L,
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val persons: List<Person>? = null,
    val transcripts: List<Transcript>? = null,
    val episodeType: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val enclosureType: String? = null,
)

/**
 * Playlists in JSON library backups. Restoring merges: a playlist this phone already has keeps its
 * name and gains the backup's episodes it lacks, at its end; a new one is added with its ID, so
 * restoring the same backup twice adds nothing.
 */
object LibraryBackupPlaylists {
    suspend fun export(repository: PlaylistRepository): List<PlaylistBackup> =
        repository.getPlaylists().map { playlist ->
            PlaylistBackup(
                id = playlist.id,
                name = playlist.name,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                episodes = repository.getEpisodes(playlist.id).map { it.toBackup() },
            )
        }

    /**
     * Returns how many episodes were added across all playlists. Rows under [refusedIds] (IDs this
     * phone holds for another episode, from the identity restore) are skipped, as history skips them.
     */
    suspend fun restore(playlists: List<PlaylistBackup>?, repository: PlaylistRepository, refusedIds: Set<String> = emptySet(),): Int {
        if (playlists.isNullOrEmpty()) return 0
        var added = 0
        for (backup in playlists.distinctBy { it.id }.filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() }) {
            val id = backup.id.orEmpty()
            val playlist = Playlist(id, backup.name.orEmpty(), backup.createdAt, backup.updatedAt, episodeCount = 0)
            val rows = backup.episodes.orEmpty().filterNot { it.episodeId in refusedIds }.mapIndexedNotNull { index, episode ->
                episode.toEpisode()?.let { PlaylistEpisode(itemId = 0, playlistId = id, position = index, addedAt = episode.addedAt, episode = it) }
            }
            // One unreadable playlist never stops the rest of the restore.
            added += try {
                repository.importPlaylist(playlist, rows).added
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("LibraryBackup", "Playlist restore skipped", error)
                0
            }
        }
        return added
    }

    private fun PlaylistEpisode.toBackup() = PlaylistEpisodeBackup(
        episodeId = episode.id,
        title = episode.title,
        audioUrl = episode.audioUrl,
        addedAt = addedAt,
        podcastId = episode.podcastId,
        podcastTitle = episode.podcastTitle,
        podcastImageUrl = episode.podcastImageUrl,
        podcastGenre = episode.podcastGenre,
        podcastArtist = episode.podcastArtist,
        imageUrl = episode.imageUrl,
        description = episode.description.takeIf(String::isNotEmpty),
        duration = episode.duration,
        publishedDate = episode.publishedDate,
        chaptersUrl = episode.chaptersUrl,
        transcriptUrl = episode.transcriptUrl,
        persons = episode.persons,
        transcripts = episode.transcripts,
        episodeType = episode.episodeType,
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
        enclosureType = episode.enclosureType,
    )

    /** Null for a row a backup can't play: no ID or no audio. */
    private fun PlaylistEpisodeBackup.toEpisode(): Episode? {
        val id = episodeId?.takeIf(String::isNotBlank) ?: return null
        val audio = audioUrl?.takeIf(String::isNotBlank) ?: return null
        return Episode(
            id = id,
            title = title.orEmpty(),
            description = description.orEmpty(),
            audioUrl = audio,
            imageUrl = imageUrl,
            podcastImageUrl = podcastImageUrl,
            podcastTitle = podcastTitle,
            podcastId = podcastId,
            podcastGenre = podcastGenre,
            podcastArtist = podcastArtist,
            duration = duration,
            publishedDate = publishedDate,
            chaptersUrl = chaptersUrl,
            transcriptUrl = transcriptUrl,
            persons = persons,
            transcripts = transcripts,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeType = episodeType,
            enclosureType = enclosureType,
        )
    }
}
