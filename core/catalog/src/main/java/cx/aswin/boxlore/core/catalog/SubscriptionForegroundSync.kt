package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-once foreground sync of subscribed shows' latest episodes via
 * [PodcastRepository.syncSubscriptions], with direct-feed tip refresh for shows
 * the user opted into via Podcast Info "Missing episodes?".
 *
 * Direct-feed network refresh is deferred after PI sync so Home first paint is not
 * competing with RSS download/parse. Until then, tips are promoted from the local
 * supplement cache when newer than Room.
 *
 * Call [ensureStarted] from the composition root after onboarding; Home may also call
 * it — only the first call per process runs.
 */
class SubscriptionForegroundSync(
    private val scope: CoroutineScope,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val syncAction: suspend () -> Unit,
) {
    private val started = AtomicBoolean(false)

    /** Starts the delayed sync at most once per process. */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        if (!processSyncStarted.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            delay(initialDelayMs)
            syncAction()
        }
    }

    /** Test seam: whether [ensureStarted] has already claimed the once-guard. */
    internal fun hasStarted(): Boolean = started.get()

    companion object {
        private const val TAG = "SubscriptionForegroundSync"
        const val DEFAULT_INITIAL_DELAY_MS = 2000L
        /** Extra wait after PI tip sync before downloading publisher feeds. */
        const val DEFAULT_FEED_NETWORK_DELAY_MS = 12_000L
        const val DEFAULT_CHUNK_SIZE = 10

        /** Process-wide guard — AppRoot + Home may hold distinct instances after restarts. */
        private val processSyncStarted = AtomicBoolean(false)

        /** Test-only: clear process-once guard between JVM test cases. */
        internal fun resetProcessGuardForTests() {
            processSyncStarted.set(false)
        }

        fun create(
            podcastRepository: PodcastRepository,
            subscriptionRepository: SubscriptionRepository,
            episodeSupplementPort: EpisodeSupplementPort,
            scope: CoroutineScope,
            initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
            feedNetworkDelayMs: Long = DEFAULT_FEED_NETWORK_DELAY_MS,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
        ): SubscriptionForegroundSync =
            SubscriptionForegroundSync(
                scope = scope,
                initialDelayMs = initialDelayMs,
                syncAction = {
                    syncSubscribedLatestEpisodes(
                        loadIds = { subscriptionRepository.subscribedPodcastIds.first() },
                        loadOptedInIds = { episodeSupplementPort.listOptedInPodcastIds() },
                        loadPodcastMeta = { id ->
                            subscriptionRepository.getPodcastEntity(id)?.let { entity ->
                                DirectFeedTipMeta(
                                    feedUrl = entity.feedUrl,
                                    title = entity.title,
                                    imageUrl = entity.imageUrl,
                                    genre = entity.genre,
                                    artist = entity.author,
                                    knownTip = entity.latestEpisode,
                                )
                            }
                        },
                        loadCachedFeedTip = { id ->
                            episodeSupplementPort
                                .getEpisodesForPodcast(id)
                                .maxByOrNull { it.publishedDate }
                        },
                        resolveFeedTip = { id, meta ->
                            episodeSupplementPort.resolveNewestTipFromFeed(
                                podcastIndexId = id,
                                feedUrl = meta.feedUrl.orEmpty(),
                                knownEpisodes = listOfNotNull(meta.knownTip),
                                podcastTitle = meta.title,
                                podcastImageUrl = meta.imageUrl,
                                podcastGenre = meta.genre,
                                podcastArtist = meta.artist,
                            )
                        },
                        syncChunk = { ids -> podcastRepository.syncSubscriptions(ids) },
                        saveLatest = saveLatest@{ id, episode ->
                            val existing = subscriptionRepository.getPodcastEntity(id)?.latestEpisode
                            if (existing?.id == episode.id &&
                                existing.publishedDate == episode.publishedDate
                            ) {
                                return@saveLatest
                            }
                            subscriptionRepository.updateLatestEpisode(id, episode)
                        },
                        saveDirectFeedLatest = saveFeed@{ id, episode ->
                            val existing = subscriptionRepository.getPodcastEntity(id)?.latestEpisode
                            if (existing?.id == episode.id &&
                                existing.publishedDate == episode.publishedDate
                            ) {
                                return@saveFeed
                            }
                            subscriptionRepository.updateLatestEpisode(
                                podcastId = id,
                                episode = episode,
                                markAsNew = true,
                            )
                        },
                        chunkSize = chunkSize,
                        feedNetworkDelayMs = feedNetworkDelayMs,
                    )
                    syncTrackedFeedUrlsForOptedInNotifications(
                        episodeSupplementPort = episodeSupplementPort,
                        subscriptionRepository = subscriptionRepository,
                    )
                },
            )

        /**
         * Chunked sync body (test seam).
         *
         * Order: PI `/sync` for non-opted-in → promote opted-in tips from **local**
         * supplement cache → optional delay → network feed tip refresh for opted-in.
         */
        internal suspend fun syncSubscribedLatestEpisodes(
            loadIds: suspend () -> Set<String>,
            syncChunk: suspend (List<String>) -> Map<String, Episode>,
            saveLatest: suspend (String, Episode) -> Unit,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
            loadOptedInIds: suspend () -> Set<String> = { emptySet() },
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta? = { null },
            loadCachedFeedTip: suspend (String) -> Episode? = { null },
            resolveFeedTip: suspend (String, DirectFeedTipMeta) -> Episode? = { _, _ -> null },
            /** Opted-in feed tips — sets the shared NEW badge until tip is seen. */
            saveDirectFeedLatest: suspend (String, Episode) -> Unit = saveLatest,
            feedNetworkDelayMs: Long = 0L,
        ) {
            val currentSubs =
                try {
                    loadIds()
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed totally", e)
                    return
                }
            if (currentSubs.isEmpty()) return

            val optedIn =
                try {
                    loadOptedInIds()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load direct-feed opt-ins; falling back to PI sync", e)
                    emptySet()
                }

            val feedTipIds =
                currentSubs.filter { id ->
                    !id.startsWith("rss:") && id in optedIn
                }
            val piSyncIds = (currentSubs - feedTipIds.toSet()).toList()

            Log.d(
                TAG,
                "Starting background sync for ${currentSubs.size} subs " +
                    "(${feedTipIds.size} direct-feed, ${piSyncIds.size} PI) " +
                    "in chunks of $chunkSize",
            )

            val chunks = piSyncIds.chunked(chunkSize)
            for (chunk in chunks) {
                syncOneChunk(chunk, syncChunk, saveLatest)
            }

            for (id in feedTipIds) {
                promoteCachedDirectFeedTip(id, loadPodcastMeta, loadCachedFeedTip, saveDirectFeedLatest)
            }

            if (feedTipIds.isNotEmpty() && feedNetworkDelayMs > 0L) {
                delay(feedNetworkDelayMs)
            }

            for (id in feedTipIds) {
                syncOneDirectFeedTip(id, loadPodcastMeta, resolveFeedTip, saveDirectFeedLatest)
            }
            Log.d(TAG, "Finished background sync for all ${currentSubs.size} subs")
        }

        /**
         * Process-once heal: opted-in shows that already have notifications on get
         * `feedUrl` on RTDB so the checker can poll RSS without a notification toggle.
         */
        private suspend fun syncTrackedFeedUrlsForOptedInNotifications(
            episodeSupplementPort: EpisodeSupplementPort,
            subscriptionRepository: SubscriptionRepository,
        ) {
            try {
                val optedIn = episodeSupplementPort.listOptedInPodcastIds()
                for (id in optedIn) {
                    val entity = subscriptionRepository.getPodcastEntity(id) ?: continue
                    subscriptionRepository.syncTrackedPodcastFeedUrl(entity.toPodcast())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync tracked feed URLs for notifications", e)
            }
        }

        private suspend fun promoteCachedDirectFeedTip(
            podcastId: String,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            loadCachedFeedTip: suspend (String) -> Episode?,
            saveLatest: suspend (String, Episode) -> Unit,
        ) {
            try {
                val meta = loadPodcastMeta(podcastId) ?: return
                val cached = loadCachedFeedTip(podcastId) ?: return
                val known = meta.knownTip
                val shouldPromote =
                    known == null ||
                        cached.id != known.id ||
                        cached.publishedDate > known.publishedDate
                if (!shouldPromote) {
                    Log.d(TAG, "Cached tip already current for $podcastId")
                    return
                }
                saveLatest(podcastId, cached)
                Log.d(TAG, "Promoted cached direct-feed tip for $podcastId")
            } catch (e: Exception) {
                Log.e(TAG, "Cached direct-feed tip promote failed for $podcastId", e)
            }
        }

        private suspend fun syncOneDirectFeedTip(
            podcastId: String,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            resolveFeedTip: suspend (String, DirectFeedTipMeta) -> Episode?,
            saveLatest: suspend (String, Episode) -> Unit,
        ) {
            try {
                val meta = loadPodcastMeta(podcastId)
                if (meta == null) {
                    Log.w(TAG, "No Room row for opted-in $podcastId; skipping feed tip")
                    return
                }
                val tip = resolveFeedTip(podcastId, meta)
                if (tip != null) {
                    val known = meta.knownTip
                    if (known != null && known.id == tip.id && known.publishedDate == tip.publishedDate) {
                        Log.d(TAG, "Network tip unchanged for $podcastId")
                        return
                    }
                    saveLatest(podcastId, tip)
                    Log.d(TAG, "Saved direct-feed tip for $podcastId")
                } else {
                    Log.w(TAG, "Direct-feed tip empty for $podcastId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct-feed tip sync failed for $podcastId", e)
            }
        }

        private suspend fun syncOneChunk(
            chunk: List<String>,
            syncChunk: suspend (List<String>) -> Map<String, Episode>,
            saveLatest: suspend (String, Episode) -> Unit,
        ) {
            try {
                val synced = syncChunk(chunk)
                Log.d(TAG, "Successfully fetched chunk of ${chunk.size} subs, saving to DB...")
                for ((podId, episode) in synced) {
                    saveLatest(podId, episode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync chunk failed", e)
            }
        }
    }
}

/** Room fields needed to resolve a direct-feed library tip. */
internal data class DirectFeedTipMeta(
    val feedUrl: String?,
    val title: String?,
    val imageUrl: String?,
    val genre: String?,
    val artist: String?,
    val knownTip: Episode?,
)
