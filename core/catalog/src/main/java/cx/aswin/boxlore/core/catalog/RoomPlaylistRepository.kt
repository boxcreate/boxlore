package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PlaylistDao
import cx.aswin.boxlore.core.database.PlaylistEntity
import cx.aswin.boxlore.core.database.PlaylistItemEntity
import cx.aswin.boxlore.core.database.PlaylistSummary
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PLAYLIST_EPISODE_LIMIT
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import cx.aswin.boxlore.core.model.normalizePlaylistName
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [PlaylistRepository] on Room. [episodeIds] gives every stored ID of an episode (the app passes
 * `EpisodeListenerStore.memberIds`), so an episode stored under several IDs is added once, as the
 * queue does. Writes run one at a time, so two adds can't both pass the limit.
 */
@Suppress("TooManyFunctions")
class RoomPlaylistRepository(
    private val dao: PlaylistDao,
    private val episodeIds: suspend (String) -> Collection<String> = { listOf(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : PlaylistRepository {
    private val writes = Mutex()

    override val playlists: Flow<List<Playlist>> =
        combine(dao.observePlaylistSummaries(), dao.observePlaylistCovers()) { summaries, covers ->
            val coversById = covers.groupBy({ it.playlistId }) { it.imageUrl }
            summaries.map { it.toPlaylist(coversById[it.playlist.playlistId].orEmpty()) }
        }.distinctUntilChanged()

    override fun playlist(playlistId: String): Flow<Playlist?> =
        playlists.map { list -> list.firstOrNull { it.id == playlistId } }.distinctUntilChanged()

    override fun episodes(playlistId: String): Flow<List<PlaylistEpisode>> =
        dao.observeItems(playlistId).map { rows -> rows.map(PlaylistEpisodeSnapshot::toPlaylistEpisode) }.distinctUntilChanged()

    override suspend fun getPlaylists(): List<Playlist> {
        val covers = dao.getAllItems().groupBy { it.playlistId }
        return dao.getAllPlaylists().map { entity -> entity.toPlaylist(covers[entity.playlistId].orEmpty()) }
    }

    override suspend fun getEpisodes(playlistId: String): List<PlaylistEpisode> =
        dao.getItems(playlistId).map(PlaylistEpisodeSnapshot::toPlaylistEpisode)

    override suspend fun createPlaylist(name: String, episodes: List<Episode>): PlaylistCreated = writes.withLock {
        val createdAt = now()
        val entity = PlaylistEntity(playlistId = newId(), name = displayName(name), createdAt = createdAt, updatedAt = createdAt)
        dao.insertPlaylist(entity)
        val result = appendLocked(entity.playlistId, episodes, createdAt)
        // Only the new playlist's rows: the episodes just added.
        val items = if (result.added > 0) dao.getItems(entity.playlistId) else emptyList()
        PlaylistCreated(entity.toPlaylist(items), result)
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): Boolean = writes.withLock {
        val normalized = normalizePlaylistName(name)
        if (normalized.isEmpty()) return@withLock false
        dao.renamePlaylist(playlistId, normalized, now()) > 0
    }

    override suspend fun deletePlaylist(playlistId: String) = writes.withLock { dao.deletePlaylist(playlistId) }

    override suspend fun addEpisodes(playlistId: String, episodes: List<Episode>): PlaylistAddResult = writes.withLock {
        if (dao.getPlaylist(playlistId) == null) return@withLock PlaylistAddResult(added = 0)
        appendLocked(playlistId, episodes, now())
    }

    override suspend fun removeEpisodes(playlistId: String, itemIds: Collection<Long>): List<PlaylistEpisode> = writes.withLock {
        val wanted = itemIds.toSet()
        val removed = dao.getItems(playlistId).filter { it.id in wanted }
        removed.forEach { dao.deleteItem(it.id) }
        if (removed.isNotEmpty()) dao.touchPlaylist(playlistId, now())
        removed.map(PlaylistEpisodeSnapshot::toPlaylistEpisode)
    }

    override suspend fun restoreEpisodes(removed: List<PlaylistEpisode>): Int = writes.withLock {
        var restored = 0
        for ((playlistId, rows) in removed.groupBy { it.playlistId }) {
            if (dao.getPlaylist(playlistId) != null) restored += restoreInto(playlistId, rows)
        }
        restored
    }

    private suspend fun restoreInto(playlistId: String, rows: List<PlaylistEpisode>): Int {
        var room = PLAYLIST_EPISODE_LIMIT - dao.countItems(playlistId)
        var back = 0
        for (row in rows.sortedBy { it.position }) {
            val fits = room > 0 && dao.episodeIdsIn(playlistId, aliases(row.episode.id).toList()).isEmpty()
            if (fits && dao.insertItem(PlaylistEpisodeSnapshot.toEntity(row)) != -1L) {
                back++
                room--
            }
        }
        if (back > 0) dao.touchPlaylist(playlistId, now())
        return back
    }

    override suspend fun reorder(playlistId: String, orderedItemIds: List<Long>) = writes.withLock {
        dao.reorder(playlistId, orderedItemIds)
    }

    override suspend fun importPlaylist(playlist: Playlist, episodes: List<PlaylistEpisode>): PlaylistAddResult = writes.withLock {
        val id = playlist.id.ifBlank { newId() }
        if (dao.getPlaylist(id) == null) {
            dao.insertPlaylist(
                PlaylistEntity(playlistId = id, name = displayName(playlist.name), createdAt = playlist.createdAt, updatedAt = playlist.updatedAt),
            )
        }
        var result = PlaylistAddResult(added = 0)
        // Each row keeps the time it was added; the playlist's own updatedAt is kept from the backup.
        for (row in episodes.sortedBy { it.position }) {
            val one = appendLocked(id, listOf(row.episode), row.addedAt, touch = false)
            result = PlaylistAddResult(result.added + one.added, result.alreadyIn + one.alreadyIn, result.didNotFit + one.didNotFit)
        }
        result
    }

    override suspend fun deleteAll() = writes.withLock { dao.deleteAll() }

    private suspend fun appendLocked(playlistId: String, episodes: List<Episode>, addedAt: Long, touch: Boolean = true): PlaylistAddResult {
        var room = PLAYLIST_EPISODE_LIMIT - dao.countItems(playlistId)
        var added = 0
        var alreadyIn = 0
        var didNotFit = 0
        val seen = HashSet<String>()
        for (episode in episodes.filter { it.id.isNotBlank() && it.audioUrl.isNotBlank() }) {
            val ids = aliases(episode.id)
            when {
                ids.any { it in seen } || dao.episodeIdsIn(playlistId, ids.toList()).isNotEmpty() -> alreadyIn++
                room <= 0 -> didNotFit++
                insertAtEnd(playlistId, episode, addedAt) -> {
                    seen += ids
                    added++
                    room--
                }
                else -> alreadyIn++
            }
        }
        if (added > 0 && touch) dao.touchPlaylist(playlistId, addedAt)
        return PlaylistAddResult(added, alreadyIn, didNotFit)
    }

    private suspend fun insertAtEnd(playlistId: String, episode: Episode, addedAt: Long): Boolean {
        val position = (dao.getMaxPosition(playlistId) ?: -1) + 1
        return dao.insertItem(PlaylistEpisodeSnapshot.toEntity(playlistId, episode, addedAt, position)) != -1L
    }

    private suspend fun aliases(episodeId: String): Set<String> =
        (episodeIds(episodeId) + episodeId).filter(String::isNotBlank).toSet()

    private fun displayName(name: String): String = normalizePlaylistName(name).ifEmpty { DEFAULT_NAME }

    /** [items] are the playlist's rows in order; the first [PlaylistDao.COVER_LIMIT] give its covers. */
    private fun PlaylistEntity.toPlaylist(items: List<PlaylistItemEntity>): Playlist = Playlist(
        id = playlistId,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        episodeCount = items.size,
        coverUrls = items.take(PlaylistDao.COVER_LIMIT).mapNotNull { (it.imageUrl ?: it.podcastImageUrl)?.takeIf(String::isNotBlank) },
    )

    private fun PlaylistSummary.toPlaylist(covers: List<String?>): Playlist = Playlist(
        id = playlist.playlistId,
        name = playlist.name,
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
        episodeCount = itemCount,
        coverUrls = covers.mapNotNull { it?.takeIf(String::isNotBlank) },
    )

    companion object {
        const val DEFAULT_NAME = "Playlist"
    }
}
