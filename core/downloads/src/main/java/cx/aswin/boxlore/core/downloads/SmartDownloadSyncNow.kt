package cx.aswin.boxlore.core.downloads

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sync Now on the Downloads screen: waits for the caller's check of the followed shows' feeds,
 * then runs a manual Smart Downloads sync, so an episode the check found can be picked. Every
 * sync, this one included, refreshes the latest episodes through
 * [cx.aswin.boxlore.core.catalog.PodcastRepository.syncSubscriptions], which uses the stored
 * episodes of shows with a ready catalog.
 *
 * The check does not promise that every followed feed was read before downloads are chosen: it
 * may end its own wait early and leave its pass running, and it reads feeds under its own refresh
 * rules, which can skip one read recently. [FEED_CHECK_TIMEOUT_MS] is only the outer limit, for
 * a check that hangs. When the check fails or runs out of time the sync still runs on what is
 * stored. Background and launch syncs call [SmartDownloadManager.performSync] directly, without
 * the feed check.
 */
object SmartDownloadSyncNow {
    /** The longest Sync Now waits for the feed check before choosing downloads. */
    const val FEED_CHECK_TIMEOUT_MS = 45_000L

    private const val TAG = "SmartDownloadSyncNow"

    /**
     * Awaits [checkFeeds] (bounded by [feedCheckTimeoutMs]), then returns [sync]'s result.
     * [checkFeeds] is expected to run its pass on its own scope, so a timeout stops only the wait.
     */
    suspend fun run(
        checkFeeds: suspend () -> Unit,
        sync: suspend () -> Boolean,
        feedCheckTimeoutMs: Long = FEED_CHECK_TIMEOUT_MS,
    ): Boolean {
        try {
            val finished = withTimeoutOrNull(feedCheckTimeoutMs) { checkFeeds() } != null
            if (!finished) Log.w(TAG, "Feed check still running after $feedCheckTimeoutMs ms; choosing from stored episodes.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Feed check failed; choosing from stored episodes.", error)
        }
        return sync()
    }
}
