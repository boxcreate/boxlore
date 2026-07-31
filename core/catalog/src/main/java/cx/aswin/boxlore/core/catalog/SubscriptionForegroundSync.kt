package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-once foreground sync of subscribed shows' latest episodes via
 * [PodcastRepository.syncSubscriptions].
 *
 * Previously lived only in Home's ViewModel init — cold starts that skip Home
 * (e.g. open-app-to Subscriptions) never refreshed New Episodes until Home mounted.
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
        const val DEFAULT_CHUNK_SIZE = 10

        fun create(
            podcastRepository: PodcastRepository,
            subscriptionRepository: SubscriptionRepository,
            scope: CoroutineScope,
            initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
        ): SubscriptionForegroundSync =
            SubscriptionForegroundSync(
                scope = scope,
                initialDelayMs = initialDelayMs,
                syncAction = {
                    syncSubscribedLatestEpisodes(
                        loadIds = { subscriptionRepository.subscribedPodcastIds.first() },
                        syncChunk = { ids -> podcastRepository.syncSubscriptions(ids) },
                        saveLatest = { id, episode ->
                            subscriptionRepository.updateLatestEpisode(id, episode)
                        },
                        chunkSize = chunkSize,
                    )
                },
            )

        /**
         * Chunked sync body (test seam). Failures are per-chunk so one bad feed
         * does not abort the rest.
         */
        internal suspend fun syncSubscribedLatestEpisodes(
            loadIds: suspend () -> Set<String>,
            syncChunk: suspend (List<String>) -> Map<String, Episode>,
            saveLatest: suspend (String, Episode) -> Unit,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
        ) {
            val currentSubs =
                try {
                    loadIds()
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed totally", e)
                    return
                }
            if (currentSubs.isEmpty()) return

            val chunks = currentSubs.chunked(chunkSize)
            Log.d(TAG, "Starting background sync for ${currentSubs.size} subs in ${chunks.size} chunks")
            for (chunk in chunks) {
                syncOneChunk(chunk, syncChunk, saveLatest)
            }
            Log.d(TAG, "Finished background sync for all ${currentSubs.size} subs")
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
