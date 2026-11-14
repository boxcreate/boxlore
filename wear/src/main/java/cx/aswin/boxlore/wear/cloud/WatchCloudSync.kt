package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.model.WatchShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How one sync with the cloud ended. */
internal enum class CloudSyncResult { Done, NotSignedIn, Unreachable, SignedOut }

/**
 * Keeps the watch's library and the listener's account the same, with no phone involved: it sends
 * what changed on the watch, then takes what changed elsewhere. It runs while the watch is signed
 * in: at start, a little after each change made here, and now and then while the app is alive.
 */
internal class WatchCloudSync(
    private val api: WatchCloudApi,
    private val tokens: WatchAccountStore,
    private val store: WatchLibraryStore,
    /** A followed show's name and artwork, looked up from the API; null when that failed. */
    private val showOf: suspend (showId: String) -> WatchShow?,
    /** The episode in the watch's player, which a pull never moves. */
    private val playingId: () -> String?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Runs when the API stopped taking this watch's token. */
    private val onSignedOut: () -> Unit = {},
) {
    private val running = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Asks for a sync soon; several asks before it runs make one sync. */
    fun request() {
        wake.trySend(Unit)
    }

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { wake.consumeEach { syncNow() } }
        scope.launch {
            // The first value is the library as loaded, not a change.
            store.state.map { it.revision }.distinctUntilChanged().drop(1).debounce(SETTLE_MS).collect { request() }
        }
        scope.launch {
            while (isActive) {
                request()
                delay(INTERVAL_MS)
            }
        }
    }

    suspend fun syncNow(): CloudSyncResult =
        running.withLock {
            val token = tokens.token ?: return@withLock CloudSyncResult.NotSignedIn
            val sent = push(token)
            if (sent != CloudSyncResult.Done) return@withLock ended(sent)
            ended(pull(token))
        }

    private suspend fun push(token: String): CloudSyncResult {
        val push = WatchCloudRules.push(store.state.value, clock()) ?: return CloudSyncResult.Done
        return when (api.push(token, push.request)) {
            is CloudAnswer.Ok -> {
                store.update { WatchCloudRules.pushed(it, push.covered) }
                CloudSyncResult.Done
            }
            CloudAnswer.Refused -> CloudSyncResult.SignedOut
            CloudAnswer.Unreachable -> CloudSyncResult.Unreachable
        }
    }

    private suspend fun pull(token: String): CloudSyncResult =
        when (val answer = api.pull(token, SyncPullRequest(since = store.state.value.cloudSince))) {
            is CloudAnswer.Ok -> {
                val unknown = WatchCloudRules.unknownShows(store.state.value, answer.value)
                val shows = unknown.take(MAX_LOOKUPS).mapNotNull { id -> showOf(id)?.let { id to it } }.toMap()
                store.update { WatchCloudRules.withCloud(it, answer.value, shows, playingId()) }
                // More shows than one sync looks up: the next one carries on straight away.
                if (unknown.size > MAX_LOOKUPS) request()
                CloudSyncResult.Done
            }
            CloudAnswer.Refused -> CloudSyncResult.SignedOut
            CloudAnswer.Unreachable -> CloudSyncResult.Unreachable
        }

    private fun ended(result: CloudSyncResult): CloudSyncResult {
        if (result == CloudSyncResult.SignedOut) onSignedOut()
        return result
    }

    private companion object {
        /** Lets a burst of changes, such as progress saves, settle into one sync. */
        const val SETTLE_MS = 15_000L
        const val INTERVAL_MS = 15L * 60L * 1_000L

        /** Shows looked up in one sync; an account with more gets the rest with the next pulls. */
        const val MAX_LOOKUPS = 40
    }
}
