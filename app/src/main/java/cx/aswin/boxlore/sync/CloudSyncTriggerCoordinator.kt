package cx.aswin.boxlore.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.catalog.sync.SyncResult
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Event-driven lifecycle and interaction coordinator for Boxlore Realtime Cloud Sync.
 * Coalesces and debounces triggers across auth state changes, process lifecycle,
 * playback milestones, and database mutations without mid-play polling.
 *
 * While an episode plays, its own history row (progress saved every few seconds) pushes at most once
 * per [PLAYING_PROGRESS_PUSH_INTERVAL_MS]; pause, completion, a track change and the process going to
 * the background push it straight away. A like of it, like every other change, pushes after
 * [LIBRARY_MUTATION_DEBOUNCE_MS]. [playingHistoryIds] gives the IDs the playing episode's row can be
 * stored under (its episode's trusted IDs), so progress written to the owner ID counts as its own.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList", "TooManyFunctions")
class CloudSyncTriggerCoordinator(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val userSyncCoordinator: UserSyncCoordinator,
    private val authRepository: AuthRepository,
    private val boxcastPrefs: BoxcastPrefs,
    private val podcastDao: PodcastDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val queueDao: QueueDao,
    private val playbackRepository: PlaybackRepository? = null,
    private val playerStateFlow: StateFlow<PlayerState>? = null,
    private val isOnlineFlow: Flow<Boolean>,
    private val processLifecycle: Lifecycle? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onSignOutAction: (() -> Unit)? = null,
    private val playingHistoryIds: suspend (episodeId: String) -> List<String> = { listOf(it) },
) : DefaultLifecycleObserver {

    private val _syncStatusFlow = MutableStateFlow<CloudSyncUiStatus>(
        if (boxcastPrefs.getLastSyncTimestamp() > 0L) {
            CloudSyncUiStatus.Success(boxcastPrefs.getLastSyncTimestamp())
        } else {
            CloudSyncUiStatus.Idle
        },
    )
    val syncStatusFlow: StateFlow<CloudSyncUiStatus> = _syncStatusFlow.asStateFlow()

    /** When the last foreground sync was claimed or last succeeded; at most one per [FOREGROUND_THROTTLE_MS]. */
    private val lastForegroundSyncTimestamp = AtomicLong(0L)

    private val coordinatorJob = kotlinx.coroutines.SupervisorJob()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true

        val lifecycle = processLifecycle ?: runCatching {
            ProcessLifecycleOwner.get().lifecycle
        }.getOrNull()

        lifecycle?.addObserver(this)

        observeAuthState()
        observeConnectivity()
        observePlaybackMilestones()
        observeQueueMutations()
        observeLibraryMutations()
    }

    fun stop() {
        if (!started) return
        started = false

        val lifecycle = processLifecycle ?: runCatching {
            ProcessLifecycleOwner.get().lifecycle
        }.getOrNull()

        lifecycle?.removeObserver(this)
        coordinatorJob.cancelChildren()
    }

    override fun onStart(owner: LifecycleOwner) {
        // Signed out: nothing to sync, so nothing is claimed and the status stays as it is.
        if (!canSyncUser(authRepository.currentUser.value)) return
        val claim = tryClaimForegroundSync() ?: return
        applicationScope.launch(ioDispatcher) { runClaimedSync(claim) }
    }

    /**
     * Claims the foreground sync when none was claimed or succeeded in the last
     * [FOREGROUND_THROTTLE_MS], so a cold start (process start and first auth state together) syncs
     * once; null when another one holds it.
     */
    private fun tryClaimForegroundSync(): ForegroundSyncClaim? {
        val now = clock()
        while (true) {
            val previous = lastForegroundSyncTimestamp.get()
            if (now - previous < FOREGROUND_THROTTLE_MS) return null
            if (lastForegroundSyncTimestamp.compareAndSet(previous, now)) return ForegroundSyncClaim(previous, now)
        }
    }

    /**
     * Runs a claimed sync. A failed one keeps the claim, so a failing server is asked at most once per
     * [FOREGROUND_THROTTLE_MS]; one that made no request (offline, signed out, cancelled) gives it
     * back so the next foreground tries again.
     */
    private suspend fun runClaimedSync(claim: ForegroundSyncClaim) {
        val result = try {
            syncNowInternal()
        } catch (e: kotlinx.coroutines.CancellationException) {
            releaseClaim(claim)
            throw e
        }
        if (result is SyncResult.SkippedOffline || result is SyncResult.SkippedNoAuth) releaseClaim(claim)
    }

    private fun releaseClaim(claim: ForegroundSyncClaim) {
        lastForegroundSyncTimestamp.compareAndSet(claim.claimedAt, claim.previous)
    }

    private class ForegroundSyncClaim(val previous: Long, val claimedAt: Long)

    override fun onStop(owner: LifecycleOwner) {
        if (!canSyncUser(authRepository.currentUser.value)) return
        applicationScope.launch(ioDispatcher) {
            try {
                val isOnline = withTimeoutOrNull(1_000L) { isOnlineFlow.first() } ?: true

                if (!isOnline) {
                    Log.i(TAG, "Process stopped while offline; enqueuing flush worker")
                    CloudSyncWorker.enqueueOneShotSync(context)
                    return@launch
                }

                val pushResult = withTimeoutOrNull(BACKGROUND_PUSH_TIMEOUT_MS) {
                    userSyncCoordinator.executePush()
                }

                if (pushResult == null || pushResult.isFailure) {
                    Log.w(TAG, "Background push timed out or failed on process stop; enqueuing flush worker")
                    CloudSyncWorker.enqueueOneShotSync(context)
                } else {
                    pushResult.getOrNull()?.let { summary ->
                        if (summary.syncedAt > 0L) {
                            _syncStatusFlow.value = CloudSyncUiStatus.Success(summary.syncedAt)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error executing background push on process stop", e)
                CloudSyncWorker.enqueueOneShotSync(context)
            }
        }
    }

    suspend fun triggerManualSync(): SyncResult = withContext(ioDispatcher) {
        syncNowInternal()
    }

    private suspend fun syncNowInternal(): SyncResult {
        if (!canSyncUser(authRepository.currentUser.value)) {
            _syncStatusFlow.value = CloudSyncUiStatus.Idle
            return SyncResult.SkippedNoAuth
        }

        _syncStatusFlow.value = CloudSyncUiStatus.Syncing
        val result = try {
            userSyncCoordinator.syncNow()
        } catch (e: kotlinx.coroutines.CancellationException) {
            _syncStatusFlow.value = lastSettledStatus()
            throw e
        } catch (e: Exception) {
            SyncResult.Failure(e)
        }
        when (result) {
            is SyncResult.Success -> {
                lastForegroundSyncTimestamp.set(clock())
                _syncStatusFlow.value = CloudSyncUiStatus.Success(result.syncedAt)
            }
            is SyncResult.Failure -> {
                _syncStatusFlow.value = CloudSyncUiStatus.Error(result.error.message ?: "Sync failed")
            }
            is SyncResult.SkippedOffline -> {
                _syncStatusFlow.value = CloudSyncUiStatus.Error("Offline — will sync when connected")
            }
            is SyncResult.SkippedNoAuth -> {
                _syncStatusFlow.value = CloudSyncUiStatus.Idle
            }
        }
        return result
    }

    private fun lastSettledStatus(): CloudSyncUiStatus {
        val lastSync = boxcastPrefs.getLastSyncTimestamp()
        return if (lastSync > 0L) {
            CloudSyncUiStatus.Success(lastSync)
        } else {
            CloudSyncUiStatus.Idle
        }
    }

    private suspend fun executePushInternal() {
        if (!canSyncUser(authRepository.currentUser.value)) return
        val pushResult = userSyncCoordinator.executePush()
        pushResult.onSuccess { summary ->
            if (summary.syncedAt > 0L) {
                _syncStatusFlow.value = CloudSyncUiStatus.Success(summary.syncedAt)
            }
        }.onFailure { err ->
            Log.w(TAG, "Non-blocking milestone push failed: ${err.message}")
        }
    }

    private fun observeAuthState() {
        applicationScope.launch(ioDispatcher + coordinatorJob) {
            var previousUser: BoxLoreUser? = null
            var isFirstEmission = true
            authRepository.currentUser.collect { currentUser ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    previousUser = currentUser
                    if (canSyncUser(currentUser)) {
                        tryClaimForegroundSync()?.let { runClaimedSync(it) }
                    }
                    return@collect
                }

                handleAuthStateTransition(previousUser, currentUser)
                previousUser = currentUser
            }
        }
    }

    private suspend fun handleAuthStateTransition(prev: BoxLoreUser?, current: BoxLoreUser?) {
        val prevCanSync = canSyncUser(prev)
        val currentCanSync = canSyncUser(current)

        when {
            !prevCanSync && currentCanSync -> {
                // Sign-in, account claim, or email verification transition
                syncNowInternal()
            }
            prev != null && current == null -> {
                // Sign-out: reset sync timestamps and status (preserve lastSyncedUserId for switch detection)
                boxcastPrefs.setLastSyncTimestamp(0L)
                _syncStatusFlow.value = CloudSyncUiStatus.Idle
                playbackRepository?.clearSession()
                onSignOutAction?.invoke()
            }
            prev != null && current != null && prev.uid != current.uid && currentCanSync -> {
                // Direct account swap
                syncNowInternal()
            }
        }
    }

    private fun observeConnectivity() {
        applicationScope.launch(ioDispatcher + coordinatorJob) {
            var wasOnline: Boolean? = null
            isOnlineFlow.collect { isOnline ->
                val prev = wasOnline
                wasOnline = isOnline
                if (!shouldSyncOnReconnect(prev, isOnline)) return@collect
                // One failed check must not end reconnect syncs for the rest of the process.
                try {
                    if (hasDirtyItems()) syncNowInternal()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect sync check failed: ${e.message}")
                }
            }
        }
    }

    private fun shouldSyncOnReconnect(wasOffline: Boolean?, isOnline: Boolean): Boolean =
        wasOffline == false && isOnline && authRepository.currentUserId != null

    /** Existence checks only: a first sync or a bulk mark can leave a very large history dirty. */
    private suspend fun hasDirtyItems(): Boolean {
        if (podcastDao.hasDirtyPodcasts()) return true
        if (listeningHistoryDao.hasDirtyListeningHistory()) return true
        val queueMeta = queueDao.getQueueMetadata()
        return queueMeta?.isDirty == true
    }

    private fun observePlaybackMilestones() {
        applicationScope.launch(ioDispatcher + coordinatorJob) {
            val stateFlow = playerStateFlow ?: playbackRepository?.playerState ?: return@launch
            var previousState: PlayerState? = null
            stateFlow.collect { currentState ->
                val prev = previousState
                previousState = currentState
                if (prev == null) return@collect

                val wasPlaying = prev.isPlaying
                val isPlaying = currentState.isPlaying

                val justPaused = wasPlaying && !isPlaying
                val justCompleted = !prev.isCompleted && currentState.isCompleted
                val prevEpisode = prev.currentEpisode
                val currentEpisode = currentState.currentEpisode
                val trackChanged = prevEpisode != null &&
                    currentEpisode != null &&
                    prevEpisode.id != currentEpisode.id

                if (justPaused || justCompleted || trackChanged) {
                    executePushInternal()
                }
            }
        }
    }

    private fun observeQueueMutations() {
        applicationScope.launch(ioDispatcher + coordinatorJob) {
            queueDao.getQueueMetadataFlow()
                .filter { it?.isDirty == true }
                .debounce(QUEUE_MUTATION_DEBOUNCE_MS)
                .collect {
                    executePushInternal()
                }
        }
    }

    private fun observeLibraryMutations() {
        applicationScope.launch(ioDispatcher + coordinatorJob) {
            val stateFlow = playerStateFlow ?: playbackRepository?.playerState
            val playingIds = stateFlow?.let { playingEpisodeHistoryIds(it) } ?: flowOf(emptyList())
            playingIds
                .flatMapLatest { ids -> libraryPushSignals(ids, stateFlow) }
                .collect {
                    executePushInternal()
                }
        }
    }

    /** The history IDs of the episode that is playing; empty while nothing plays. */
    private fun playingEpisodeHistoryIds(stateFlow: StateFlow<PlayerState>): Flow<List<String>> =
        stateFlow
            .map { state -> state.currentEpisode?.id?.takeIf { state.isPlaying } }
            .distinctUntilChanged()
            .map { episodeId -> episodeId?.let { historyIdsOf(it) }.orEmpty() }

    private suspend fun historyIdsOf(episodeId: String): List<String> {
        val ids = try {
            playingHistoryIds(episodeId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve the playing episode's IDs: ${e.message}")
            emptyList()
        }
        return (listOf(episodeId) + ids).distinct()
    }

    /**
     * When to push library changes. With nothing playing ([playingIds] empty), any dirty row pushes
     * after [LIBRARY_MUTATION_DEBOUNCE_MS]. While an episode plays, its own row is left out of that
     * count and pushes at most once per [PLAYING_PROGRESS_PUSH_INTERVAL_MS], or after
     * [LIBRARY_MUTATION_DEBOUNCE_MS] when its like changes; the playback milestones push it on pause,
     * completion and track change. While playing, other rows push only when their dirty counts change:
     * each progress save re-runs the count queries, so rows a failed push left dirty would otherwise
     * push again on every save. Those rows are retried by the progress interval, the milestones,
     * backgrounding and the sync worker.
     */
    private fun libraryPushSignals(playingIds: List<String>, stateFlow: StateFlow<PlayerState>?): Flow<Unit> {
        val dirtyHistory = if (playingIds.isEmpty()) {
            listeningHistoryDao.getDirtyCountFlow()
        } else {
            listeningHistoryDao.getDirtyCountExcludingFlow(playingIds)
        }
        val dirtyCounts = combine(podcastDao.getDirtyCountFlow(), dirtyHistory) { podcastsDirty, historyDirty ->
            podcastsDirty to historyDirty
        }
        // Debounced before the filter, so rows another push already sent (count back to 0) don't push again.
        val changes = (if (playingIds.isEmpty()) dirtyCounts else dirtyCounts.distinctUntilChanged())
            .map { (podcastsDirty, historyDirty) -> podcastsDirty + historyDirty }
            .debounce(LIBRARY_MUTATION_DEBOUNCE_MS)
            .filter { it > 0 }
            .map { }
        if (playingIds.isEmpty() || stateFlow == null) return changes

        val playingProgress = flow {
            listeningHistoryDao.hasDirtyHistoryForFlow(playingIds)
                .filter { it }
                .conflate()
                .collect {
                    delay(PLAYING_PROGRESS_PUSH_INTERVAL_MS)
                    if (listeningHistoryDao.hasDirtyHistoryForFlow(playingIds).first()) emit(Unit)
                }
        }
        val playingLike = stateFlow
            .map { it.isLiked }
            .distinctUntilChanged()
            .drop(1)
            .debounce(LIBRARY_MUTATION_DEBOUNCE_MS)
            .map { }
        return merge(changes, playingProgress, playingLike)
    }

    companion object {
        private const val TAG = "CloudSyncTriggerCoordinator"
        const val FOREGROUND_THROTTLE_MS = 15_000L
        const val BACKGROUND_PUSH_TIMEOUT_MS = 3_000L
        const val QUEUE_MUTATION_DEBOUNCE_MS = 2_000L
        const val LIBRARY_MUTATION_DEBOUNCE_MS = 1_500L
        const val PLAYING_PROGRESS_PUSH_INTERVAL_MS = 5 * 60_000L
    }
}

internal fun canSyncUser(user: BoxLoreUser?): Boolean {
    if (user == null) return false
    val isPassword = user.providerId == "password" || user.providerId == null
    if (isPassword && !user.isEmailVerified) {
        return false
    }
    return true
}
