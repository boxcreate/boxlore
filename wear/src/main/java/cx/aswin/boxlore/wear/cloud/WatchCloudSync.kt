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
    /** Runs with the token the API stopped taking. */
    private val onSignedOut: (refused: String) -> Unit = {},
) {
    private val running = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    // Shows whose lookup failed; the ones not tried yet go first, so a few that keep failing hold nobody up.
    private val failed = mutableSetOf<String>()

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
            if (sent != CloudSyncResult.Done) return@withLock ended(sent, token)
            ended(pull(token), token)
        }

    private suspend fun push(token: String): CloudSyncResult {
        val push = WatchCloudRules.push(store.state.value, clock()) ?: return CloudSyncResult.Done
        return when (val answer = api.push(token, push.request)) {
            is CloudAnswer.Ok -> {
                keep(answer.renewedToken, used = token)
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
                keep(answer.renewedToken, used = token)
                val unknown = WatchCloudRules.unknownShows(store.state.value, answer.value)
                val tried = unknown.sortedBy { it in failed }.take(MAX_LOOKUPS)
                val shows = tried.mapNotNull { id -> showOf(id)?.let { id to it } }.toMap()
                failed += tried - shows.keys
                failed -= shows.keys
                store.update { WatchCloudRules.withCloud(it, answer.value, shows, playingId()) }
                // More shows than one sync looks up: the next one carries on straight away, as long as
                // this one got somewhere. Lookups that all failed wait for the next regular sync.
                if (unknown.size > tried.size && shows.isNotEmpty()) request()
                CloudSyncResult.Done
            }
            CloudAnswer.Refused -> CloudSyncResult.SignedOut
            CloudAnswer.Unreachable -> CloudSyncResult.Unreachable
        }

    /** Keeps a fresh token the API sent, unless the watch signed out or in again meanwhile. */
    private fun keep(renewed: String?, used: String) {
        if (renewed != null) tokens.replace(used, renewed)
    }

    private fun ended(result: CloudSyncResult, token: String): CloudSyncResult {
        if (result == CloudSyncResult.SignedOut) onSignedOut(token)
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
