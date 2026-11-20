package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.model.PlaylistShow
import kotlinx.coroutines.flow.Flow

/**
 * The listener's playlists (Room v44): each a name, an episode order and a set of shows. What a
 * playlist plays is worked out when asked ([nextEpisodes]): the unfinished stored episodes of its
 * followed, unhidden shows, any the listener is midway through first, then by release date in the
 * playlist's order. Playlists stay on this phone: they go into library backups and Android backup,
 * never the cloud sync, and an account switch deletes them with the folders.
 */
@Suppress("TooManyFunctions")
interface PlaylistRepository {
    /** Every playlist, oldest first. */
    val playlists: Flow<List<Playlist>>

    fun playlist(playlistId: String): Flow<Playlist?>

    /** [playlistId]'s shows in playlist order, each marked when it is no longer followed or is hidden. */
    fun shows(playlistId: String): Flow<List<PlaylistShow>>

    /** The IDs of the playlists holding show [podcastId]. */
    fun playlistIdsForShow(podcastId: String): Flow<Set<String>>

    suspend fun getPlaylists(): List<Playlist>

    /** Every playlist's show IDs in playlist order, by playlist ID (for backups). */
    suspend fun getShowIds(): Map<String, List<String>>

    /** Creates a playlist named [name] (normalised; blank becomes "Playlist") holding [podcastIds] in order. */
    suspend fun createPlaylist(name: String, podcastIds: List<String> = emptyList()): Playlist

    /** False when the playlist doesn't exist or [name] is blank. */
    suspend fun renamePlaylist(playlistId: String, name: String): Boolean

    /** False when the playlist doesn't exist. */
    suspend fun setOrder(playlistId: String, order: PlaylistOrder): Boolean

    suspend fun deletePlaylist(playlistId: String)

    /** Adds [podcastIds] after the playlist's last show, skipping ones it holds; returns how many were added. */
    suspend fun addShows(playlistId: String, podcastIds: List<String>): Int

    /** Removes [podcastId] from [playlistId] and returns the removed show, for [restoreShow]. Null when it wasn't there. */
    suspend fun removeShow(playlistId: String, podcastId: String): PlaylistShow?

    /**
     * Moves show [oldPodcastId]'s places in every playlist to [newPodcastId] (a show Podcast Index
     * renumbered, or followed through its feed instead). A playlist already holding [newPodcastId]
     * keeps it once.
     */
    suspend fun replaceShow(oldPodcastId: String, newPodcastId: String)

    /** Puts a [removed] show back in its place (Undo). False when its playlist is gone or holds it again. */
    suspend fun restoreShow(removed: PlaylistShow): Boolean

    /**
     * Up to [limit] episodes [playlistId] plays next: unfinished, with audio, not trailers, from its
     * followed and unhidden shows; any midway through first, then by release date in the playlist's
     * order. Episodes under any ID of [excludeIds] (what is already queued) are left out. An
     * unfinished episode taken out of Up next comes back here: the queue's removals can't tell it
     * from a queue Play replaced, so Mark played is how a listener drops one for good.
     */
    suspend fun nextEpisodes(playlistId: String, limit: Int, excludeIds: Collection<String> = emptyList()): List<Episode>

    /**
     * A playlist from a library backup: added when this phone doesn't have [playlist]'s ID, otherwise
     * its name and order stay; then [podcastIds] it doesn't hold are added to its end, in order.
     * Returns how many shows were added.
     */
    suspend fun importPlaylist(playlist: Playlist, podcastIds: List<String>): Int

    /** Deletes every playlist (account switch). */
    suspend fun deleteAll()
}
