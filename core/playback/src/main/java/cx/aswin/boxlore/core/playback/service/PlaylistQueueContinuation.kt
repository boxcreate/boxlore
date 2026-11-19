package cx.aswin.boxlore.core.playback.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.QueueEntry
import cx.aswin.boxlore.core.playback.QueueRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Tops a playing playlist up from the playlist itself when its last queued episode ends. Owned by
 * the playback service only, beside Smart Queue's refill ([SmartQueueRefillCoordinator]): the
 * service tries this first and falls back to Smart Queue when it returns null (the episode isn't a
 * playlist's) or false (the playlist has nothing left).
 *
 * [nextEpisodes] is `PlaylistRepository.nextEpisodes`: the playlist's next unheard episodes, leaving
 * out every ID already in the player. Added rows keep the playlist's `playlist:<id>` context and are
 * saved as `MANUAL`, so they play, refill and report as the playlist's, never as Smart Queue picks.
 */
@Suppress("LongParameterList")
internal class PlaylistQueueContinuation(
    private val database: BoxLoreDatabase,
    private val queueRepository: QueueRepository,
    private val nextEpisodes: suspend (playlistId: String, limit: Int, excludeIds: Collection<String>) -> List<Episode>,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val queueMaxSize: Int,
    private val mediaIdPrefixStripper: (String) -> String,
    private val batchSize: Int = BATCH_SIZE,
) {
    private val listenerStore = database.episodeListenerStore()

    /** True when episodes were added, false when the playlist had none left, null when the playing episode isn't a playlist's. */
    suspend fun refill(player: Player, isCurrentSession: () -> Boolean): Boolean? {
        val snapshot = withContext(mainDispatcher) { snapshotOf(player, isCurrentSession) } ?: return null
        val episodeId = mediaIdPrefixStripper(snapshot.item.mediaId)
        val sourceId = withContext(ioDispatcher) { database.queueDao().getQueueItemByEpisodeId(episodeId)?.contextSourceId }
        val playlistId = playlistIdOf(sourceId) ?: return null
        val room = (queueMaxSize - (snapshot.ids.size - snapshot.index)).coerceAtLeast(0)
        if (room == 0) return false
        // The saved queue before the slow read, so a change made meanwhile (cloud apply, Android Auto) is seen.
        val expectedIds = withContext(ioDispatcher) { database.queueDao().getAllQueueItemsSync().map { it.episodeId } }
        val episodes = withContext(ioDispatcher) { nextEpisodes(playlistId, minOf(batchSize, room), snapshot.ids) }
            .filter { it.audioUrl.isNotBlank() && it.id.toLongOrNull() != null }
        val picks = picks(episodes, sourceId.orEmpty(), snapshot.casting)
        if (picks.isEmpty()) return false
        return append(player, snapshot, episodeId, picks, expectedIds) { isCurrentSession() && snapshot.matches(player) }
    }

    private fun snapshotOf(player: Player, isCurrentSession: () -> Boolean): Snapshot? {
        val item = player.currentMediaItem ?: return null
        if (!isCurrentSession() || PlaybackQueueContext.contextEntryPoint(item) != PlaybackQueueContext.PLAYLIST) return null
        return Snapshot(item, player.currentMediaItemIndex, playerIds(player), CastQueueGuard.isRemote(player))
    }

    /** The playlist ID of a `playlist:<id>` context source, or null for any other source. */
    private fun playlistIdOf(sourceId: String?): String? = sourceId
        ?.takeIf { it.startsWith(PlaybackQueueContext.PLAYLIST_SOURCE_PREFIX) }
        ?.removePrefix(PlaybackQueueContext.PLAYLIST_SOURCE_PREFIX)
        ?.takeIf(String::isNotBlank)

    /** Each episode with the media item it plays as; while casting, only ones a receiver can fetch. */
    private suspend fun picks(episodes: List<Episode>, sourceId: String, casting: Boolean): List<Pair<QueueEntry, MediaItem>> {
        if (episodes.isEmpty()) return emptyList()
        val cacheIds = withContext(ioDispatcher) { listenerStore.downloadHolders(episodes.map { it.id }) }
        return episodes.map { QueueEntry(it.toEpisodeItem(), it.toPodcast(), sourceId) }
            .map { it to playlistMediaItem(it, cacheIds[it.episode.id.toString()]) }
            .filter { (_, item) -> !casting || CastTransferPolicy.isCastable(item) }
    }

    /** Saves [picks] after the playing episode, then adds them to the player, unless the saved queue no longer is [expectedIds]. */
    @Suppress("LongParameterList")
    private suspend fun append(
        player: Player,
        snapshot: Snapshot,
        episodeId: String,
        picks: List<Pair<QueueEntry, MediaItem>>,
        expectedIds: List<String>,
        isCurrent: () -> Boolean,
    ): Boolean {
        val persisted = queueRepository.addRefillEntriesIfUnchanged(picks.map { it.first }, expectedIds, episodeId, contextType = MANUAL) {
            withContext(mainDispatcher) { isCurrent() }
        }
        if (!persisted) return false
        return withContext(mainDispatcher) {
            if (!isCurrent() || !snapshot.matches(player)) return@withContext false
            val items = CastQueueGuard.castable(player, picks.map { it.second })
            if (items.isNotEmpty()) player.addMediaItems(items)
            true
        }
    }

    private fun playerIds(player: Player): List<String> = (0 until player.mediaItemCount).map { mediaIdPrefixStripper(player.getMediaItemAt(it).mediaId) }

    private fun Snapshot.matches(player: Player): Boolean =
        player.currentMediaItem == item && player.currentMediaItemIndex == index && playerIds(player) == ids

    private data class Snapshot(val item: MediaItem, val index: Int, val ids: List<String>, val casting: Boolean)

    companion object {
        /** Episodes added per top-up, never more than the queue has room for. */
        const val BATCH_SIZE = 20

        private const val MANUAL = "MANUAL"
    }
}

/**
 * The service's refill order: when a context queue's last episode ended ([contextExhausted]), a
 * playlist tops up from itself first ([playlistTopUp], null without playlists); once it isn't a
 * playlist's or has nothing left, Smart Queue ([smartQueue]) takes over, as it always does otherwise.
 */
internal suspend fun refillPlaylistFirst(
    contextExhausted: Boolean,
    playlistTopUp: (suspend () -> Boolean?)?,
    smartQueue: suspend () -> Boolean,
): Boolean {
    if (contextExhausted && playlistTopUp?.invoke() == true) return true
    return smartQueue()
}

/** A Smart Queue style media item ([refillMediaItem]) that also plays as the playlist's. */
internal fun playlistMediaItem(entry: QueueEntry, cacheId: String?): MediaItem {
    val item = refillMediaItem(entry, cacheId)
    val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
        putString("source_entry_point", PlaybackQueueContext.PLAYLIST)
    }
    return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
}

private fun Episode.toEpisodeItem(): EpisodeItem = EpisodeItem(
    id = id.toLong(),
    title = title,
    description = description,
    enclosureUrl = audioUrl,
    duration = duration,
    datePublished = publishedDate,
    image = imageUrl,
    feedImage = podcastImageUrl,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    episodeType = episodeType,
    season = seasonNumber,
    episodeNumber = episodeNumber,
    enclosureType = enclosureType,
)

private fun Episode.toPodcast(): Podcast = Podcast(
    id = podcastId.orEmpty(),
    title = podcastTitle.orEmpty(),
    artist = podcastArtist.orEmpty(),
    imageUrl = podcastImageUrl.orEmpty(),
    genre = podcastGenre?.takeIf(String::isNotBlank) ?: "Podcast",
)
