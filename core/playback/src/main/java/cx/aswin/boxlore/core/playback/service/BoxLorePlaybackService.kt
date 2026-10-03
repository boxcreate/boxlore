package cx.aswin.boxlore.core.playback.service

import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import cx.aswin.boxlore.core.catalog.content.CuratedMoods
import cx.aswin.boxlore.core.playback.PlaybackActivationRequest
import cx.aswin.boxlore.core.playback.PlaybackIntroOutroController
import cx.aswin.boxlore.core.playback.PlaybackPowerPolicy
import cx.aswin.boxlore.core.playback.PlaybackProgressCoordinator
import cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.core.playback.PlaybackTaskRemovalPolicy
import cx.aswin.boxlore.core.playback.PlaybackTelemetrySession
import cx.aswin.boxlore.core.playback.PlaybackUiVisibility
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryCallback
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryHost
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

open class BoxLorePlaybackService :
    MediaLibraryService(),
    AutoBrowseLibraryHost {
    override fun asContext(): android.content.Context = this

    override fun requestAutoCollageRefresh(force: Boolean) {
        serviceScope.launch { autoCollagePrewarmer.prewarm(force = force) }
    }

    override var mediaSession: MediaLibrarySession? = null
        protected set
    private var localExoPlayer: ExoPlayer? = null
    internal var playbackPlayer: Player? = null
    private var pausedIdleTeardownJob: Job? = null
    private val manualCompletionPersistenceJobs = mutableSetOf<Job>()
    override lateinit var seekBackAction: androidx.media3.session.CommandButton
        protected set
    override lateinit var seekForwardAction: androidx.media3.session.CommandButton
        protected set
    internal lateinit var likeAction: androidx.media3.session.CommandButton
    internal lateinit var addToQueueAction: androidx.media3.session.CommandButton
    override lateinit var markCompleteAction: androidx.media3.session.CommandButton
        protected set

    @Volatile
    override var autoCollageUris: Map<String, android.net.Uri> = emptyMap()
        protected set

    @VisibleForTesting internal var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @VisibleForTesting internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    override val serviceScope by lazy { CoroutineScope(mainDispatcher + SupervisorJob()) }

    /**
     * Shared Application graph — do not rebuild PodcastRepository / ranking / RSS here.
     * Installed in [cx.aswin.boxlore.BoxLoreApplication] via SharedAppDependenciesHolder.
     */
    private val sharedDeps by lazy {
        cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
            .require()
    }
    private val downloadDeps by lazy {
        cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
            .require()
    }
    override val userPreferencesRepository by lazy { sharedDeps.userPreferencesRepository }
    override val database by lazy { sharedDeps.database }
    override val podcastRepository by lazy { sharedDeps.podcastRepository }
    private val subscriptionRepository by lazy { sharedDeps.subscriptionRepository }
    private val queueSkipMemory by lazy {
        cx.aswin.boxlore.core.playback.QueueSkipMemory
            .fromContext(this)
    }
    private val rankingFeedbackRepository by lazy { sharedDeps.rankingFeedbackRepository }
    override val adaptiveCandidateScorer by lazy { sharedDeps.adaptiveCandidateScorer }
    override val smartQueueSources by lazy {
        cx.aswin.boxlore.core.playback.DefaultSmartQueueSources(
            context = this,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }
    private val smartQueueEngine by lazy {
        cx.aswin.boxlore.core.playback.DefaultSmartQueueEngine(
            sources = smartQueueSources,
            skipMemory = queueSkipMemory,
            adaptiveScorer = adaptiveCandidateScorer,
            staleRestartEnabled = { cachedRestartForgottenEpisodes },
            sameShowQueueOnly = { cachedSameShowQueueOnly },
        )
    }
    override val queueRepository by lazy {
        cx.aswin.boxlore.core.playback
            .QueueRepository(database, podcastRepository, sharedDeps.deviceIdentityPort)
    }
    override var isRefilling = false

    @Volatile internal var queueActivationGeneration = 0L
    private val contextQueueContinuation: ContextQueueContinuationCoordinator by lazy {
        ContextQueueContinuationCoordinator(
            scope = serviceScope,
            generation = { queueActivationGeneration + PlaybackActivationRequest.generation },
            awaitCompletion = { introOutroController.awaitPendingCompletionPersistence() },
            refill = { player, isCurrent -> refillQueueForSession(player, true, isCurrent) },
            stop = { player ->
                player.stop()
                introOutroController.reset(null, 0L)
            },
        )
    }
    private val queueMaxSize = 50
    internal val smartQueueRefillCoordinator by lazy {
        SmartQueueRefillCoordinator(
            database = database,
            podcastRepository = podcastRepository,
            queueRepository = queueRepository,
            smartQueueEngine = smartQueueEngine,
            userPreferencesRepository = userPreferencesRepository,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            queueMaxSize = queueMaxSize,
            mediaIdPrefixStripper = cx.aswin.boxlore.core.playback.SmartQueueRefillPolicy::stripQueuePrefixes,
        )
    }

    @Volatile private var cachedSkipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS

    @Volatile private var cachedSkipEndingMs = PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS

    @Volatile private var cachedSeekBackwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS

    @Volatile private var cachedSeekForwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS

    @Volatile private var cachedRestartForgottenEpisodes = true

    @Volatile private var cachedSameShowQueueOnly = false

    // Breaks circular lazy init between telemetry ↔ intro/outro controllers.
    private var introOutroControllerRef: PlaybackIntroOutroController? = null

    internal val telemetrySession by lazy {
        PlaybackTelemetrySession(
            scope = serviceScope,
            mainDispatcher = mainDispatcher,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
            queueSkipMemory = queueSkipMemory,
            userPreferencesRepository = userPreferencesRepository,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            effectiveSkipEndingMs = { durationMs ->
                introOutroControllerRef!!.effectiveEndingTrimForCompletion(durationMs)
            },
            markCompletionTelemetryDispatched = {
                introOutroControllerRef!!.markCompletionTelemetryDispatched()
            },
            playerProvider = { mediaSession?.player ?: playbackPlayer },
            removeCompletedDownload = { episodeId ->
                downloadDeps.downloadRepository.removeDownload(episodeId)
            },
        )
    }

    private val introOutroController: PlaybackIntroOutroController by lazy {
        PlaybackIntroOutroController(
            scope = serviceScope,
            database = database,
            globalSkipBeginningMs = { cachedSkipBeginningMs },
            globalSkipEndingMs = { cachedSkipEndingMs },
            staleRestartEnabled = { cachedRestartForgottenEpisodes },
            lifecycleEpisodeId = ::lifecycleEpisodeId,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            onActiveDurationResolved = { episodeId, durationMs ->
                if (episodeId == telemetrySession.episodeId) {
                    telemetrySession.totalDurationMs = durationMs
                }
            },
            onNaturalCompletion = this::persistNaturalCompletionFromLifecycle,
            onContextQueueExhausted = { player -> contextQueueContinuation.onExhausted(player) },
            onClearEndOfEpisodeSleep = this::clearEndOfEpisodeSleep,
        ).also { introOutroControllerRef = it }
    }

    private val progressCoordinator by lazy {
        PlaybackProgressCoordinator(
            mainDispatcher = mainDispatcher,
            database = database,
            mediaSessionProvider = { mediaSession },
            isEffectiveEndLatched = { introOutroController.isEffectiveEndLatched },
            effectiveSkipEndingMs = { durationMs ->
                introOutroController.effectiveEndingTrimForCompletion(durationMs)
            },
            updateConsumedAudio = { player -> telemetrySession.updateConsumedAudio(player) },
            dispatchHeartbeatTelemetry = { player -> telemetrySession.dispatchHeartbeatTelemetry(player) },
            missingHistorySeedProvider = this::buildMissingProgressHistory,
        )
    }

    internal var customPlayerFactory: PlaybackServicePlayerFactory? = null

    internal fun getPlayerFactory(): PlaybackServicePlayerFactory =
        customPlayerFactory ?: PlaybackServicePlayerFactory(this, serviceScope)

    private val autoCollagePrewarmer by lazy {
        AutoCollagePrewarmer(
            context = this,
            database = database,
            queueRepository = queueRepository,
            smartQueueSources = smartQueueSources,
            adaptiveCandidateScorer = adaptiveCandidateScorer,
            toAutoPodcast = ::toAutoPodcast,
            mediaSessionProvider = { mediaSession },
            onCollagesReady = { fresh ->
                autoCollageUris = autoCollageUris + fresh
            },
        )
    }

    private var sleepRestoreInProgress = false

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val localPlayer = getPlayerFactory().createExoPlayer()
        val player = getPlayerFactory().createCastPlayer(localPlayer)
        localExoPlayer = localPlayer
        playbackPlayer = player
        serviceScope.launch {
            PlaybackUiVisibility.isForeground.collectLatest {
                reconcilePausedIdleTeardown(player)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.playbackSpeedStream.collectLatest { savedSpeed ->
                player.setPlaybackSpeed(savedSpeed.coerceIn(0.5f, 3.0f))
            }
        }
        serviceScope.launch {
            userPreferencesRepository.skipBeginningMsStream.collectLatest { value ->
                cachedSkipBeginningMs = PlaybackSkipPolicy.sanitizeTrim(value)
                introOutroController.onSkipPreferencesChanged(player)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.skipEndingMsStream.collectLatest { value ->
                cachedSkipEndingMs = PlaybackSkipPolicy.sanitizeTrim(value)
                introOutroController.onSkipPreferencesChanged(player)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.seekBackwardMsStream.collectLatest { value ->
                cachedSeekBackwardMs = PlaybackSkipPolicy.sanitizeSeekBackward(value)
                updateSeekCommandButtons()
            }
        }
        serviceScope.launch {
            userPreferencesRepository.seekForwardMsStream.collectLatest { value ->
                cachedSeekForwardMs = PlaybackSkipPolicy.sanitizeSeekForward(value)
                updateSeekCommandButtons()
            }
        }
        serviceScope.launch {
            userPreferencesRepository.restartForgottenEpisodesStream.collectLatest { enabled ->
                cachedRestartForgottenEpisodes = enabled
            }
        }
        serviceScope.launch {
            userPreferencesRepository.sameShowQueueOnlyStream.collectLatest { enabled ->
                val wasRestricted = cachedSameShowQueueOnly
                cachedSameShowQueueOnly = enabled
                if (wasRestricted && !enabled) {
                    refillQueueAfterSmartQueueEnabled()
                }
            }
        }

        localPlayer.addAnalyticsListener(
            object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onPlayerError(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    error: androidx.media3.common.PlaybackException,
                ) {
                    android.util.Log.e("BoxCastPlayer", "onPlayerError: ${error.errorCodeName}", error)
                    telemetrySession.trackPlayerError(error)
                }

                override fun onAudioSinkError(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    error: Exception,
                ) {
                    android.util.Log.e("BoxCastPlayer", "onAudioSinkError", error)
                }

                override fun onAudioUnderrun(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    bufferSize: Int,
                    bufferSizeMs: Long,
                    elapsedSinceLastFeedMs: Long,
                ) {
                    android.util.Log.e("BoxCastPlayer", "onAudioUnderrun: buffer=$bufferSize, elapsed=$elapsedSinceLastFeedMs")
                }

                override fun onIsPlayingChanged(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    isPlaying: Boolean,
                ) {
                    android.util.Log.d("BoxCastPlayer", "onIsPlayingChanged: $isPlaying")
                }

                override fun onPlaybackStateChanged(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    state: Int,
                ) {
                    if (state == Player.STATE_BUFFERING) {
                        telemetrySession.onBufferingStarted()
                    } else if (state == Player.STATE_READY) {
                        telemetrySession.onBufferingEnded()
                    }
                }

                override fun onPositionDiscontinuity(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    android.util.Log.d(
                        "BoxCastPlayer",
                        "onPositionDiscontinuity: reason=$reason, from ${oldPosition.positionMs} to ${newPosition.positionMs}",
                    )
                    telemetrySession.noteSeekPosition(newPosition.positionMs)
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        telemetrySession.updateHeartbeatsForPosition(
                            newPosition.positionMs,
                            telemetrySession.totalDurationMs,
                        )
                        val source =
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                .consumeSeekSource()
                        val seekResult =
                            introOutroController.onSeekDiscontinuity(
                                newPositionMs = newPosition.positionMs,
                                durationMs = player.duration,
                                source = source,
                            )
                        android.util.Log.d(
                            "BoxCastPlayer",
                            "onPositionDiscontinuity (SEEK): source=$source, reason=$reason, from ${oldPosition.positionMs} to ${newPosition.positionMs}",
                        )
                        val epId = telemetrySession.episodeId
                        if (!seekResult.isLifecycleSeek && epId != null) {
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPlaybackSeeked(
                                podcastId = telemetrySession.podcastId,
                                podcastName = telemetrySession.podcastName,
                                episodeId = epId,
                                episodeTitle = telemetrySession.episodeTitle,
                                fromPositionSeconds = oldPosition.positionMs / 1000f,
                                toPositionSeconds = newPosition.positionMs / 1000f,
                                totalDurationSeconds = telemetrySession.totalDurationMs / 1000f,
                                seekSource = source,
                                entryPoint = telemetrySession.entryPoint,
                            )
                        }
                    }
                }
            },
        )

        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (player.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                        android.util.Log.e("BoxCastPlayer", "Remote playback error: ${error.errorCodeName}", error)
                        telemetrySession.trackPlayerError(error)
                    }
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,) {
                    if (player.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                        handleRemotePositionDiscontinuity(player, oldPosition, newPosition, reason)
                    }
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        val snapshot =
                            progressCoordinator.captureProgressSnapshot(
                                player = player,
                                allowZeroPosition = oldPosition.mediaItemIndex == newPosition.mediaItemIndex,
                            )
                        if (snapshot != null) {
                            serviceScope.launch {
                                progressCoordinator.saveProgressSnapshot(snapshot)
                            }
                        }
                    }
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int,) {
                    handleMediaItemTransition(player, mediaItem, reason)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int,) {
                    if (!playWhenReady) {
                        val pauseReason =
                            when (reason) {
                                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "headphone_disconnected"
                                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio_focus_loss_permanent"
                                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "user_voluntary"
                                else -> "user_voluntary"
                            }
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper
                            .setPauseReason(pauseReason)
                        android.util.Log.d(
                            "BoxCastPlayer",
                            "onPlayWhenReadyChanged: playWhenReady=false, reason=$reason, pauseReason=$pauseReason",
                        )
                    }
                    reconcilePausedIdleTeardown(player)
                }

                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                    reconcilePausedIdleTeardown(player)
                }

                override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                    if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper
                            .setPauseReason("audio_focus_loss_transient")
                        android.util.Log.d("BoxCastPlayer", "onPlaybackSuppressionReasonChanged: transient audio focus loss")
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            telemetrySession.onBufferingStarted()
                        } else if (playbackState == Player.STATE_READY) {
                            telemetrySession.onBufferingEnded()
                        }
                    }
                    when (playbackState) {
                        Player.STATE_READY -> {
                            introOutroController.onReadyOrPlaying(player)
                        }
                        Player.STATE_ENDED -> introOutroController.onNaturalStateEnded(player)
                        Player.STATE_IDLE ->
                            if (isRemoteContextNaturalEnd(player)) {
                                introOutroController.onNaturalStateEnded(player)
                            } else {
                                queueActivationGeneration++
                                contextQueueContinuation.invalidate()
                                if (!player.playWhenReady) introOutroController.reset(null, 0L)
                            }
                    }
                    reconcilePausedIdleTeardown(player)
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int,) {
                    if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                    val currentItem = player.currentMediaItem
                    if (currentItem == null) {
                        queueActivationGeneration++
                        contextQueueContinuation.invalidate()
                        introOutroController.reset(null, 0L)
                    } else if (!introOutroController.isActiveMediaItem(currentItem)) {
                        queueActivationGeneration++
                        contextQueueContinuation.invalidate()
                        introOutroController.onMediaActivated(
                            player,
                            currentItem,
                            preservePosition = introOutroController.activeEpisodeId == lifecycleEpisodeId(currentItem)
                        )
                    }
                }
            },
        )

        var progressSaverJob: kotlinx.coroutines.Job? = null
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val currentItem = player.currentMediaItem
                    val episodeId = currentItem?.mediaId?.stripEpisodePrefix()

                    if (isPlaying) {
                        if (episodeId != null) telemetrySession.start(episodeId, currentItem)
                        progressCoordinator.activePlaybackStartTimeMs = System.currentTimeMillis()

                        introOutroController.onReadyOrPlaying(player)
                        introOutroController.startOutroMonitor(player)
                        progressSaverJob?.cancel()
                        progressSaverJob =
                            serviceScope.launch {
                                progressCoordinator.startPlaybackTicker(player)
                            }
                        refreshLastPlayedAtAfterActivation(player, episodeId)
                    } else {
                        introOutroController.stopOutroMonitor()
                        val wasActive = progressCoordinator.activePlaybackStartTimeMs > 0
                        val progressSnapshot =
                            progressCoordinator.captureProgressSnapshot(
                                player = player,
                                activePlaybackEnded = wasActive,
                            )
                        val shouldEndSession =
                            !player.playWhenReady ||
                                player.playbackState == Player.STATE_ENDED ||
                                player.playbackState == Player.STATE_IDLE ||
                                player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE

                        if (shouldEndSession) {
                            telemetrySession.lastPausedEpisodeId = episodeId
                            telemetrySession.end(forceCompleted = false)
                        }

                        progressSaverJob?.cancel()
                        progressSaverJob = null
                        progressCoordinator.activePlaybackStartTimeMs = 0L
                        serviceScope.launch {
                            if (progressSnapshot != null) {
                                progressCoordinator.saveProgressSnapshot(progressSnapshot)
                            }
                        }
                    }
                    reconcilePausedIdleTeardown(player)
                }
            },
        )

        initMediaSession(player)
    }

    private fun refreshLastPlayedAtAfterActivation(player: Player, episodeId: String?) {
        if (episodeId == null) return
        serviceScope.launch {
            // Resolve resume policy against the previous listen time before refreshing it.
            if (!introOutroController.awaitActivationConfiguration(episodeId) ||
                lifecycleEpisodeId(player.currentMediaItem) != episodeId ||
                !player.isPlaying
            ) {
                return@launch
            }
            database.listeningHistoryDao().updateLastPlayedAt(episodeId, System.currentTimeMillis())
        }
    }

    internal fun initMediaSession(player: Player) {
        val buttons = getPlayerFactory().buildSeekButtons(cachedSeekBackwardMs, cachedSeekForwardMs)
        try {
            val config =
                PlaybackServicePlayerFactory.SessionConfig(
                    seekForwardMs = { cachedSeekForwardMs },
                    seekBackMs = { cachedSeekBackwardMs },
                    onSeekByConfiguredIncrement = ::seekByConfiguredIncrement,
                    onSkipNext = ::handleSkipNext,
                    callback = AutoBrowseLibraryCallback(this),
                    seekButtons = buttons,
                )
            val built =
                getPlayerFactory().assembleSession(
                    service = this,
                    player = player,
                    config = config,
                )
            seekBackAction = built.seekButtons.seekBack
            seekForwardAction = built.seekButtons.seekForward
            likeAction = built.customActions.like
            addToQueueAction = built.customActions.addToQueue
            markCompleteAction = built.customActions.markComplete
            mediaSession = built.mediaSession
            serviceScope.launch { autoCollagePrewarmer.prewarm() }
        } catch (e: SecurityException) {
            android.util.Log.e(
                "BoxLorePlaybackService",
                "Failed to assemble MediaLibrarySession due to system PendingIntent UID limit",
                e,
            )
            val customActions = getPlayerFactory().buildCustomActions()
            seekBackAction = buttons.seekBack
            seekForwardAction = buttons.seekForward
            likeAction = customActions.like
            addToQueueAction = customActions.addToQueue
            markCompleteAction = customActions.markComplete
            mediaSession = null
        }
    }

    private fun rebuildSeekCommandButtons() {
        val buttons = getPlayerFactory().buildSeekButtons(cachedSeekBackwardMs, cachedSeekForwardMs)
        seekBackAction = buttons.seekBack
        seekForwardAction = buttons.seekForward
    }

    private fun updateSeekCommandButtons() {
        rebuildSeekCommandButtons()
        if (::markCompleteAction.isInitialized) {
            mediaSession?.setCustomLayout(
                listOf(seekBackAction, seekForwardAction, markCompleteAction),
            )
        }
    }

    private fun seekByConfiguredIncrement(player: Player, deltaMs: Long, source: String,) {
        val upperBound = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, upperBound)
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .setSeekSource(source)
        player.seekTo(target)
        android.util.Log.d("BoxCastPlayer", "$source to ${target}ms")
    }

    private fun handleRemotePositionDiscontinuity(
        player: Player,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        telemetrySession.noteSeekPosition(newPosition.positionMs)
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        telemetrySession.updateHeartbeatsForPosition(
            newPosition.positionMs,
            telemetrySession.totalDurationMs,
        )
        val source =
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .consumeSeekSource()
        val seekResult =
            introOutroController.onSeekDiscontinuity(
                newPositionMs = newPosition.positionMs,
                durationMs = player.duration,
                source = source,
            )
        val episodeId = telemetrySession.episodeId
        if (!seekResult.isLifecycleSeek && episodeId != null) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPlaybackSeeked(
                podcastId = telemetrySession.podcastId,
                podcastName = telemetrySession.podcastName,
                episodeId = episodeId,
                episodeTitle = telemetrySession.episodeTitle,
                fromPositionSeconds = oldPosition.positionMs / 1000f,
                toPositionSeconds = newPosition.positionMs / 1000f,
                totalDurationSeconds = telemetrySession.totalDurationMs / 1000f,
                seekSource = source,
                entryPoint = telemetrySession.entryPoint,
            )
        }
    }

    private fun lifecycleEpisodeId(item: MediaItem?): String? = item?.mediaId?.stripEpisodePrefix()

    private fun handleMediaItemTransition(player: Player, mediaItem: MediaItem?, reason: Int,) {
        android.util.Log.d(
            "BoxCastPlayer",
            "onMediaItemTransition: mediaId=${mediaItem?.mediaId}, title=${mediaItem?.mediaMetadata?.title}, artworkUri=${mediaItem?.mediaMetadata?.artworkUri}, reason=$reason",
        )
        val previousEpisodeId = introOutroController.activeEpisodeId
        val previousDurationMs = introOutroController.activeDurationMs
        val wasAutoCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        val wasServiceOwnedNaturalAdvance =
            previousEpisodeId ==
                cx.aswin.boxlore.core.playback.PlaybackLifecycleSignals
                    .serviceOwnedNaturalAdvanceEpisodeId

        completePreviousItemTransition(
            previousEpisodeId = previousEpisodeId,
            previousDurationMs = previousDurationMs,
            wasAutoCompleted = wasAutoCompleted,
        )
        if (restoreLifecycleAfterSleepTransition(player, mediaItem)) return
        if (
            wasAutoCompleted &&
            cx.aswin.boxlore.core.playback.SleepTimerHolder.sleepAtEndOfEpisode
        ) {
            enforceEndOfEpisodeSleepAfterTransition(player, previousDurationMs)
            return
        }

        queueActivationGeneration++
        contextQueueContinuation.invalidate()
        introOutroController.onMediaActivated(
            player,
            mediaItem,
            preservePosition = previousEpisodeId == lifecycleEpisodeId(mediaItem) && !wasAutoCompleted,
        )
        updateTransitionPlaybackSession(
            player = player,
            mediaItem = mediaItem,
            reason = reason,
            wasServiceOwnedNaturalAdvance = wasServiceOwnedNaturalAdvance,
        )
        maybeRefillQueueAfterTransition(player, reason)
    }

    private fun completePreviousItemTransition(previousEpisodeId: String?, previousDurationMs: Long, wasAutoCompleted: Boolean,) {
        if (wasAutoCompleted && previousEpisodeId != null) {
            introOutroController.claimNaturalCompletion(previousEpisodeId, previousDurationMs)
        } else {
            telemetrySession.end(forceCompleted = false, isTransition = true)
        }
    }

    private fun restoreLifecycleAfterSleepTransition(player: Player, mediaItem: MediaItem?,): Boolean {
        if (!sleepRestoreInProgress) return false
        sleepRestoreInProgress = false
        introOutroController.reset(mediaItem, player.currentPosition)
        return true
    }

    private fun updateTransitionPlaybackSession(
        player: Player,
        mediaItem: MediaItem?,
        reason: Int,
        wasServiceOwnedNaturalAdvance: Boolean,
    ) {
        if (!player.isPlaying) {
            progressCoordinator.activePlaybackStartTimeMs = 0L
            return
        }
        val episodeId = lifecycleEpisodeId(mediaItem)
        val transitionSource =
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "queue_auto_advance"
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
                    if (wasServiceOwnedNaturalAdvance) {
                        "queue_auto_advance"
                    } else {
                        "queue_skip"
                    }
                else -> null
            }
        if (episodeId != null) telemetrySession.start(episodeId, mediaItem, transitionSource)
        progressCoordinator.activePlaybackStartTimeMs = System.currentTimeMillis()
    }

    private fun enforceEndOfEpisodeSleepAfterTransition(player: Player, completedDurationMs: Long,) {
        clearEndOfEpisodeSleep()
        player.pause()
        val previousIndex = player.currentMediaItemIndex - 1
        if (previousIndex >= 0) {
            sleepRestoreInProgress = true
            introOutroController.markAutomaticSeekSource("transition")
            player.seekTo(
                previousIndex,
                introOutroController.trueEndSeekTarget(completedDurationMs),
            )
        }
    }

    override fun observeManualCompletion(episodeId: String) {
        introOutroController.observeManualCompletion(episodeId)
    }

    override fun toAutoPodcast(entity: cx.aswin.boxlore.core.database.PodcastEntity) = with(entity) {
        cx.aswin.boxlore.core.model.Podcast(
            id = podcastId,
            title = title,
            artist = author,
            imageUrl = imageUrl,
            type = type,
            description = description,
            genre = genre ?: "Podcast",
            fallbackImageUrl = imageUrl,
            latestEpisode = latestEpisode,
            subscribedAt = subscribedAt,
            preferredSort = preferredSort,
            notificationsEnabled = notificationsEnabled,
            autoDownloadEnabled = autoDownloadEnabled,
            sourceType = sourceType,
            feedUrl = feedUrl,
            rssRefreshCapability = rssRefreshCapability,
            rssCatalogStale = rssCatalogStale,
            rssHasNewEpisodes = rssHasNewEpisodes,
        )
    }

    override fun getTimeBasedGenres(hour: Int): List<Pair<String, String>> = CuratedMoods.forDaypart(CuratedMoods.daypartForHour(hour)).map { it.id to it.title }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private fun reconcilePausedIdleTeardown(player: Player) {
        val shouldSchedule = isPausedLocalTeardownEligible(player)
        if (!shouldSchedule) {
            pausedIdleTeardownJob?.cancel()
            pausedIdleTeardownJob = null
            return
        }
        if (pausedIdleTeardownJob?.isActive == true) return

        pausedIdleTeardownJob =
            serviceScope.launch {
                delay(PlaybackPowerPolicy.PAUSED_IDLE_TIMEOUT_MS)
                PlaybackPowerPolicy.persistThenTearDownIfStillIdle(
                    persistProgress = {
                        awaitTerminalPersistence()
                        progressCoordinator.saveProgressOnce(player)
                    },
                    isStillIdle = { isPausedLocalTeardownEligible(player) },
                    tearDown = {
                        telemetrySession.end(forceCompleted = false)
                        introOutroController.reset(null, 0L)
                        player.pause()
                        stopSelf()
                    },
                )
                pausedIdleTeardownJob = null
            }
    }

    private fun isPausedLocalTeardownEligible(player: Player): Boolean = PlaybackPowerPolicy.shouldSchedulePausedLocalTeardown(
        isUiForeground = PlaybackUiVisibility.isForeground.value,
        isRemote = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE,
        isPlaying = player.isPlaying,
        playWhenReady = player.playWhenReady,
    )

    internal fun releasePlayers() {
        if (mediaSession != null) {
            mediaSession?.run {
                player.release()
                release()
                mediaSession = null
            }
        } else {
            playbackPlayer?.release()
        }
        playbackPlayer = null
        localExoPlayer = null
    }

    override fun onDestroy() {
        pausedIdleTeardownJob?.cancel()
        pausedIdleTeardownJob = null
        telemetrySession.end(forceCompleted = false)
        introOutroController.reset(null, 0L)
        clearEndOfEpisodeSleep()
        PlaybackActivationRequest.clear()
        contextQueueContinuation.invalidate()
        releasePlayers()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: playbackPlayer
        val plan =
            PlaybackTaskRemovalPolicy.plan(
                hasPlayer = player != null,
                playWhenReady = player?.playWhenReady == true,
                mediaItemCount = player?.mediaItemCount ?: 0,
                playbackState = player?.playbackState ?: Player.STATE_IDLE,
            )
        if (plan.keepServiceRunning) {
            android.util.Log.d(
                "BoxLorePlaybackService",
                "onTaskRemoved: player is playing, keeping service in foreground and bypassing super.onTaskRemoved to prevent notification from disappearing",
            )
            return
        }

        android.util.Log.d(
            "BoxLorePlaybackService",
            "onTaskRemoved: inactive player, persisting before stopping service",
        )
        serviceScope.launch {
            awaitTerminalPersistence()
            if (plan.persistBeforeStop && player != null) {
                progressCoordinator.saveProgressOnce(player, activePlaybackEnded = true)
            }
            val latestPlayer = mediaSession?.player ?: playbackPlayer
            val latestPlan =
                PlaybackTaskRemovalPolicy.plan(
                    hasPlayer = latestPlayer != null,
                    playWhenReady = latestPlayer?.playWhenReady == true,
                    mediaItemCount = latestPlayer?.mediaItemCount ?: 0,
                    playbackState = latestPlayer?.playbackState ?: Player.STATE_IDLE,
                )
            if (!latestPlan.keepServiceRunning) {
                telemetrySession.end(forceCompleted = false)
                introOutroController.reset(null, 0L)
                finishTaskRemoval(rootIntent)
            }
        }
    }

    private fun finishTaskRemoval(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun awaitTerminalPersistence() {
        introOutroController.awaitPendingCompletionPersistence()
        manualCompletionPersistenceJobs.toList().forEach { it.join() }
    }

    /**
     * SmartQueue refill: the single auto-refill path in the app (the UI-side triggers
     * were removed). Uses the tiered SmartQueueEngine to build a batch of episodes
     * (same podcast → resume → scored subscriptions → server recs → region trending).
     * Works independently of the app UI being open.
     */
    override suspend fun refillQueue(player: Player) {
        refillQueueForSession(player)
    }

    private suspend fun findPodcastIdForEpisode(episodeId: String): String? {
        val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
        historyItem?.podcastId?.takeIf { it.isNotBlank() }?.let { return it }

        val queueItem = database.queueDao().getQueueItemByEpisodeId(episodeId)
        queueItem?.podcastId?.takeIf { it.isNotBlank() }?.let { return it }

        val episode = podcastRepository.getEpisode(episodeId)
        return episode?.podcastId
    }

    private fun markCurrentEpisodeCompleted() {
        val player = playbackPlayer ?: return
        val currentItem = player.currentMediaItem
        val durationMs = player.duration
        val episodeId = currentItem?.mediaId?.stripEpisodePrefix() ?: return
        val progressSnapshot =
            progressCoordinator.captureProgressSnapshot(
                player = player,
                allowZeroPosition = true,
            )
        observeManualCompletion(episodeId)
        val persistenceJob =
            serviceScope.launch {
                persistManualCompletion(
                    episodeId = episodeId,
                    playerDurationMs = durationMs,
                    progressSnapshot = progressSnapshot,
                )
            }
        manualCompletionPersistenceJobs += persistenceJob
        persistenceJob.invokeOnCompletion {
            manualCompletionPersistenceJobs -= persistenceJob
        }
    }

    private fun handleSkipNext() {
        val player = playbackPlayer ?: return
        serviceScope.launch {
            val skipBehavior =
                try {
                    userPreferencesRepository.skipBehaviorStream.first()
                } catch (e: Exception) {
                    "just_skip"
                }

            if (skipBehavior == "mark_completed_skip") {
                markCurrentEpisodeCompleted()
            }

            kotlinx.coroutines.withContext(mainDispatcher) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    PlaybackActivationRequest.clear()
                    player.stop()
                    introOutroController.reset(null, 0L)
                }
            }
        }
    }

    override fun markCurrentEpisodeCompletedAndSkip(session: MediaSession) {
        markCurrentEpisodeCompleted()
        serviceScope.launch {
            val player = playbackPlayer ?: return@launch
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                PlaybackActivationRequest.clear()
                player.stop()
                introOutroController.reset(null, 0L)
            }
        }
    }
}
