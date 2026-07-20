package cx.aswin.boxlore.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
)

object SleepTimerHolder {
    @Volatile var activeSleepTimerEndMs: Long? = null

    @Volatile var sleepAtEndOfEpisode: Boolean = false
}

object PlaybackLifecycleSignals {
    @Volatile var serviceOwnedNaturalAdvanceEpisodeId: String? = null

    @Volatile var effectiveSkipEndingMs: Long? = null
}

@Suppress("LongParameterList") // historyStore is required for `by` delegation; public ctor stays at 9 args
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
) : ListeningHistoryBackupPort by historyStore, ListeningHistoryPort by historyStore {
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

    internal val repositoryScope = historyStore.playerDeps.scope
    internal val playerStateFlow: MutableStateFlow<PlayerState> = historyStore.playerDeps.playerStateFlow
    val playerState = playerStateFlow.asStateFlow()

    // Preferences for session state
    private val prefs =
        PrefsFileMigrator.open(
            context,
            newName = PrefsFileMigrator.Files.PLAYER,
            oldName = PrefsFileMigrator.LegacyFiles.PLAYER,
        )
    private val KEY_PLAYER_DISMISSED = "player_dismissed"
    private val KEY_LAST_SLEEP_PROMPT_WINDOW_ID = "last_sleep_prompt_window_id"
    private val KEY_DEBUG_SKIP_SLEEP_WINDOW = "debug_skip_sleep_window"

    private var currentSkipBehavior: String = "just_skip"

    @Volatile private var currentSkipEndingMs: Long = 0L

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
            checkSavedProgress = { startEpisodeId, initialPositionMs, entryPoint ->
                checkSavedProgress(startEpisodeId, initialPositionMs, entryPoint)
            },
            onPlaybackStarted = { sleepController.onPlaybackStarted() },
            storePendingEntryPoint = ::storePendingEntryPoint,
            saveCurrentState = { updateLastPlayedAt -> saveCurrentState(updateLastPlayedAt) },
            stopProgressTicker = ::stopProgressTicker,
        )

    internal val transportHelper =
        PlaybackTransportHelper(
            scope = repositoryScope,
            playerStateFlow = playerStateFlow,
            mediaHandle = mediaHandle,
            listeningHistoryDao = listeningHistoryDao,
            storePendingEntryPoint = ::storePendingEntryPoint,
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

    init {
        getOrCreateDeviceUuid()
        initializeMediaController()
        historyStore.monitorLikeState()
        chaptersController.monitorChaptersAndTranscripts()
        repositoryScope.launch {
            userPreferencesRepository.skipBehaviorStream.collect {
                currentSkipBehavior = it
            }
        }
        repositoryScope.launch {
            userPreferencesRepository.skipEndingMsStream.collect {
                currentSkipEndingMs = it.coerceAtLeast(0L)
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
            mediaHandle.controller?.addListener(
                PlaybackMediaControllerBridge(
                    context = context,
                    scope = repositoryScope,
                    playerStateFlow = playerStateFlow,
                    mediaHandle = mediaHandle,
                    queueRepository = queueRepository,
                    currentSkipBehavior = { currentSkipBehavior },
                    activePlaybackStartTimeMs = { activePlaybackStartTimeMs },
                    setActivePlaybackStartTimeMs = { activePlaybackStartTimeMs = it },
                    onPlaybackStarted = { sleepController.onPlaybackStarted() },
                    startProgressTicker = ::startProgressTicker,
                    stopProgressTicker = ::stopProgressTicker,
                    saveCurrentState = { updateLastPlayedAt -> saveCurrentState(updateLastPlayedAt) },
                    cancelSleepTimer = { sleepController.cancelSleepTimerJob() },
                    syncQueueToDb = { queueCoordinator.syncQueueToDb() },
                    reconcileQueueWithController = { queueCoordinator.reconcileQueueWithController() },
                    markEpisodeAsCompleted = { episode, podcast ->
                        historyStore.markEpisodeAsCompleted(episode, podcast)
                    },
                    findPodcastIdForEpisode = { historyStore.findPodcastIdForEpisode(it) },
                ),
            )

            // Sync state from MediaController (handles app coming back from background)
            syncStateFromMediaController()
        }, MoreExecutors.directExecutor())
    }

    /**
     * Sync playback state from the MediaController.
     * Called when MediaController connects (including when app comes back from background).
     */
    private fun syncStateFromMediaController() {
        val controller = mediaHandle.controller ?: return

        val isPlaying = controller.isPlaying
        val isLoading = controller.playbackState == androidx.media3.common.Player.STATE_BUFFERING
        val currentPosition = controller.currentPosition.coerceAtLeast(0)
        val bufferedPosition = controller.bufferedPosition.coerceAtLeast(0)
        val duration = controller.duration.coerceAtLeast(0)
        val hasMedia = controller.mediaItemCount > 0

        if (hasMedia && playerStateFlow.value.currentEpisode == null) {
            // MediaController has media but we don't have metadata - restore from DB
            repositoryScope.launch {
                val lastSession = listeningHistoryDao.getLastPlayedSessionAny()
                if (lastSession != null) {
                    var episode =
                        Episode(
                            id = lastSession.episodeId,
                            title = lastSession.episodeTitle,
                            description = lastSession.episodeDescription ?: "",
                            audioUrl = lastSession.episodeAudioUrl ?: "",
                            imageUrl = lastSession.episodeImageUrl,
                            duration = (lastSession.durationMs / 1000).toInt(),
                            publishedDate = 0L,
                            enclosureType = lastSession.enclosureType,
                        )
                    val podcast =
                        Podcast(
                            id = lastSession.podcastId,
                            title = lastSession.podcastName,
                            artist = "",
                            imageUrl = lastSession.podcastImageUrl ?: "",
                            description = null,
                            genre = "Podcast",
                        )
                    // Enrich with P2.0 data from queue if available
                    val currentQueue = playerStateFlow.value.queue
                    val queueEp = currentQueue.find { it.id == episode.id }
                    if (queueEp != null) {
                        episode =
                            episode.copy(
                                chaptersUrl = queueEp.chaptersUrl,
                                transcriptUrl = queueEp.transcriptUrl,
                                persons = queueEp.persons,
                                transcripts = queueEp.transcripts,
                            )
                    }
                    playerStateFlow.value =
                        PlayerState(
                            currentEpisode = episode,
                            currentPodcast = podcast,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            position = currentPosition,
                            bufferedPosition = bufferedPosition,
                            duration = if (duration > 0) duration else lastSession.durationMs,
                            playbackSpeed = controller.playbackParameters.speed,
                            queue = playerStateFlow.value.queue, // Preserve queue
                            isLiked = lastSession.isLiked,
                        )
                    if (isPlaying) startProgressTicker()
                }
            }
        } else {
            // Just sync playback state
            playerStateFlow.value =
                playerStateFlow.value.copy(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    position = if (currentPosition > 0) currentPosition else playerStateFlow.value.position,
                    bufferedPosition = bufferedPosition,
                    duration = if (duration > 0) duration else playerStateFlow.value.duration,
                    playbackSpeed = controller.playbackParameters.speed,
                )
            if (isPlaying) startProgressTicker()
        }
    }

    private var lastProgressSaveMs = 0L
    private var activePlaybackStartTimeMs = 0L

    private fun startProgressTicker() {
        stopProgressTicker()
        lastProgressSaveMs = System.currentTimeMillis() // Reset so first ticker save is 10s from now
        progressJob =
            repositoryScope.launch {
                while (true) {
                    mediaHandle.controller?.let { controller ->
                        if (controller.isPlaying || controller.isLoading) {
                            val currentPos = controller.currentPosition
                            val bufferedPos = controller.bufferedPosition
                            val currentDur = controller.duration.coerceAtLeast(0)

                            playerStateFlow.value =
                                playerStateFlow.value.copy(
                                    position = currentPos,
                                    bufferedPosition = bufferedPos,
                                    duration = currentDur,
                                )

                            // Save progress every 10 seconds (deterministic)
                            val now = System.currentTimeMillis()
                            if (now - lastProgressSaveMs > 10_000) {
                                val hasBeenPlayingFor10s =
                                    activePlaybackStartTimeMs > 0 &&
                                        (now - activePlaybackStartTimeMs >= 10_000)
                                saveCurrentState(updateLastPlayedAt = hasBeenPlayingFor10s)
                                lastProgressSaveMs = now
                            }
                        }
                    }
                    kotlinx.coroutines.delay(500) // Update every 500ms
                }
            }
    }

    // Helper to save current state
    private suspend fun saveCurrentState(updateLastPlayedAt: Boolean = true) {
        val state = playerStateFlow.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return

        // Check existing completion status to avoid overwriting
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val wasCompleted = existing?.isCompleted ?: false
        val lastPlayed = if (updateLastPlayedAt) System.currentTimeMillis() else (existing?.lastPlayedAt ?: System.currentTimeMillis())

        // While an effective ending trim is active the service is the sole completion owner.
        // In particular, do not let the legacy 95%/long-episode heuristics complete an item
        // before the service observes the configured boundary.
        val isCompletedNow =
            PlaybackSkipPolicy.shouldCompleteFromProgress(
                positionMs = state.position,
                durationMs = state.duration,
                effectiveSkipEndingMs =
                    PlaybackLifecycleSignals.effectiveSkipEndingMs ?: currentSkipEndingMs,
            )
        val finalCompleted = wasCompleted || isCompletedNow
        val finalPosition = if (isCompletedNow && !wasCompleted) 0L else state.position

        historyStore.savePlaybackState(
            ListeningHistoryUpsertLogic.ProgressSaveInput(
                podcastId = podcast.id,
                episodeId = episode.id,
                positionMs = finalPosition,
                durationMs = state.duration,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = podcast.imageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = podcast.title,
                isCompleted = finalCompleted,
                isLiked = state.isLiked,
                lastPlayedAt = lastPlayed,
                enclosureType = episode.enclosureType,
                episodeDescription = episode.description,
            ),
        )
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun shouldResetPlaybackForMixtape(
        savedProgressMs: Long,
        durationMs: Long,
        entryPoint: PlaybackEntryPoint,
    ): Boolean =
        MixtapeResumePolicy.shouldResetPlayback(
            savedProgressMs = savedProgressMs,
            durationMs = durationMs,
            entryPoint = entryPoint,
        )

    private suspend fun checkSavedProgress(
        startEpisodeId: String?,
        initialPositionMs: Long?,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    ): Pair<Long, Boolean> {
        var initialLikeState = false
        var savedProgressMs = 0L
        var isCompleted = false
        var resetRequested = false
        if (startEpisodeId != null) {
            val saved = listeningHistoryDao.getHistoryItem(startEpisodeId)
            if (saved != null) {
                savedProgressMs = saved.progressMs
                isCompleted = saved.isCompleted
                resetRequested =
                    shouldResetPlaybackForMixtape(
                        saved.progressMs,
                        saved.durationMs,
                        entryPoint,
                    )
                initialLikeState = saved.isLiked
            }
        }
        val initialPosition =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = initialPositionMs,
                savedProgressMs = savedProgressMs,
                isCompleted = isCompleted,
                skipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
                resetRequested = resetRequested,
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
    suspend fun restoreLastSession(): Boolean {
        // Don't restore if player was explicitly dismissed
        if (prefs.getBoolean(KEY_PLAYER_DISMISSED, false)) {
            return false
        }

        val lastSession = listeningHistoryDao.getLastPlayedSessionAny() ?: return false

        // Construct Episode WITH podcast metadata (critical for onMediaItemTransition)
        var episode =
            Episode(
                id = lastSession.episodeId,
                title = lastSession.episodeTitle,
                description = "",
                audioUrl = lastSession.episodeAudioUrl ?: return false,
                imageUrl = lastSession.episodeImageUrl,
                podcastImageUrl = lastSession.podcastImageUrl,
                podcastTitle = lastSession.podcastName,
                podcastId = lastSession.podcastId,
                podcastGenre = "Podcast",
                podcastArtist = "",
                duration = (lastSession.durationMs / 1000).toInt(),
                publishedDate = 0L,
                enclosureType = lastSession.enclosureType,
            )

        val podcast =
            Podcast(
                id = lastSession.podcastId,
                title = lastSession.podcastName,
                artist = "",
                imageUrl = lastSession.podcastImageUrl ?: "",
                description = null,
                genre = "Podcast",
            )

        // Restore Queue from DB (queue items now include P2.0 fields)
        val savedQueue = queueRepository.getQueueSnapshot()

        // Enrich restored episode with P2.0 data from queue if available
        val queueEpisode = savedQueue.find { it.id == episode.id }
        if (queueEpisode != null) {
            episode =
                episode.copy(
                    chaptersUrl = queueEpisode.chaptersUrl,
                    transcriptUrl = queueEpisode.transcriptUrl,
                    persons = queueEpisode.persons,
                    transcripts = queueEpisode.transcripts,
                    seasonNumber = queueEpisode.seasonNumber,
                    episodeNumber = queueEpisode.episodeNumber,
                    episodeType = queueEpisode.episodeType,
                )
        }

        // If saved queue is empty but we have an episode, make a single-item queue
        val restoredQueue = if (savedQueue.isEmpty()) listOf(episode) else savedQueue

        // Prefer live MediaController truth when the playback service is still running
        // (e.g. user swiped the app from recents while audio continued). Forcing
        // isPlaying=false here races with syncStateFromMediaController() and leaves
        // the UI paused while ExoPlayer keeps playing.
        val controller = mediaHandle.controller
        val controllerPlaying = controller?.isPlaying == true
        val controllerPosition = controller?.currentPosition?.takeIf { it > 0 }
        val controllerDuration = controller?.duration?.takeIf { it > 0 }

        playerStateFlow.value =
            PlaybackControlSync.withSyncedPlaybackSpeed(
                playerStateFlow.value.copy(
                    currentEpisode = episode,
                    currentPodcast = podcast,
                    isPlaying = controllerPlaying,
                    position = controllerPosition ?: lastSession.progressMs,
                    duration = controllerDuration ?: lastSession.durationMs,
                    isLiked = lastSession.isLiked,
                    queue = restoredQueue,
                ),
                controllerSpeed = controller?.playbackParameters?.speed,
            )

        // Re-sync after metadata restore in case the controller connected first and
        // onIsPlayingChanged won't fire again (already playing when the listener attached).
        if (controller != null) {
            syncStateFromMediaController()
        }

        return true
    }

    /**
     * Clear the current session (for swipe-to-dismiss)
     */
    fun clearSession() {
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

    fun seekTo(
        positionMs: Long,
        play: Boolean = false,
    ) {
        mediaHandle.controller?.seekTo(positionMs)
        playerStateFlow.value = playerStateFlow.value.copy(position = positionMs)

        if (play) {
            mediaHandle.controller?.play()
        }

        // Save state on seek (do not update lastPlayedAt to prevent reordering on scrub)
        repositoryScope.launch { saveCurrentState(updateLastPlayedAt = false) }
    }
}
