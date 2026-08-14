package cx.aswin.boxlore.core.catalog.backup

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
    val ensureFeedUrl: suspend (podcastId: String, feedUrl: String) -> Unit,
    val invalidateCache: (podcastId: String) -> Unit,
    val refreshFeed: suspend (podcastId: String, feedUrl: String) -> EpisodeSupplementOutcome,
    val saveTip: suspend (podcastId: String, episode: Episode) -> Unit,
    val syncTrackedUrl: suspend (podcastId: String) -> Unit,
    val onError: (podcastId: String, error: Exception) -> Unit = { _, _ -> },
)

/**
 * After JSON subscribe: restore Missing episodes? stubs, refresh publisher feeds,
 * and promote tips. Failures are isolated per show so one dead feed does not abort import.
 */
internal object LibraryBackupDirectFeedRestore {
    const val DEFAULT_CONCURRENCY = 6

    suspend fun restoreAndRefresh(
        targets: List<DirectFeedOptInBackup>,
        actions: DirectFeedRestoreActions,
        concurrency: Int = DEFAULT_CONCURRENCY,
    ) {
        if (targets.isEmpty()) return
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        coroutineScope {
            targets.map { optIn ->
                async {
                    gate.withPermit { restoreOne(optIn, actions) }
                }
            }.awaitAll()
        }
    }

    private suspend fun restoreOne(
        optIn: DirectFeedOptInBackup,
        actions: DirectFeedRestoreActions,
    ) {
        val id = optIn.podcastId
        try {
            actions.restoreStub(id, optIn.feedUrl)
            actions.ensureFeedUrl(id, optIn.feedUrl)
            actions.invalidateCache(id)
            when (val outcome = actions.refreshFeed(id, optIn.feedUrl)) {
                is EpisodeSupplementOutcome.Success -> {
                    val tip = outcome.newestFeedEpisode
                    if (tip != null) actions.saveTip(id, tip)
                }
                is EpisodeSupplementOutcome.Failure,
                EpisodeSupplementOutcome.NoDisconnect,
                -> Unit
            }
            actions.syncTrackedUrl(id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            actions.onError(id, error)
        }
    }
}
