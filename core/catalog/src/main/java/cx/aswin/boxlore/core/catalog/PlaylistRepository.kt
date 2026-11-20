package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PLAYLIST_DEFAULT_PER_SHOW_LIMIT
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import kotlinx.coroutines.flow.Flow

/**
 * The listener's playlists (Room v46): each a name, a set of shows, a [PlaylistMix], a limit of
 * episodes per show (null for none) and an optional icon key. What a playlist plays is worked out
 * when asked ([nextEpisodes]): the unheard stored episodes of its followed, unhidden shows, any the
 * listener is midway through first, then mixed by the playlist's settings. Playlists stay on this
 * phone: they go into library backups and Android backup, never the cloud sync, and an account
 * switch deletes them with the folders.
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

    /**
     * Creates a playlist named [name] (normalised; blank becomes "Playlist") holding [podcastIds] in
     * order, mixed by [mix] with at most [perShowLimit] episodes per show (null or 0 for no limit) and
     * genre icon [iconKey] (null or blank for none).
     */
    suspend fun createPlaylist(
        name: String,
        podcastIds: List<String> = emptyList(),
        mix: PlaylistMix = PlaylistMix.TAKE_TURNS,
        perShowLimit: Int? = PLAYLIST_DEFAULT_PER_SHOW_LIMIT,
        iconKey: String? = null,
    ): Playlist

    /** False when the playlist doesn't exist or [name] is blank. */
    suspend fun renamePlaylist(playlistId: String, name: String): Boolean

    /** Sets how the playlist mixes its shows and how many episodes each adds (null or 0 for no limit). False when it doesn't exist. */
    suspend fun setPlayback(playlistId: String, mix: PlaylistMix, perShowLimit: Int?): Boolean

    /** Sets the playlist's genre icon key (null or blank for none). False when it doesn't exist. */
    suspend fun setIcon(playlistId: String, iconKey: String?): Boolean

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
     * Up to [limit] episodes [playlistId] plays next: unheard (with audio, not trailers, not finished
     * or archived, not under any ID of [excludeIds], which is what is already queued), from its
     * followed and unhidden shows; any midway through first, most recently played first, then by the
     * playlist's mix, at most its limit from each show:
     * - [PlaylistMix.TAKE_TURNS]: each show's newest of the last `PLAYLIST_TAKE_TURNS_WINDOW_DAYS`
     *   days, shows taking turns in playlist order (every show's newest, then every show's second
     *   newest, and so on).
     * - [PlaylistMix.NEWEST_FIRST]: each show's newest, merged by release date.
     * - [PlaylistMix.ONE_SHOW_AT_A_TIME]: show after show in playlist order, each oldest first.
     *
     * An unfinished episode taken out of Up next comes back here: the queue's removals can't tell it
     * from a queue Play replaced, so Mark played is how a listener drops one for good.
     */
    suspend fun nextEpisodes(playlistId: String, limit: Int, excludeIds: Collection<String> = emptyList()): List<Episode>

    /**
     * A playlist from a library backup: added with its name, mix, limit per show and icon when this
     * phone doesn't have [playlist]'s ID, otherwise all of those stay as they are; then [podcastIds]
     * it doesn't hold are added to its end, in order.
     * Returns how many shows were added.
     */
    suspend fun importPlaylist(playlist: Playlist, podcastIds: List<String>): Int

    /** Deletes every playlist (account switch). */
    suspend fun deleteAll()
}
