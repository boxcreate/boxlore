package cx.aswin.boxlore.core.catalog.backup

import android.util.Log
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import kotlinx.coroutines.CancellationException

/**
 * A playlist in a JSON library backup (version 11+): its name, episode order (`newest` or
 * `oldest`) and show IDs in order. Fields a file may leave out are nullable, since Gson leaves them null.
 */
data class PlaylistBackup(
    val id: String?,
    val name: String?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val order: String? = null,
    val showIds: List<String>? = null,
)

/**
 * Playlists in JSON library backups. Restoring only adds: a playlist this phone already has keeps
 * its name and order and gains the backup's shows it lacks, at its end; a new one is added with its
 * ID, so restoring the same backup twice adds nothing.
 */
object LibraryBackupPlaylists {
    suspend fun export(repository: PlaylistRepository): List<PlaylistBackup> {
        val showIds = repository.getShowIds()
        return repository.getPlaylists().map { playlist ->
            PlaylistBackup(
                id = playlist.id,
                name = playlist.name,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                order = playlist.order.stored,
                showIds = showIds[playlist.id].orEmpty(),
            )
        }
    }

    /** Returns how many shows were added across all playlists. */
    suspend fun restore(playlists: List<PlaylistBackup>?, repository: PlaylistRepository): Int {
        if (playlists.isNullOrEmpty()) return 0
        var added = 0
        for (backup in playlists.distinctBy { it.id }.filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() }) {
            val playlist = Playlist(
                id = backup.id.orEmpty(),
                name = backup.name.orEmpty(),
                createdAt = backup.createdAt,
                updatedAt = backup.updatedAt,
                showCount = 0,
                order = PlaylistOrder.fromStored(backup.order),
            )
            // Gson can write JSON null into the list; such entries are dropped.
            val showIds = backup.showIds.orEmpty().filter { (it as String?)?.isNotBlank() == true }
            // One unreadable playlist never stops the rest of the restore.
            added += try {
                repository.importPlaylist(playlist, showIds)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("LibraryBackup", "Playlist restore skipped", error)
                0
            }
        }
        return added
    }
}
