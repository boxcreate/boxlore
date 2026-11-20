package cx.aswin.boxlore.feature.library.playlists

import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.model.PlaylistShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [PlaylistRepository]: IDs `p1`, `p2`… and [next] as every playlist's next episodes. */
internal class FakePlaylistRepository : PlaylistRepository {
    private val lists = MutableStateFlow<List<Playlist>>(emptyList())
    private val rows = MutableStateFlow<List<PlaylistShow>>(emptyList())
    private var ids = 0
    var next: List<Episode> = emptyList()
    val asked = mutableListOf<Int>()

    override val playlists: Flow<List<Playlist>> = lists.map { all -> all.map { it.withCount() } }

    override fun playlist(playlistId: String): Flow<Playlist?> = playlists.map { all -> all.firstOrNull { it.id == playlistId } }

    override fun shows(playlistId: String): Flow<List<PlaylistShow>> = rows.map { all -> all.filter { it.playlistId == playlistId }.sortedBy { it.position } }

    override fun playlistIdsForShow(podcastId: String): Flow<Set<String>> = rows.map { all -> all.filter { it.podcastId == podcastId }.map { it.playlistId }.toSet() }

    override suspend fun getPlaylists(): List<Playlist> = lists.value.map { it.withCount() }

    override suspend fun getShowIds(): Map<String, List<String>> = rows.value.groupBy({ it.playlistId }) { it.podcastId }

    override suspend fun createPlaylist(name: String, podcastIds: List<String>): Playlist {
        val playlist = Playlist("p${++ids}", name, 1L, 1L, showCount = 0)
        lists.value += playlist
        addShows(playlist.id, podcastIds)
        return playlist.withCount()
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): Boolean {
        lists.value = lists.value.map { if (it.id == playlistId) it.copy(name = name) else it }
        return true
    }

    override suspend fun setOrder(playlistId: String, order: PlaylistOrder): Boolean {
        lists.value = lists.value.map { if (it.id == playlistId) it.copy(order = order) else it }
        return true
    }

    override suspend fun deletePlaylist(playlistId: String) {
        lists.value = lists.value.filterNot { it.id == playlistId }
        rows.value = rows.value.filterNot { it.playlistId == playlistId }
    }

    override suspend fun addShows(playlistId: String, podcastIds: List<String>): Int {
        val held = rows.value.filter { it.playlistId == playlistId }
        val fresh = podcastIds.distinct().filter { id -> held.none { it.podcastId == id } }
        rows.value += fresh.mapIndexed { index, id -> PlaylistShow(playlistId, id, held.size + index, 1L, "Show $id", null, isFollowed = true) }
        return fresh.size
    }

    override suspend fun removeShow(playlistId: String, podcastId: String): PlaylistShow? {
        val row = rows.value.firstOrNull { it.playlistId == playlistId && it.podcastId == podcastId } ?: return null
        rows.value -= row
        return row
    }

    override suspend fun replaceShow(oldPodcastId: String, newPodcastId: String) {
        rows.value = rows.value.map { if (it.podcastId == oldPodcastId) it.copy(podcastId = newPodcastId) else it }
    }

    override suspend fun restoreShow(removed: PlaylistShow): Boolean {
        rows.value += removed
        return true
    }

    override suspend fun nextEpisodes(playlistId: String, limit: Int, excludeIds: Collection<String>): List<Episode> {
        asked += limit
        return next.take(limit)
    }

    override suspend fun importPlaylist(playlist: Playlist, podcastIds: List<String>): Int = 0

    override suspend fun deleteAll() {
        lists.value = emptyList()
        rows.value = emptyList()
    }

    private fun Playlist.withCount() = copy(showCount = rows.value.count { it.playlistId == id })
}
