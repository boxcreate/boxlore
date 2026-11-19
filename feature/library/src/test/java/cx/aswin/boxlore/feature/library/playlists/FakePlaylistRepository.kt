package cx.aswin.boxlore.feature.library.playlists

import cx.aswin.boxlore.core.catalog.PlaylistAddResult
import cx.aswin.boxlore.core.catalog.PlaylistCreated
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [PlaylistRepository] for ViewModel and screen tests. */
internal class FakePlaylistRepository : PlaylistRepository {
    private val lists = MutableStateFlow<List<Playlist>>(emptyList())
    private val rows = MutableStateFlow<List<PlaylistEpisode>>(emptyList())
    private var nextItem = 1L
    val reorders = mutableListOf<List<Long>>()

    override val playlists: Flow<List<Playlist>> = lists.map { all -> all.map { it.withCount() } }

    override fun playlist(playlistId: String): Flow<Playlist?> = playlists.map { all -> all.firstOrNull { it.id == playlistId } }

    override fun episodes(playlistId: String): Flow<List<PlaylistEpisode>> = rows.map { all -> ordered(all, playlistId) }

    override suspend fun getPlaylists(): List<Playlist> = lists.value.map { it.withCount() }

    override suspend fun getEpisodes(playlistId: String): List<PlaylistEpisode> = ordered(rows.value, playlistId)

    override suspend fun createPlaylist(name: String, episodes: List<Episode>): PlaylistCreated {
        val playlist = Playlist("p${lists.value.size + 1}", name, 1L, 1L, 0)
        lists.value += playlist
        val result = addEpisodes(playlist.id, episodes)
        return PlaylistCreated(playlist.withCount(), result)
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): Boolean {
        lists.value = lists.value.map { if (it.id == playlistId) it.copy(name = name) else it }
        return true
    }

    override suspend fun deletePlaylist(playlistId: String) {
        lists.value = lists.value.filterNot { it.id == playlistId }
        rows.value = rows.value.filterNot { it.playlistId == playlistId }
    }

    override suspend fun addEpisodes(playlistId: String, episodes: List<Episode>): PlaylistAddResult {
        var added = 0
        for (episode in episodes) {
            if (rows.value.none { it.playlistId == playlistId && it.episode.id == episode.id }) {
                val position = rows.value.count { it.playlistId == playlistId }
                rows.value += PlaylistEpisode(nextItem++, playlistId, position, 1L, episode)
                added++
            }
        }
        return PlaylistAddResult(added, alreadyIn = episodes.size - added)
    }

    override suspend fun removeEpisodes(playlistId: String, itemIds: Collection<Long>): List<PlaylistEpisode> {
        val removed = rows.value.filter { it.playlistId == playlistId && it.itemId in itemIds }
        rows.value -= removed.toSet()
        return removed
    }

    override suspend fun restoreEpisodes(removed: List<PlaylistEpisode>): Int {
        rows.value += removed
        return removed.size
    }

    override suspend fun reorder(playlistId: String, orderedItemIds: List<Long>) {
        reorders += orderedItemIds
        rows.value = rows.value.map { row ->
            if (row.playlistId == playlistId && row.itemId in orderedItemIds) row.copy(position = orderedItemIds.indexOf(row.itemId)) else row
        }
    }

    override suspend fun importPlaylist(playlist: Playlist, episodes: List<PlaylistEpisode>): PlaylistAddResult = PlaylistAddResult(0)

    override suspend fun deleteAll() {
        lists.value = emptyList()
        rows.value = emptyList()
    }

    private fun Playlist.withCount() = copy(episodeCount = rows.value.count { it.playlistId == id })

    private fun ordered(all: List<PlaylistEpisode>, playlistId: String) =
        all.filter { it.playlistId == playlistId }.sortedWith(compareBy({ it.position }, { it.itemId }))
}
