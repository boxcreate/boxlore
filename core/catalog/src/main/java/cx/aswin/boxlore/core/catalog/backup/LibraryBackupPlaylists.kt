package cx.aswin.boxlore.core.catalog.backup

import android.util.Log
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.normalizePlaylistPerShowLimit
import kotlinx.coroutines.CancellationException

/**
 * A playlist in a JSON library backup (version 11+): its name, show IDs in order, [mix]
 * (`PlaylistMix.stored`), episodes per show ([perShowLimit], left out for no limit) and genre
 * [iconKey]. [order] (`newest`/`oldest`) is only read, from backups written before playlists had a
 * mix. Fields a file may leave out are nullable, since Gson leaves them null.
 */
data class PlaylistBackup(
    val id: String?,
    val name: String?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val order: String? = null,
    val showIds: List<String>? = null,
    val mix: String? = null,
    val perShowLimit: Int? = null,
    val iconKey: String? = null,
)

/**
 * Playlists in JSON library backups. Restoring only adds: a playlist this phone already has keeps
 * its name and settings and gains the backup's shows it lacks, at its end; a new one is added with
 * its ID, so restoring the same backup twice adds nothing.
 */
object LibraryBackupPlaylists {
    /** A backup's `order` from before playlists had a mix. */
    private const val LEGACY_OLDEST = "oldest"

    suspend fun export(repository: PlaylistRepository): List<PlaylistBackup> {
        val showIds = repository.getShowIds()
        return repository.getPlaylists().map { playlist ->
            PlaylistBackup(
                id = playlist.id,
                name = playlist.name,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                showIds = showIds[playlist.id].orEmpty(),
                mix = playlist.mix.stored,
                perShowLimit = playlist.perShowLimit,
                iconKey = playlist.iconKey,
            )
        }
    }

    /** Returns how many shows were added across all playlists. */
    suspend fun restore(playlists: List<PlaylistBackup>?, repository: PlaylistRepository): Int {
        if (playlists.isNullOrEmpty()) return 0
        var added = 0
        for (backup in playlists.distinctBy { it.id }.filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() }) {
            // Gson can write JSON null into the list; such entries are dropped.
            val showIds = backup.showIds.orEmpty().filter { (it as String?)?.isNotBlank() == true }
            // One unreadable playlist never stops the rest of the restore.
            added += try {
                repository.importPlaylist(backup.toPlaylist(), showIds)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("LibraryBackup", "Playlist restore skipped", error)
                0
            }
        }
        return added
    }

    /**
     * The playlist [this] describes. A backup without a mix predates mixes and plays as it did then:
     * `oldest` one show at a time, anything else newest first, with no limit per show.
     */
    private fun PlaylistBackup.toPlaylist(): Playlist {
        val legacy = mix.isNullOrBlank()
        return Playlist(
            id = id.orEmpty(),
            name = name.orEmpty(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            showCount = 0,
            mix = when {
                !legacy -> PlaylistMix.fromStored(mix)
                order == LEGACY_OLDEST -> PlaylistMix.ONE_SHOW_AT_A_TIME
                else -> PlaylistMix.NEWEST_FIRST
            },
            perShowLimit = if (legacy) null else normalizePlaylistPerShowLimit(perShowLimit),
            iconKey = iconKey?.takeIf(String::isNotBlank),
        )
    }
}
