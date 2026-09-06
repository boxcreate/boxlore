package cx.aswin.boxlore.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackSession(
    val podcastId: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long,
    // Cached Metadata
    val episodeTitle: String,
    val podcastTitle: String,
    val imageUrl: String?, // Primary (Episode) Art
    val podcastImageUrl: String?, // Fallback (Podcast) Art
    val audioUrl: String?,
    val enclosureType: String? = null,
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L,
    val bufferedPosition: Long = 0L,
    val currentEpisode: Episode? = null,
    val currentPodcast: Podcast? = null,
    val isLoading: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val seekBackwardMs: Long = PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS,
    val seekForwardMs: Long = PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS,
    val sleepTimerEnd: Long? = null,
    val sleepAtEndOfEpisode: Boolean = false, // Dynamic mode: sleep when episode ends
    val queue: List<Episode> = emptyList(),
    val isLiked: Boolean = false,
    val isCompleted: Boolean = false,
    val showLateNightNudge: Boolean = false,
    val currentChapters: List<cx.aswin.boxlore.core.model.Chapter> = emptyList(),
    val isChaptersLoading: Boolean = false,
    val isChaptersNative: Boolean = false,
    val currentTranscript: List<TranscriptSegment> = emptyList(),
    val autoTranscriptState: AutoTranscriptState = AutoTranscriptState.NONE,
    val autoChaptersState: AutoTranscriptState = AutoTranscriptState.NONE,
    val autoTranscriptLimitLeft: Int? = null,
    val playbackRoute: PlaybackRouteState = PlaybackRouteState(),
    val sameShowContinuation: SameShowContinuationState = SameShowContinuationState.HIDDEN,
)

object SleepTimerHolder {
    @Volatile var activeSleepTimerEndMs: Long? = null

    @Volatile var sleepAtEndOfEpisode: Boolean = false
}

object PlaybackLifecycleSignals {
    @Volatile var serviceOwnedNaturalAdvanceEpisodeId: String? = null

    private val pendingZeroStartEpisodeId =
        java.util.concurrent.atomic
            .AtomicReference<String?>()

    fun markPendingZeroStart(episodeId: String) {
        pendingZeroStartEpisodeId.set(episodeId)
    }

    fun consumePendingZeroStart(episodeId: String): Boolean {
        while (true) {
            val pending = pendingZeroStartEpisodeId.get() ?: return false
            if (pending != episodeId) return false
            if (pendingZeroStartEpisodeId.compareAndSet(pending, null)) return true
        }
    }
}

private fun entryPointBundle(entryPointKey: String?): android.os.Bundle? = entryPointKey?.let { key ->
    android.os.Bundle().apply { putString("entry_point", key) }
}

@Suppress("LongParameterList", "TooManyFunctions")
class PlaybackRepository internal constructor(
    private val context: Context,
    private val listeningHistoryDao: cx.aswin.boxlore.core.database.ListeningHistoryDao,
    private val listeningSessionDao: cx.aswin.boxlore.core.database.ListeningSessionDao,
    private val listeningRollupDao: cx.aswin.boxlore.core.database.ListeningRollupDao,
    private val listeningInsightsMaintenance: cx.aswin.boxlore.core.database.ListeningInsightsMaintenance,
    private val queueRepository: cx.aswin.boxlore.core.playback.QueueRepository,
    private val podcastRepository: PodcastRepository,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
    internal val userPreferencesRepository: UserPreferencesRepository,
    internal val historyStore: PlaybackHistoryStore,
) : ListeningHistoryBackupPort by historyStore,
    ListeningHistoryPort by historyStore {
    /** Nested alias so existing `PlaybackRepository.RemovedQueueItem` call sites keep compiling. */
    typealias RemovedQueueItem = cx.aswin.boxlore.core.playback.RemovedQueueItem

    /**
     * AppContainer / production entry. Builds [historyStore] from the DAO and repository args
     * (same call site as before the delegation split).
     */
    constructor(
        context: Context,
        listeningHistoryDao: cx.aswin.boxlore.core.database.ListeningHistoryDao,
        listeningSessionDao: cx.aswin.boxlore.core.database.ListeningSessionDao,
        listeningRollupDao: cx.aswin.boxlore.core.database.ListeningRollupDao,
        listeningInsightsMaintenance: cx.aswin.boxlore.core.database.ListeningInsightsMaintenance,
        queueRepository: cx.aswin.boxlore.core.playback.QueueRepository,
        podcastRepository: PodcastRepository,
        rankingFeedbackRepository: RankingFeedbackRepository,
        userPreferencesRepository: UserPreferencesRepository,
    ) : this(
        context = context,
        listeningHistoryDao = listeningHistoryDao,
        listeningSessionDao = listeningSessionDao,
        listeningRollupDao = listeningRollupDao,
        listeningInsightsMaintenance = listeningInsightsMaintenance,
        queueRepository = queueRepository,
        podcastRepository = podcastRepository,
        rankingFeedbackRepository = rankingFeedbackRepository,
        userPreferencesRepository = userPreferencesRepository,
        historyStore =
        defaultPlaybackHistoryStore(
            context = context,
            listeningHistoryDao = listeningHistoryDao,
            listeningSessionDao = listeningSessionDao,
            listeningRollupDao = listeningRollupDao,
            listeningInsightsMaintenance = listeningInsightsMaintenance,
            podcastRepository = podcastRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
        ),
    )

    internal val mediaHandle = PlaybackMediaControllerHandle()
    val controller: MediaController? get() = mediaHandle.controller
    internal var controllerBridge: PlaybackMediaControllerBridge? = null
    internal var hasActiveCastSession: Boolean? = null

    internal fun endCurrentCastSession(stopReceiverApplication: Boolean) {
        runCatching {
            androidx.media3.cast.Cast
                .getSingletonInstance(context)
                .endCurrentSession(stopReceiverApplication)
        }.onFailure { exception ->
            android.util.Log.e("PlaybackRepo", "Unable to end Cast session", exception)
        }
    }

    internal val repositoryScope = historyStore.playerDeps.scope
    internal val playerStateFlow: MutableStateFlow<PlayerState> = historyStore.playerDeps.playerStateFlow
    val playerState = playerStateFlow.asStateFlow()

    fun setUiForeground(isForeground: Boolean) {
        if (PlaybackUiVisibility.isForeground.value == isForeground) return
        PlaybackUiVisibility.setForeground(isForeground)
        if (isForeground) {
            runOnMainThread {
                val controllerNow = mediaHandle.controller
                if (controllerNow != null) {
                    playerStateFlow.value =
                        PlaybackControllerStatePolicy.mergeProgress(
                            previous = playerStateFlow.value,
                            snapshot = controllerNow.progressSnapshot(),
                        )
                }
                refreshRestoredProgressIfPlayerEmpty()
                if (
                    controllerNow != null &&
                    PlaybackPowerPolicy.shouldRunUiPositionTicker(
                        isUiForeground = true,
                        isPlaying = controllerNow.isPlaying,
                        isLoading = controllerNow.isLoading,
                    )
                ) {
                    startProgressTicker()
                }
            }
        } else {
            stopProgressTicker()
        }
    }

    // Preferences for session state
    private val prefs =
        PrefsFileMigrator.open(
            context,
            newName = PrefsFileMigrator.Files.PLAYER,
            oldName = PrefsFileMigrator.LegacyFiles.PLAYER,
        )

    @Suppress("PropertyName")
    private val KEY_PLAYER_DISMISSED = "player_dismissed"

    @Suppress("PropertyName")
    private val KEY_LAST_SLEEP_PROMPT_WINDOW_ID = "last_sleep_prompt_window_id"

    @Suppress("PropertyName")
    private val KEY_DEBUG_SKIP_SLEEP_WINDOW = "debug_skip_sleep_window"

    private var currentSkipBehavior: String = "just_skip"

    fun getOrCreateDeviceUuid(): String {
        val key = "device_uuid"
        var uuid = prefs.getString(key, null)
        if (uuid == null) {
            uuid =
                java.util.UUID
                    .randomUUID()
                    .toString()
            prefs.edit().putString(key, uuid).apply()
        }
        // Never log the raw device UUID (PII / install fingerprint).
        if (android.util.Log.isLoggable("BoxLoreDeviceUuid", android.util.Log.DEBUG)) {
            android.util.Log.d(
                "BoxLoreDeviceUuid",
                "device uuid ready (len=${uuid.length})",
            )
        }
        return uuid
    }

    private var progressJob: Job? = null

    @Suppress("PropertyName")
    private val QUEUE_MAX_SIZE = 50

    // Local memory of rejected auto-fill suggestions (feeds the SmartQueueEngine).
    private val queueSkipMemory = QueueSkipMemory.fromContext(context)

    internal val chaptersController =
        PlaybackChaptersTranscriptController(
            scope = repositoryScope,
            playerState = playerState,
            playerStateFlow = playerStateFlow,
            podcastRepository = podcastRepository,
            deviceUuid = ::getOrCreateDeviceUuid,
        )

    internal val sleepController =
        PlaybackSleepController(
            scope = repositoryScope,
            playerStateFlow = playerStateFlow,
            prefs = prefs,
            mediaHandle = mediaHandle,
            stopProgressTicker = ::stopProgressTicker,
            lastSleepPromptWindowIdKey = KEY_LAST_SLEEP_PROMPT_WINDOW_ID,
            debugSkipSleepWindowKey = KEY_DEBUG_SKIP_SLEEP_WINDOW,
        )

    internal val queueCoordinator =
        PlaybackQueueCoordinator(
            scope = repositoryScope,
            playerStateFlow = playerStateFlow,
            mediaHandle = mediaHandle,
            queueRepository = queueRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
            queueSkipMemory = queueSkipMemory,
            prefs = prefs,
            playerDismissedKey = KEY_PLAYER_DISMISSED,
            queueMaxSize = QUEUE_MAX_SIZE,
            checkSavedProgress = { startEpisodeId, initialPositionMs, entryPoint, sourceContext ->
                checkSavedProgress(startEpisodeId, initialPositionMs, entryPoint, sourceContext)
            },
            onPlaybackStarted = { sleepController.onPlaybackStarted() },
            storePendingEntryPoint = ::storePendingEntryPoint,
            ensureCurrentHistoryRow = ::ensureCurrentHistoryRow,
            stopProgressTicker = ::stopProgressTicker,
        )

    internal val transportHelper =
        PlaybackTransportHelper(
            scope = repositoryScope,
            playerStateFlow = playerStateFlow,
            mediaHandle = mediaHandle,
            storePendingEntryPoint = ::storePendingEntryPoint,
            resolveInitialSeekMs = { episodeId, entryPointKey ->
                checkSavedProgress(
                    startEpisodeId = episodeId,
                    initialPositionMs = null,
                    entryPoint = PlaybackEntryPoint.GENERIC,
                    sourceContext = entryPointBundle(entryPointKey),
                ).first
            },
            resolvePersistedResumePositionMs = { episodeId, entryPointKey ->
                listeningHistoryDao.getHistoryItem(episodeId)?.let {
                    checkSavedProgress(episodeId, null, PlaybackEntryPoint.GENERIC, entryPointBundle(entryPointKey)).first
                }
            },
            playQueue = { episodes, podcast, startIndex, entryPoint, initialPositionMs, sourceContext ->
                queueCoordinator.playQueue(
                    episodes,
                    podcast,
                    startIndex,
                    entryPoint,
                    initialPositionMs,
                    sourceContext,
                )
            },
        )

    internal val continuationCoordinator =
        SameShowContinuationCoordinator(
            scope = repositoryScope,
            playerState = playerState,
            playerStateFlow = playerStateFlow,
            podcastRepository = podcastRepository,
            userPreferencesRepository = userPreferencesRepository,
            queueCoordinator = queueCoordinator,
        )

    init {
        getOrCreateDeviceUuid()
        initializeMediaController()
        historyStore.monitorLikeState()
        chaptersController.monitorChaptersAndTranscripts()
        continuationCoordinator.startMonitoring()
        repositoryScope.launch {
            userPreferencesRepository.skipBehaviorStream.collect {
                currentSkipBehavior = it
            }
        }
        repositoryScope.launch {
            userPreferencesRepository.seekBackwardMsStream.collect { value ->
                playerStateFlow.value =
                    playerStateFlow.value.copy(
                        seekBackwardMs = value.coerceAtLeast(1_000L),
                    )
            }
        }
        repositoryScope.launch {
            userPreferencesRepository.seekForwardMsStream.collect { value ->
                playerStateFlow.value =
                    playerStateFlow.value.copy(
                        seekForwardMs = value.coerceAtLeast(1_000L),
                    )
            }
        }
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, BoxLorePlaybackService::class.java))
        mediaHandle.future = MediaController.Builder(context, sessionToken).buildAsync()
        mediaHandle.future?.addListener({
            mediaHandle.controller = mediaHandle.future?.get()
            // Restore the persisted playback speed so UI and ExoPlayer stay aligned
            // across process death and session clears (even when the rate is already 1×).
            repositoryScope.launch {
                val savedSpeed =
                    userPreferencesRepository.playbackSpeedStream
                        .first()
                        .coerceIn(0.5f, 3.0f)
                val controller = mediaHandle.controller
                if (controller != null && controller.playbackParameters.speed != savedSpeed) {
                    controller.playbackParameters = PlaybackParameters(savedSpeed)
                }
                playerStateFlow.value = playerStateFlow.value.copy(playbackSpeed = savedSpeed)
            }
            controllerBridge =
                PlaybackMediaControllerBridge(
                    context = context,
                    scope = repositoryScope,
                    playerStateFlow = playerStateFlow,
                    mediaHandle = mediaHandle,
                    queueRepository = queueRepository,
                    currentSkipBehavior = { currentSkipBehavior },
                    onPlaybackStarted = { sleepController.onPlaybackStarted() },
                    startProgressTicker = ::startProgressTicker,
                    stopProgressTicker = ::stopProgressTicker,
                    cancelSleepTimer = { sleepController.cancelSleepTimerJob() },
                    syncQueueToDb = { queueCoordinator.syncQueueToDb() },
                    reconcileQueueWithController = { queueCoordinator.reconcileQueueWithController() },
                    markEpisodeAsCompleted = { episode, podcast ->
                        historyStore.markEpisodeAsCompleted(episode, podcast)
                    },
                    findPodcastIdForEpisode = { historyStore.findPodcastIdForEpisode(it) },
                    hasActiveCastSession = { hasActiveCastSession },
                )
            controllerBridge?.let { bridge ->
                mediaHandle.controller?.addListener(bridge)
                bridge.syncPlaybackRoute()
            }

            // Sync state from MediaController (handles app coming back from background)
            syncStateFromMediaController()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Sync playback state from the MediaController.
     * Called when MediaController connects (including when app comes back from background).
     */
    private fun syncStateFromMediaController() {
        runOnMainThread {
            val controller = mediaHandle.controller ?: return@runOnMainThread

            val isPlaying = controller.isPlaying
            val isLoading = controller.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            val currentPosition = controller.currentPosition.coerceAtLeast(0)
            val bufferedPosition = controller.bufferedPosition.coerceAtLeast(0)
            val duration = controller.duration.coerceAtLeast(0)
            val hasMedia = controller.mediaItemCount > 0

            if (hasMedia && playerStateFlow.value.currentEpisode == null) {
                // MediaController has media but we don't have metadata - restore from DB
                repositoryScope.launch {
                    val currentItem = controller.currentMediaItem
                    val targetEpisodeId = currentItem?.mediaId?.stripEpisodePrefix()
                    val restored =
                        PlaybackSessionRestoreHelper.resolveRestoredSession(
                            targetEpisodeId = targetEpisodeId,
                            currentItem = currentItem,
                            listeningHistoryDao = listeningHistoryDao,
                            podcastRepository = podcastRepository,
                            savedQueue = playerStateFlow.value.queue,
                        ) ?: return@launch

                    playerStateFlow.value =
                        PlayerState(
                            currentEpisode = restored.episode,
                            currentPodcast = restored.podcast,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            position = currentPosition.takeIf { it > 0L } ?: restored.lastSession.progressMs,
                            bufferedPosition = bufferedPosition,
                            duration = if (duration > 0) duration else restored.lastSession.durationMs,
                            playbackSpeed = controller.playbackParameters.speed,
                            queue = playerStateFlow.value.queue, // Preserve queue
                            isLiked = restored.lastSession.isLiked,
                            playbackRoute = playerStateFlow.value.playbackRoute,
                        )
                    if (isPlaying) startProgressTicker()
                }
            } else {
                // Just sync playback state
                playerStateFlow.value =
                    PlaybackControllerStatePolicy
                        .mergeProgress(
                            previous = playerStateFlow.value,
                            snapshot = controller.progressSnapshot(),
                        ).copy(
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            playbackSpeed = controller.playbackParameters.speed,
                        )
                if (isPlaying) startProgressTicker()
            }
        }
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        if (!PlaybackUiVisibility.isForeground.value) return
        progressJob =
            repositoryScope.launch {
                var controllerNow = mediaHandle.controller
                while (
                    controllerNow != null &&
                    PlaybackPowerPolicy.shouldRunUiPositionTicker(
                        isUiForeground = PlaybackUiVisibility.isForeground.value,
                        isPlaying = controllerNow.isPlaying,
                        isLoading = controllerNow.isLoading,
                    )
                ) {
                    playerStateFlow.value =
                        PlaybackControllerStatePolicy.mergeProgress(
                            previous = playerStateFlow.value,
                            snapshot = controllerNow.progressSnapshot(),
                        )
                    kotlinx.coroutines.delay(PlaybackPowerPolicy.UI_POSITION_POLL_INTERVAL_MS)
                    controllerNow = mediaHandle.controller
                }
            }
    }

    private fun refreshRestoredProgressIfPlayerEmpty() {
        runOnMainThread {
            val controller = mediaHandle.controller
            if (controller?.isConnected == true && controller.mediaItemCount > 0) return@runOnMainThread
            val episodeId = playerStateFlow.value.currentEpisode?.id ?: return@runOnMainThread

            repositoryScope.launch {
                val persisted = listeningHistoryDao.getHistoryItem(episodeId) ?: return@launch
                val latestController = mediaHandle.controller
                if (latestController?.isConnected == true && latestController.mediaItemCount > 0) {
                    return@launch
                }
                val current = playerStateFlow.value
                if (current.currentEpisode?.id != episodeId) return@launch
                playerStateFlow.value =
                    current.copy(
                        position = persisted.progressMs.coerceAtLeast(0L),
                        duration = persisted.durationMs.takeIf { it > 0L } ?: current.duration,
                        isCompleted = persisted.isCompleted,
                        isLiked = persisted.isLiked,
                    )
            }
        }
    }

    /**
     * Creates the history row needed by the service-owned progress writer. Existing rows are never
     * replaced from UI state, because that state intentionally stops polling in the background.
     */
    private suspend fun ensureCurrentHistoryRow() {
        val state = playerStateFlow.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return
        val history =
            ListeningHistoryUpsertLogic.buildProgressSaveEntity(
                ListeningHistoryUpsertLogic.ProgressSaveInput(
                    podcastId = podcast.id,
                    episodeId = episode.id,
                    positionMs = state.position.coerceAtLeast(0L),
                    durationMs =
                    state.duration.takeIf { it > 0L }
                        ?: episode.duration.toLong().coerceAtLeast(0L) * 1_000L,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = podcast.imageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName = podcast.title,
                    isCompleted = false,
                    isLiked = state.isLiked,
                    lastPlayedAt = System.currentTimeMillis(),
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                ),
            )
        val inserted = listeningHistoryDao.insertIfAbsent(history)
        if (inserted == -1L) {
            listeningHistoryDao.updateLastPlayedAt(history.episodeId, history.lastPlayedAt)
        }
        listeningHistoryDao.enrichMetadataIfMissing(
            episodeId = history.episodeId,
            podcastId = history.podcastId,
            episodeTitle = history.episodeTitle,
            episodeImageUrl = history.episodeImageUrl,
            podcastImageUrl = history.podcastImageUrl,
            episodeAudioUrl = history.episodeAudioUrl,
            podcastName = history.podcastName,
            durationMs = history.durationMs,
            enclosureType = history.enclosureType,
            episodeDescription = history.episodeDescription,
        )
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun shouldResetPlaybackForMixtape(savedProgressMs: Long, durationMs: Long, entryPoint: PlaybackEntryPoint,): Boolean = MixtapeResumePolicy.shouldResetPlayback(
        savedProgressMs = savedProgressMs,
        durationMs = durationMs,
        entryPoint = entryPoint,
    )

    private suspend fun checkSavedProgress(
        startEpisodeId: String?,
        initialPositionMs: Long?,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        sourceContext: android.os.Bundle? = null,
    ): Pair<Long, Boolean> {
        var initialLikeState = false
        var savedProgressMs = 0L
        var isCompleted = false
        var lastPlayedAtMs: Long? = null
        var resetRequested = false
        if (startEpisodeId != null) {
            val saved = listeningHistoryDao.getHistoryItem(startEpisodeId)
            if (saved != null) {
                savedProgressMs = saved.progressMs
                isCompleted = saved.isCompleted
                lastPlayedAtMs = saved.lastPlayedAt
                resetRequested =
                    shouldResetPlaybackForMixtape(
                        saved.progressMs,
                        saved.durationMs,
                        entryPoint,
                    )
                initialLikeState = saved.isLiked
            }
        }
        val entryPointKey =
            PlaybackMediaIdPolicy.parseEntryPointString(sourceContext)
                ?: entryPoint.takeIf { it != PlaybackEntryPoint.GENERIC }?.name?.lowercase()
        val staleRestartEnabled = userPreferencesRepository.restartForgottenEpisodesStream.first()
        val initialPosition =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = initialPositionMs,
                savedProgressMs = savedProgressMs,
                isCompleted = isCompleted,
                skipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
                resetRequested = resetRequested,
                resumeIntent = PlaybackSkipPolicy.resumeIntentFromEntryPoint(entryPointKey),
                lastPlayedAtMs = lastPlayedAtMs,
                staleRestartEnabled = staleRestartEnabled,
            )
        return Pair(initialPosition.positionMs, initialLikeState)
    }

    private fun storePendingEntryPoint(entryPointContext: android.os.Bundle?) {
        if (entryPointContext != null) {
            val map = mutableMapOf<String, Any>()
            entryPointContext.keySet().forEach { key ->
                @Suppress("DEPRECATION")
                val value = entryPointContext.get(key)
                if (value != null) {
                    map[key] = value
                }
            }
            if (map.isNotEmpty()) {
                cx.aswin.boxlore.core.analytics.PendingEntryPoint
                    .set(map)
            }
        }
    }

    /**
     * Restore the last played session on app startup (does NOT auto-play)
     */
    suspend fun restoreLastSession(): Boolean =
        withContext(PlaybackThreadPolicy.mainDispatcher) {
            // Don't restore if player was explicitly dismissed
            if (prefs.getBoolean(KEY_PLAYER_DISMISSED, false)) {
                return@withContext false
            }

            val controller = mediaHandle.controller
            val controllerItem = controller?.currentMediaItem
            val targetEpisodeId = controllerItem?.mediaId?.stripEpisodePrefix()
            val savedQueue = queueRepository.getQueueSnapshot()

            val restored =
                PlaybackSessionRestoreHelper.resolveRestoredSession(
                    targetEpisodeId = targetEpisodeId,
                    currentItem = controllerItem,
                    listeningHistoryDao = listeningHistoryDao,
                    podcastRepository = podcastRepository,
                    savedQueue = savedQueue,
                ) ?: return@withContext false

            // If saved queue is empty but we have an episode, make a single-item queue
            val restoredQueue = if (savedQueue.isEmpty()) listOf(restored.episode) else savedQueue

            // Prefer live MediaController truth when the playback service is still running
            // (e.g. user swiped the app from recents while audio continued). Forcing
            // isPlaying=false here races with syncStateFromMediaController() and leaves
            // the UI paused while ExoPlayer keeps playing.
            val controllerPlaying = controller?.isPlaying == true
            val controllerPosition = controller?.currentPosition?.takeIf { it > 0 }
            val controllerDuration = controller?.duration?.takeIf { it > 0 }

            playerStateFlow.value =
                PlaybackControlSync.withSyncedPlaybackSpeed(
                    playerStateFlow.value.copy(
                        currentEpisode = restored.episode,
                        currentPodcast = restored.podcast,
                        isPlaying = controllerPlaying,
                        position = controllerPosition ?: restored.lastSession.progressMs,
                        duration = controllerDuration ?: restored.lastSession.durationMs,
                        isLiked = restored.lastSession.isLiked,
                        queue = restoredQueue,
                    ),
                    controllerSpeed = controller?.playbackParameters?.speed,
                )

            // Re-sync after metadata restore in case the controller connected first and
            // onIsPlayingChanged won't fire again (already playing when the listener attached).
            if (controller != null) {
                syncStateFromMediaController()
            }

            true
        }

    /**
     * Clear the current session (for swipe-to-dismiss)
     */
    fun clearSession() {
        runOnMainThread {
            val previous = playerStateFlow.value
            val controllerSpeed = mediaHandle.controller?.playbackParameters?.speed
            mediaHandle.controller?.stop()
            mediaHandle.controller?.clearMediaItems()
            stopProgressTicker()
            sleepController.cancelTimer()
            // Keep speed / seek sizes so the next episode's UI matches ExoPlayer + prefs.
            playerStateFlow.value =
                PlaybackControlSync.clearedStatePreservingControls(previous, controllerSpeed)
            // Mark as dismissed so we don't restore on next app launch
            prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, true).apply()
        }
    }

    fun seekTo(positionMs: Long, play: Boolean = false,) {
        runOnMainThread {
            mediaHandle.controller?.seekTo(positionMs)
            playerStateFlow.value = playerStateFlow.value.copy(position = positionMs)

            if (play) {
                mediaHandle.controller?.play()
            }
        }
    }
}

internal object PlaybackOutputVolumePolicy {
    fun targetVolume(requestedVolume: Int, route: PlaybackRouteState, commandAvailable: Boolean,): Int? {
        if (!route.canControlVolume || !commandAvailable) return null
        return requestedVolume.coerceIn(route.minimumVolume, route.maximumVolume)
    }
}

private fun MediaController.progressSnapshot(): PlaybackControllerStatePolicy.Snapshot = PlaybackControllerStatePolicy.Snapshot(
    hasMedia = isConnected && mediaItemCount > 0,
    positionMs = currentPosition,
    bufferedPositionMs = bufferedPosition,
    durationMs = duration,
)
