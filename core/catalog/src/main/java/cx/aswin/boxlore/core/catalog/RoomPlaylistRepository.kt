package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PlaylistDao
import cx.aswin.boxlore.core.database.PlaylistEntity
import cx.aswin.boxlore.core.database.PlaylistShowEntity
import cx.aswin.boxlore.core.database.PlaylistShowRow
import cx.aswin.boxlore.core.database.PlaylistSummary
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.normalizePlaylistName
import cx.aswin.boxlore.core.model.normalizePlaylistPerShowLimit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [PlaylistRepository] on Room. [projectedHistory] and [withAliases] read listening state across an
 * episode's stored IDs (the app passes `EpisodeListenerStore.projectedHistoryOf` and
 * `withAllAliases`); [hiddenShowIds] are the shows hidden everywhere. [now] (epoch milliseconds)
 * dates writes and places the take-turns window. Writes run one at a time.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class RoomPlaylistRepository(
    private val dao: PlaylistDao,
    projectedHistory: suspend (Collection<String>) -> Map<String, ListeningHistoryEntity> = { emptyMap() },
    withAliases: suspend (Collection<String>) -> Set<String> = { it.toSet() },
    private val hiddenShowIds: Flow<Set<String>> = flowOf(emptySet()),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : PlaylistRepository {
    private val writes = Mutex()
    private val episodes = PlaylistEpisodeSource(dao, projectedHistory, withAliases)

    override val playlists: Flow<List<Playlist>> =
        combine(dao.observePlaylistSummaries(), dao.observePlaylistCovers()) { summaries, covers ->
            val coversById = covers.groupBy({ it.playlistId }) { it.imageUrl }
            summaries.map { it.toPlaylist(coversById[it.playlist.playlistId].orEmpty()) }
        }.distinctUntilChanged()

    override fun playlist(playlistId: String): Flow<Playlist?> =
        playlists.map { list -> list.firstOrNull { it.id == playlistId } }.distinctUntilChanged()

    override fun shows(playlistId: String): Flow<List<PlaylistShow>> =
        combine(dao.observeShows(playlistId), hiddenShowIds) { rows, hidden -> rows.map { it.toShow(hidden) } }.distinctUntilChanged()

    override fun playlistIdsForShow(podcastId: String): Flow<Set<String>> =
        dao.observePlaylistIdsForShow(podcastId).map { it.toSet() }.distinctUntilChanged()

    override suspend fun getPlaylists(): List<Playlist> = playlists.first()

    override suspend fun getShowIds(): Map<String, List<String>> =
        dao.getAllShowRows().groupBy({ it.playlistId }) { it.podcastId }

    override suspend fun createPlaylist(
        name: String,
        podcastIds: List<String>,
        mix: PlaylistMix,
        perShowLimit: Int?,
        iconKey: String?,
    ): Playlist = writes.withLock {
        val createdAt = now()
        val entity = PlaylistEntity(
            playlistId = newId(),
            name = displayName(name),
            createdAt = createdAt,
            updatedAt = createdAt,
            mix = mix.stored,
            perShowLimit = storedLimit(perShowLimit),
            iconKey = storedIcon(iconKey),
        )
        dao.insertPlaylist(entity)
        appendLocked(entity.playlistId, podcastIds, createdAt, touch = false)
        playlist(entity.playlistId).first() ?: PlaylistSummary(entity, showCount = 0).toPlaylist(emptyList())
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): Boolean = writes.withLock {
        val normalized = normalizePlaylistName(name)
        if (normalized.isEmpty()) return@withLock false
        dao.renamePlaylist(playlistId, normalized, now()) > 0
    }

    override suspend fun setPlayback(playlistId: String, mix: PlaylistMix, perShowLimit: Int?): Boolean = writes.withLock {
        dao.setPlayback(playlistId, mix.stored, storedLimit(perShowLimit), now()) > 0
    }

    override suspend fun setIcon(playlistId: String, iconKey: String?): Boolean = writes.withLock {
        dao.setIcon(playlistId, storedIcon(iconKey), now()) > 0
    }

    override suspend fun deletePlaylist(playlistId: String) = writes.withLock { dao.deletePlaylist(playlistId) }

    override suspend fun addShows(playlistId: String, podcastIds: List<String>): Int = writes.withLock {
        appendLocked(playlistId, podcastIds, now(), touch = true)
    }

    override suspend fun removeShow(playlistId: String, podcastId: String): PlaylistShow? = writes.withLock {
        val row = dao.getShows(playlistId).firstOrNull { it.podcastId == podcastId } ?: return@withLock null
        if (dao.deleteShow(playlistId, podcastId) == 0) return@withLock null
        dao.touchPlaylist(playlistId, now())
        row.toShow(emptySet())
    }

    override suspend fun replaceShow(oldPodcastId: String, newPodcastId: String) {
        if (oldPodcastId.isBlank() || newPodcastId.isBlank()) return
        writes.withLock { dao.reassignPodcast(oldPodcastId, newPodcastId) }
    }

    override suspend fun restoreShow(removed: PlaylistShow): Boolean = writes.withLock {
        if (dao.getPlaylist(removed.playlistId) == null) return@withLock false
        val back = dao.insertShow(PlaylistShowEntity(removed.playlistId, removed.podcastId, removed.position, removed.addedAt)) != -1L
        if (back) dao.touchPlaylist(removed.playlistId, now())
        back
    }

    override suspend fun nextEpisodes(playlistId: String, limit: Int, excludeIds: Collection<String>): List<Episode> {
        val playlist = dao.getPlaylist(playlistId) ?: return emptyList()
        val hidden = hiddenShowIds.first()
        val shows = dao.getShows(playlistId).filter { it.isSubscribed && it.podcastId !in hidden }
        return episodes.next(shows, PlaylistMix.fromStored(playlist.mix), readLimit(playlist.perShowLimit), limit, excludeIds, now())
    }

    override suspend fun importPlaylist(playlist: Playlist, podcastIds: List<String>): Int = writes.withLock {
        val id = playlist.id.ifBlank { newId() }
        if (dao.getPlaylist(id) == null) {
            dao.insertPlaylist(
                PlaylistEntity(
                    playlistId = id,
                    name = displayName(playlist.name),
                    createdAt = playlist.createdAt,
                    updatedAt = playlist.updatedAt,
                    mix = playlist.mix.stored,
                    perShowLimit = storedLimit(playlist.perShowLimit),
                    iconKey = storedIcon(playlist.iconKey),
                ),
            )
        }
        // The playlist's own updatedAt is kept from the backup.
        appendLocked(id, podcastIds, playlist.updatedAt, touch = false)
    }

    override suspend fun deleteAll() = writes.withLock { dao.deleteAll() }

    private suspend fun appendLocked(playlistId: String, podcastIds: List<String>, addedAt: Long, touch: Boolean): Int {
        val added = podcastIds.filter(String::isNotBlank).distinct().count { dao.appendShow(playlistId, it, addedAt, touch = false) }
        if (added > 0 && touch) dao.touchPlaylist(playlistId, addedAt)
        return added
    }

    private fun displayName(name: String): String = normalizePlaylistName(name).ifEmpty { DEFAULT_NAME }

    /** The stored limit per show: 0 is no limit. */
    private fun storedLimit(perShowLimit: Int?): Int = normalizePlaylistPerShowLimit(perShowLimit) ?: NO_LIMIT

    private fun readLimit(stored: Int): Int? = normalizePlaylistPerShowLimit(stored)

    private fun storedIcon(iconKey: String?): String? = iconKey?.trim()?.takeIf(String::isNotEmpty)

    private fun PlaylistShowRow.toShow(hidden: Set<String>) = PlaylistShow(
        playlistId = playlistId,
        podcastId = podcastId,
        position = position,
        addedAt = addedAt,
        title = title.orEmpty(),
        imageUrl = imageUrl?.takeIf(String::isNotBlank),
        isFollowed = isSubscribed,
        isHidden = podcastId in hidden,
    )

    private fun PlaylistSummary.toPlaylist(covers: List<String?>): Playlist = Playlist(
        id = playlist.playlistId,
        name = playlist.name,
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
        showCount = showCount,
        mix = PlaylistMix.fromStored(playlist.mix),
        perShowLimit = readLimit(playlist.perShowLimit),
        iconKey = playlist.iconKey?.takeIf(String::isNotBlank),
        coverUrls = covers.mapNotNull { it?.takeIf(String::isNotBlank) },
    )

    companion object {
        const val DEFAULT_NAME = "Playlist"

        /** `playlists.perShowLimit` for no limit. */
        private const val NO_LIMIT = 0
    }
}
