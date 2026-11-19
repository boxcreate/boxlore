package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import kotlinx.coroutines.flow.Flow

/** What adding episodes to a playlist did. */
data class PlaylistAddResult(
    val added: Int,
    /** Episodes the playlist already held, under any of their IDs. */
    val alreadyIn: Int = 0,
    /** Episodes left out because the playlist reached [cx.aswin.boxlore.core.model.PLAYLIST_EPISODE_LIMIT]. */
    val didNotFit: Int = 0,
)

/** A new playlist and what adding its first episodes did. */
data class PlaylistCreated(val playlist: Playlist, val result: PlaylistAddResult)

/**
 * The listener's playlists (Room v43). A playlist keeps its episodes in the listener's order, holds
 * an episode once across all of the episode's stored IDs (the queue's rule, docs/EPISODE_IDS.md §5),
 * and holds at most [cx.aswin.boxlore.core.model.PLAYLIST_EPISODE_LIMIT] episodes. Playlists stay on
 * this phone: they go into library backups and Android backup, never the cloud sync, and an
 * account switch deletes them with the folders.
 */
@Suppress("TooManyFunctions")
interface PlaylistRepository {
    /** Every playlist, oldest first. */
    val playlists: Flow<List<Playlist>>

    fun playlist(playlistId: String): Flow<Playlist?>

    /** [playlistId]'s episodes in playlist order. */
    fun episodes(playlistId: String): Flow<List<PlaylistEpisode>>

    suspend fun getPlaylists(): List<Playlist>

    suspend fun getEpisodes(playlistId: String): List<PlaylistEpisode>

    /** Creates a playlist named [name] (normalised; blank becomes "Playlist") holding [episodes]. */
    suspend fun createPlaylist(name: String, episodes: List<Episode> = emptyList()): PlaylistCreated

    /** False when the playlist doesn't exist or [name] is blank. */
    suspend fun renamePlaylist(playlistId: String, name: String): Boolean

    suspend fun deletePlaylist(playlistId: String)

    /** Adds [episodes] to the end of [playlistId], in order, skipping ones it holds and ones past the limit. */
    suspend fun addEpisodes(playlistId: String, episodes: List<Episode>): PlaylistAddResult

    /** Removes the rows [itemIds] from [playlistId] and returns them, for [restoreEpisodes]. */
    suspend fun removeEpisodes(playlistId: String, itemIds: Collection<Long>): List<PlaylistEpisode>

    /** Puts [removed] rows back in their places (Undo). Returns how many came back. */
    suspend fun restoreEpisodes(removed: List<PlaylistEpisode>): Int

    /** Puts [playlistId]'s rows in the order of [orderedItemIds]; rows left out keep their order after them. */
    suspend fun reorder(playlistId: String, orderedItemIds: List<Long>)

    /**
     * A playlist from a library backup: added when this phone doesn't have [playlist]'s ID, otherwise
     * its name stays; then [episodes] it doesn't hold are added to its end, in order, up to the limit.
     */
    suspend fun importPlaylist(playlist: Playlist, episodes: List<PlaylistEpisode>): PlaylistAddResult

    /** Deletes every playlist (account switch). */
    suspend fun deleteAll()
}
