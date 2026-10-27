package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.FeedAddressNotSavedException
import cx.aswin.boxlore.core.catalog.FeedAddressUpdate
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Callbacks for [LibraryBackupDirectFeedRestore.restoreAndRefresh]. */
internal data class DirectFeedRestoreActions(
    val restoreStub: suspend (podcastId: String, feedUrl: String) -> Unit,
    /** Saves the backup's address through the one address writer; [FeedAddressUpdate.NOT_SAVED] fails the show. */
    val ensureFeedUrl: suspend (podcastId: String, feedUrl: String) -> FeedAddressUpdate,
    val invalidateCache: (podcastId: String) -> Unit,
    val refreshFeed: suspend (podcastId: String, feedUrl: String) -> EpisodeSupplementOutcome,
    val saveTip: suspend (podcastId: String, episode: Episode) -> Unit,
    val syncTrackedUrl: suspend (podcastId: String) -> Unit,
    val onError: (podcastId: String, error: Exception) -> Unit = { _, _ -> },
)

/**
 * After JSON subscribe: restore Missing episodes? stubs, patch RTDB `feedUrl`,
 * refresh publisher feeds, and promote tips. Failures are isolated per show so
 * one dead feed does not abort import.
 */
internal object LibraryBackupDirectFeedRestore {
    const val DEFAULT_CONCURRENCY = 6

    suspend fun restoreAndRefresh(
        targets: List<DirectFeedOptInBackup>,
        actions: DirectFeedRestoreActions,
        concurrency: Int = DEFAULT_CONCURRENCY,
        onTargetStarted: (suspend (podcastId: String) -> Unit)? = null,
        onTargetCompleted: (suspend (podcastId: String) -> Unit)? = null,
    ) {
        if (targets.isEmpty()) return
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        coroutineScope {
            targets.map { optIn ->
                async {
                    gate.withPermit {
                        val id = optIn.podcastId?.trim().orEmpty()
                        if (id.isNotEmpty()) onTargetStarted?.invoke(id)
                        try {
                            restoreOne(optIn, actions)
                        } finally {
                            if (id.isNotEmpty()) onTargetCompleted?.invoke(id)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun restoreOne(optIn: DirectFeedOptInBackup, actions: DirectFeedRestoreActions,) {
        val id = optIn.podcastId?.trim().orEmpty()
        val url = optIn.feedUrl
        if (id.isEmpty() || url.isNullOrBlank()) return
        try {
            actions.restoreStub(id, url)
            // An address dropped with its notification request is reported, never treated as restored.
            if (actions.ensureFeedUrl(id, url) == FeedAddressUpdate.NOT_SAVED) throw FeedAddressNotSavedException(id)
            actions.syncTrackedUrl(id)
            actions.invalidateCache(id)
            when (val outcome = actions.refreshFeed(id, url)) {
                is EpisodeSupplementOutcome.Success -> {
                    val tip = outcome.newestFeedEpisode
                    if (tip != null) actions.saveTip(id, tip)
                }
                is EpisodeSupplementOutcome.Failure,
                EpisodeSupplementOutcome.NoDisconnect,
                -> Unit
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            actions.onError(id, error)
        }
    }
}
