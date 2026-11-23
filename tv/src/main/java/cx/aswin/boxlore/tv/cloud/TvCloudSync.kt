package cx.aswin.boxlore.tv.cloud

import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.tv.data.TvLibraryStore
import cx.aswin.boxlore.tv.model.TvShow
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
 * Keeps the TV's library and the listener's account the same: it sends what changed on the TV, then
 * takes what changed elsewhere. It runs while the TV is signed in: at start, a little after each
 * change made here, and now and then while the app is alive.
 */
internal class TvCloudSync(
    private val api: TvCloudApi,
    private val tokens: TvTokenStore,
    private val store: TvLibraryStore,
    /** A followed show's name and artwork, looked up from the API; null when that failed. */
    private val showOf: suspend (showId: String) -> TvShow?,
    /** The episode in the TV's player, which a pull never moves. */
    private val playingId: () -> String?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    // Shows whose lookup failed; the ones not tried yet go first, so a few that keep failing hold nobody up.
    private val failed = mutableSetOf<String>()

    /** Runs with the token the API stopped taking. */
    var onRefused: (token: String) -> Unit = {}

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
            val result = if (sent == CloudSyncResult.Done) pull(token) else sent
            if (result == CloudSyncResult.SignedOut) onRefused(token)
            result
        }

    private suspend fun push(token: String): CloudSyncResult {
        val push = TvCloudRules.push(store.state.value, clock()) ?: return CloudSyncResult.Done
        return when (val answer = api.push(token, push.request)) {
            is CloudAnswer.Ok -> {
                keep(answer.renewedToken, used = token)
                store.update { TvCloudRules.pushed(it, push.covered) }
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
                val unknown = TvCloudRules.unknownShows(store.state.value, answer.value)
                val tried = unknown.sortedBy { it in failed }.take(MAX_LOOKUPS)
                val shows = tried.mapNotNull { id -> showOf(id)?.let { id to it } }.toMap()
                failed += tried - shows.keys
                failed -= shows.keys
                store.update { TvCloudRules.withCloud(it, answer.value, shows, playingId(), clock()) }
                // More shows than one sync looks up: the next one carries on straight away, as long as this one got somewhere.
                if (unknown.size > tried.size && shows.isNotEmpty()) request()
                CloudSyncResult.Done
            }
            CloudAnswer.Refused -> CloudSyncResult.SignedOut
            CloudAnswer.Unreachable -> CloudSyncResult.Unreachable
        }

    /** Keeps a fresh token the API sent, unless the TV signed out or in again meanwhile. */
    private fun keep(renewed: String?, used: String) {
        if (renewed != null) tokens.replace(used, renewed)
    }

    private companion object {
        /** Lets a burst of changes, such as progress saves, settle into one sync. */
        const val SETTLE_MS = 15_000L
        const val INTERVAL_MS = 15L * 60L * 1_000L

        /** Shows looked up in one sync; an account with more gets the rest with the next pulls. */
        const val MAX_LOOKUPS = 40
    }
}
