package cx.aswin.boxlore.core.playback.service

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.playback.PlaybackMediaIdPolicy
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.PlaybackUris
import cx.aswin.boxlore.core.playback.QueueEntry
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueEngine
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Appends Smart Queue picks after the current item. Owned by the playback service only.
 *
 * An episode stored under several IDs counts once (docs/EPISODE_IDS.md §5): every ID of a queued
 * episode is excluded, picks of the same episode are kept once, and each pick plays from the cache
 * key of whichever of its IDs holds a completed download (own-episode-IDs P4, plan section 8).
 */
class SmartQueueRefillCoordinator(
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val queueRepository: QueueRepository,
    private val smartQueueEngine: SmartQueueEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
    private val queueMaxSize: Int,
    private val mediaIdPrefixStripper: (String) -> String,
) {
    private val refillMutex = Mutex()
    private val listenerStore = database.episodeListenerStore()

    suspend fun refillQueue(
        player: Player,
        contextExhausted: Boolean = false,
        isCurrentSession: () -> Boolean = { true },
    ): Boolean = refillMutex.withLock {
        refillCurrentQueue(player, contextExhausted, isCurrentSession)
    }

    private suspend fun refillCurrentQueue(
        player: Player,
        contextExhausted: Boolean,
        isCurrentSession: () -> Boolean,
    ): Boolean {
        val snapshot = readSnapshot(player, contextExhausted, isCurrentSession) ?: return false
        val episodeId = mediaIdPrefixStripper(snapshot.item.mediaId)
        // Only current/upcoming items count against the cap; a long list may retain a consumed prefix.
        val room = (queueMaxSize - (snapshot.ids.size - snapshot.index)).coerceAtLeast(0)
        if (room == 0) return false
        val expectedQueueIds = database.queueDao().getAllQueueItemsSync().map { it.episodeId }
        val podcast = resolvePodcast(episodeId, snapshot.item.mediaMetadata) ?: return false
        val isCurrent = { isCurrentSession() && snapshot.matches(player) }
        val sourceId = database.queueDao().getQueueItemByEpisodeId(episodeId)?.contextSourceId
            ?: snapshot.item.mediaMetadata.extras?.getString("source_entry_point")
        val excluded = withContext(ioDispatcher) { listenerStore.withAllAliases(snapshot.ids) }
        val candidates = withContext(ioDispatcher) {
            smartQueueEngine.getNextEpisodes(
                currentEpisode = currentEpisode(episodeId, snapshot.item, podcast),
                podcast = podcast,
                preferredSort = podcast.preferredSort,
                excludeEpisodeIds = excluded,
                currentContextSourceId = sourceId,
            )
        }.filter { it.episode.id.toString() !in excluded && !it.episode.enclosureUrl.isNullOrBlank() }
        val entries = withContext(ioDispatcher) { oncePerEpisode(candidates, snapshot.ids) }.take(room)
        if (entries.isEmpty()) return false
        val cacheIds = withContext(ioDispatcher) { listenerStore.downloadHolders(entries.map { it.episode.id.toString() }) }

        // Room rolls back the entire batch if a new transport request or queue edit wins.
        val persisted = queueRepository.addRefillEntriesIfUnchanged(entries, expectedQueueIds, episodeId) {
            withContext(mainDispatcher) { isCurrent() }
        }
        if (!persisted) return false
        val appended = withContext(mainDispatcher) {
            if (!isCurrent()) return@withContext false
            // One mutation avoids asynchronous Cast receiver sequence-number races.
            player.addMediaItems(entries.map { refillMediaItem(it, cacheIds[it.episode.id.toString()]) })
            true
        }
        if (!appended) return false
        trackRefill(episodeId, podcast, entries)
        return true
    }

    private suspend fun readSnapshot(player: Player, contextExhausted: Boolean, isCurrentSession: () -> Boolean): RefillSnapshot? = withContext(mainDispatcher) {
        if (!isCurrentSession()) return@withContext null
        val item = player.currentMediaItem ?: return@withContext null
        if (PlaybackQueueContext.isContextItem(item) && !contextExhausted) return@withContext null
        RefillSnapshot(item, player.currentMediaItemIndex, playerIds(player))
    }

    private fun playerIds(player: Player): List<String> = (0 until player.mediaItemCount).map { mediaIdPrefixStripper(player.getMediaItemAt(it).mediaId) }

    private fun RefillSnapshot.matches(player: Player): Boolean = player.currentMediaItem == item &&
        player.currentMediaItemIndex == index &&
        playerIds(player) == ids

    private suspend fun resolvePodcast(episodeId: String, metadata: MediaMetadata): Podcast? {
        if (episodeId.startsWith("briefing_")) {
            return Podcast("briefing_daily", metadata.subtitle?.toString() ?: "Daily Briefing", "", metadata.artworkUri?.toString() ?: "", genre = "News")
        }
        val podcastId = findPodcastIdForEpisode(episodeId) ?: return null
        val entity = database.podcastDao().getPodcast(podcastId) ?: return podcastRepository.getPodcastDetails(podcastId)
        return Podcast(
            id = entity.podcastId,
            title = entity.title,
            artist = entity.author,
            imageUrl = entity.imageUrl,
            description = entity.description,
            genre = entity.genre ?: "Podcast",
            type = entity.type,
            preferredSort = entity.preferredSort,
        )
    }

    private fun currentEpisode(episodeId: String, item: MediaItem, podcast: Podcast): EpisodeItem = EpisodeItem(
        id = episodeId.toLongOrNull() ?: 0L,
        title = item.mediaMetadata.title?.toString() ?: "",
        description = "",
        enclosureUrl = item.localConfiguration?.uri?.toString(),
        image = cx.aswin.boxlore.core.playback.PlaybackArtworkMetadata.savedArtworkUrl(item.mediaMetadata),
        feedImage = podcast.imageUrl,
    )

    /** [candidates] without picks of an episode already queued, and with each episode once. */
    private suspend fun oncePerEpisode(candidates: List<QueueEntry>, queuedIds: List<String>): List<QueueEntry> {
        val candidateIds = candidates.map { it.episode.id.toString() }
        val tokens = listenerStore.groupTokens(candidateIds + queuedIds)
        val queuedTokens = queuedIds.mapTo(HashSet()) { tokens[it] ?: it }
        return candidates
            .filter { (tokens[it.episode.id.toString()] ?: it.episode.id.toString()) !in queuedTokens }
            .distinctBy { tokens[it.episode.id.toString()] ?: it.episode.id.toString() }
    }

    private suspend fun trackRefill(episodeId: String, podcast: Podcast, entries: List<QueueEntry>) {
        val region = runCatching { userPreferencesRepository.regionStream.first() }.getOrNull()
        val sources = entries.map { it.source }
        val sourceCounts = sources.groupingBy { it }.eachCount()
        Log.d("AutoQueue", "Added ${entries.size} items after $episodeId: $sourceCounts")
        AnalyticsHelper.trackSmartQueueRefilled(
            AnalyticsHelper.SmartQueueRefillEvent(
                triggeringEpisodeId = episodeId,
                triggeringPodcastGenre = podcast.genre,
                refilledCount = entries.size,
                recommendationSources = sources.distinct(),
                refilledEpisodeIds = entries.map { it.episode.id.toString() },
                region = region,
                sourceCounts = sourceCounts,
                usedServerRecommendations = SmartQueueEngine.SOURCE_PERSONALIZED_REC in sourceCounts || SmartQueueEngine.SOURCE_SERVER_REC in sourceCounts,
            ),
        )
    }

    private data class RefillSnapshot(val item: MediaItem, val index: Int, val ids: List<String>)
}

/**
 * The media item a Smart Queue pick plays. Its cache key follows [PlaybackMediaIdPolicy.customCacheKey],
 * the same key downloads are stored under, so a downloaded pick plays from its file. [cacheId] is
 * the stored ID whose download plays the pick ([cx.aswin.boxlore.core.database.EpisodeListenerStore.downloadHolders]);
 * null plays the pick's own key.
 */
internal fun refillMediaItem(entry: QueueEntry, cacheId: String? = null): MediaItem {
    val ep = entry.episode
    val pod = entry.podcast
    val episodeId = ep.id.toString()
    return MediaItem.Builder()
        .setMediaId(episodeId)
        .setUri(ep.enclosureUrl)
        .setCustomCacheKey(PlaybackMediaIdPolicy.customCacheKey(cacheId ?: episodeId, ep.enclosureUrl))
        .setMimeType(ep.enclosureType?.takeIf { it.isNotBlank() })
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(ep.title)
                .setSubtitle(pod.title)
                .setArtist(pod.artist)
                .setArtworkUri(PlaybackUris.parseOrNull(ep.image ?: ep.feedImage ?: pod.imageUrl))
                .setDisplayTitle(ep.title)
                .setGenre(pod.genre)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .build(),
        ).build()
}
