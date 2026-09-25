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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Event-driven lifecycle and interaction coordinator for Boxlore Realtime Cloud Sync.
 * Coalesces and debounces triggers across auth state changes, process lifecycle,
 * playback milestones, and database mutations without mid-play polling.
 */
@OptIn(FlowPreview::class)
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
) : DefaultLifecycleObserver {

    private val _syncStatusFlow = MutableStateFlow<CloudSyncUiStatus>(
        if (boxcastPrefs.getLastSyncTimestamp() > 0L) {
            CloudSyncUiStatus.Success(boxcastPrefs.getLastSyncTimestamp())
        } else {
            CloudSyncUiStatus.Idle
        },
    )
    val syncStatusFlow: StateFlow<CloudSyncUiStatus> = _syncStatusFlow.asStateFlow()

    @Volatile
    private var lastForegroundSyncTimestamp = 0L

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
        val now = clock()
        if (now - lastForegroundSyncTimestamp >= FOREGROUND_THROTTLE_MS) {
            lastForegroundSyncTimestamp = now
            applicationScope.launch(ioDispatcher) {
                if (canSyncUser(authRepository.currentUser.value)) {
                    syncNowInternal()
                }
            }
        }
    }

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
                lastForegroundSyncTimestamp = clock()
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
                        syncNowInternal()
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
                if (shouldSyncOnReconnect(prev, isOnline) && hasDirtyItems()) {
                    syncNowInternal()
                }
            }
        }
    }

    private fun shouldSyncOnReconnect(wasOffline: Boolean?, isOnline: Boolean): Boolean =
        wasOffline == false && isOnline && authRepository.currentUserId != null

    private suspend fun hasDirtyItems(): Boolean {
        if (podcastDao.getDirtyPodcasts().isNotEmpty()) return true
        if (listeningHistoryDao.getDirtyListeningHistory().isNotEmpty()) return true
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
            combine(
                podcastDao.getDirtyCountFlow(),
                listeningHistoryDao.getDirtyCountFlow(),
            ) { podcastsDirty, historyDirty ->
                podcastsDirty + historyDirty
            }
                .filter { it > 0 }
                .debounce(LIBRARY_MUTATION_DEBOUNCE_MS)
                .collect {
                    executePushInternal()
                }
        }
    }

    companion object {
        private const val TAG = "CloudSyncTriggerCoordinator"
        const val FOREGROUND_THROTTLE_MS = 15_000L
        const val BACKGROUND_PUSH_TIMEOUT_MS = 3_000L
        const val QUEUE_MUTATION_DEBOUNCE_MS = 2_000L
        const val LIBRARY_MUTATION_DEBOUNCE_MS = 1_500L
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
