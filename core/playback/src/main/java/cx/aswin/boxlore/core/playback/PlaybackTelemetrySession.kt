package cx.aswin.boxlore.core.playback

import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.ListenStart
import cx.aswin.boxlore.core.analytics.PendingEntryPoint
import cx.aswin.boxlore.core.analytics.PlaybackListenValues
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.playback.QueueMath
import cx.aswin.boxlore.core.playback.QueueSkipMemory
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Playback analytics session: start / enrich / end, heartbeats, and consumed-audio accounting.
 * Extracted from [cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService].
 *
 * One listen runs from sound starting until it pauses, finishes, switches episode, stops or the
 * service closes; rebuffering inside it doesn't end it. [listens] ([ListenRecorder]) counts it and
 * sends its one `playback_session_ended`, and keeps a note of it so the next launch can report one
 * Android cut off. [ListeningRecordWriter] saves the play to the local listening record.
 */
internal class PlaybackTelemetrySession(
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
    private val queueSkipMemory: QueueSkipMemory,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
    private val effectiveSkipEndingMs: (Long) -> Long,
    private val markCompletionTelemetryDispatched: () -> Boolean,
    private val playerProvider: () -> Player?,
    private val removeCompletedDownload: (String) -> Unit,
) {
    /** Where the play is heard ([cx.aswin.boxlore.core.database.ListeningSessionValues] `OUTPUT_*`), read on the main thread; set by the service. */
    var listeningOutput: () -> String? = { null }

    /** This installation's listening device ID (`BoxcastPrefs.getOrCreateListeningDeviceId`); set by the service. */
    var listeningDeviceId: () -> String? = { null }

    /** The listen in progress and its one record; the service sets its route, note and visibility. */
    val listens = ListenRecorder(playerProvider)

    private val records = ListeningRecordWriter(scope, database)

    private val firedHeartbeats = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    var startTimeMs: Long = 0L
        private set
    var episodeId: String? = null
        private set
    var episodeTitle: String? = null
        private set
    var podcastId: String? = null
        private set
    var podcastName: String? = null
        private set
    var podcastGenre: String? = null
        private set
    var totalDurationMs: Long = 0L
    var isRepeating: Boolean = false
        private set
    var entryPoint: String? = null
        private set
    var entryPointContext: Map<String, Any>? = null
        private set
    var contextType: String? = null
        private set
    var contextSourceId: String? = null
        private set

    /** Start of the buffering spell in progress, listen or not, for stall events. */
    private var bufferingStartTimeMs: Long = 0L

    /** Episode paused so a bare remote play() can be attributed as resume. */
    var lastPausedEpisodeId: String? = null

    fun onBufferingStarted() {
        bufferingStartTimeMs = listens.clock()
        listens.current?.bufferingStarted(bufferingStartTimeMs)
    }

    /** Ends a buffering spell; one of [PlaybackListenValues.STALL_MS] or more is sent as a stall. */
    fun onBufferingEnded() {
        val now = listens.clock()
        listens.current?.bufferingEnded(now)
        if (bufferingStartTimeMs > 0) {
            val bufferMs = now - bufferingStartTimeMs
            bufferingStartTimeMs = 0L
            if (bufferMs >= PlaybackListenValues.STALL_MS) {
                AnalyticsHelper.trackPlaybackBuffering(
                    episodeId = episodeId,
                    podcastId = podcastId,
                    entryPoint = entryPoint,
                    bufferDurationMs = bufferMs,
                )
            }
        }
    }

    /** The player jumped from [fromMs] to [toMs]; see [ListenRecorder.jump]. */
    fun onPositionJump(fromMs: Long, toMs: Long, userSeek: Boolean = false,) = listens.jump(fromMs, toMs, userSeek)

    fun start(
        episodeId: String,
        currentItem: MediaItem?,
        fallbackEntryPoint: String? = null,
    ) {
        if (startTimeMs > 0 && this.episodeId == episodeId) return

        end(forceCompleted = false, reason = PlaybackListenValues.END_SWITCHED)

        if (this.episodeId != episodeId) {
            firedHeartbeats.clear()
        }

        startTimeMs = System.currentTimeMillis()
        bufferingStartTimeMs = 0L
        this.episodeId = episodeId
        val listen = listens.begin()

        val title = currentItem?.mediaMetadata?.title?.toString()
        val artist =
            currentItem?.mediaMetadata?.artist?.toString()
                ?: currentItem?.mediaMetadata?.subtitle?.toString()
        val genre = currentItem?.mediaMetadata?.genre?.toString()
        episodeTitle = title
        podcastName = artist
        podcastGenre = genre

        val extras = currentItem?.mediaMetadata?.extras
        val pendingEntryPoint = PendingEntryPoint.consume()
        if (pendingEntryPoint != null) {
            entryPoint = pendingEntryPoint["entry_point"] as? String
            val contextMap = pendingEntryPoint.filterKeys { it != "entry_point" }
            entryPointContext = contextMap.ifEmpty { null }
        } else {
            entryPoint = extras?.getString("entry_point")
            entryPointContext = analyticsEntryPointContext(extras)
        }

        if (entryPoint == null) {
            entryPoint =
                if (episodeId == lastPausedEpisodeId) {
                    "resume_notification"
                } else {
                    fallbackEntryPoint
                }
        }
        lastPausedEpisodeId = null
        listenContext()?.let(listens::started)

        val listenStart = ListenStart(listenId = listen.listenId, controlledBy = listens.controlledBy)
        scope.launch {
            enrich(episodeId, currentItem, genre, listenStart)
        }
    }

    /** What the listen in progress plays, or null between listens. */
    private fun listenContext(): ListenContext? {
        val currentEpisodeId = episodeId ?: return null
        return ListenContext(
            episodeId = currentEpisodeId,
            podcastId = podcastId,
            podcastName = podcastName,
            podcastGenre = podcastGenre,
            episodeTitle = episodeTitle,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
            durationMs = totalDurationMs,
            previouslyFinished = isRepeating,
        )
    }

    private suspend fun resolvePodcastFromDb(podcastId: String): Pair<String?, String?> =
        try {
            val podcast = database.podcastDao().getPodcast(podcastId)
            if (podcast != null) {
                val genre =
                    if (!podcast.genre.isNullOrBlank() && podcast.genre != "Podcast") {
                        podcast.genre
                    } else {
                        null
                    }
                Pair(podcast.title, genre)
            } else {
                Pair(null, null)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }

    private suspend fun resolvePodcastFromHistory(episodeId: String): String? =
        try {
            val historyItem = database.episodeListenerStore().projectedHistory(episodeId)
            if (historyItem != null && !historyItem.podcastName.isNullOrBlank()) {
                historyItem.podcastName
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    private suspend fun resolvePodcastFromNetwork(podcastId: String): String? =
        try {
            podcastRepository.getPodcastDetails(podcastId)?.title
        } catch (_: Exception) {
            null
        }

    private suspend fun resolvePodcastMetadata(
        podcastId: String,
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?,
    ): Pair<String?, String?> {
        val (dbName, dbGenre) = resolvePodcastFromDb(podcastId)
        var resolvedPodcastName = dbName
        var actualGenre = dbGenre ?: genre

        if (resolvedPodcastName.isNullOrBlank()) {
            resolvedPodcastName = resolvePodcastFromHistory(episodeId)
        }
        if (resolvedPodcastName.isNullOrBlank()) {
            resolvedPodcastName = resolvePodcastFromNetwork(podcastId)
        }

        val finalPodcastName =
            resolvedPodcastName
                ?: currentItem?.mediaMetadata?.subtitle?.toString()
                ?: currentItem?.mediaMetadata?.artist?.toString()

        return Pair(finalPodcastName, actualGenre)
    }

    private suspend fun enrich(
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?,
        listen: ListenStart,
    ) {
        try {
            val queueItem = database.queueDao().getQueueItemByEpisodeId(episodeId)
            if (queueItem != null) {
                contextType = queueItem.contextType
                contextSourceId = queueItem.contextSourceId
            } else {
                contextType = null
                contextSourceId = null
            }
        } catch (_: Exception) {
            contextType = null
            contextSourceId = null
        }

        val resolvedPodcastId = findPodcastIdForEpisode(episodeId)
        podcastId = resolvedPodcastId

        val (resolvedName, resolvedGenre) =
            if (resolvedPodcastId != null) {
                resolvePodcastMetadata(resolvedPodcastId, episodeId, currentItem, genre)
            } else {
                val finalName =
                    currentItem?.mediaMetadata?.subtitle?.toString()
                        ?: currentItem?.mediaMetadata?.artist?.toString()
                Pair(finalName, genre)
            }
        podcastName = resolvedName
        podcastGenre = resolvedGenre

        // The episode's projected completion, so a replay counts whichever of its IDs is playing.
        val history = database.episodeListenerStore().projectedHistory(episodeId)
        isRepeating = history?.isCompleted == true

        var durationMs = currentItem?.mediaMetadata?.extras?.getLong("durationMs", 0L) ?: 0L
        val exoDuration =
            withContext(mainDispatcher) {
                playerProvider()?.duration ?: 0L
            }
        if (exoDuration > 0) durationMs = exoDuration
        totalDurationMs = durationMs

        val startPositionMs =
            withContext(mainDispatcher) {
                playerProvider()?.currentPosition ?: 0L
            }
        withContext(mainDispatcher) {
            updateHeartbeatsForPosition(startPositionMs, durationMs)
        }

        val isSubscribed = resolvedPodcastId?.let { subscriptionRepository.isSubscribed(it) } ?: false
        val mode = playbackModeOf(episodeId)
        val speed =
            withContext(mainDispatcher) {
                if (listens.current?.listenId == listen.listenId) listens.playbackMode = mode
                playerProvider()?.playbackParameters?.speed ?: 1f
            }

        AnalyticsHelper.trackPlaybackStarted(
            podcastId = resolvedPodcastId,
            podcastName = resolvedName,
            podcastGenre = resolvedGenre,
            episodeId = episodeId,
            episodeTitle = currentItem?.mediaMetadata?.title?.toString(),
            startPositionSeconds = startPositionMs / 1000f,
            totalDurationSeconds = durationMs / 1000f,
            isRepeating = isRepeating,
            isSubscribed = isSubscribed,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
            playbackMode = mode,
            speed = speed,
            listen = listen,
        )
    }

    /** Download when any stored ID of the episode holds a finished download, else stream. */
    private suspend fun playbackModeOf(episodeId: String): String = runCatching {
        val ids = database.episodeListenerStore().memberIds(episodeId).ifEmpty { listOf(episodeId) }
        if (database.downloadedEpisodeDao().getCompletedDownloadIds(ids).isNotEmpty()) {
            PlaybackListenValues.MODE_DOWNLOAD
        } else {
            PlaybackListenValues.MODE_STREAM
        }
    }.getOrDefault(PlaybackListenValues.MODE_STREAM)

    /**
     * Ends the listen in progress. The reason is finished when it reached the end (or [forceCompleted]),
     * switched episode for [isTransition], else [reason].
     */
    fun end(
        forceCompleted: Boolean = false,
        isTransition: Boolean = false,
        reason: String = PlaybackListenValues.END_PAUSED,
    ) {
        val currentEpisodeId = episodeId
        if (startTimeMs <= 0 || currentEpisodeId == null) return

        playerProvider()?.let(::updateConsumedAudio)
        val listen = listens.current?.also { it.finish(listens.clock()) }
        val consumedAudioSeconds = (listen?.heardMs ?: 0L) / 1000f
        val isCompleted = forceCompleted || isCompletedByProgress()
        val currentQueueSize = queueSize()
        val pauseReason = if (isCompleted) null else AnalyticsHelper.consumePauseReason()

        if (isCompleted) {
            if (markCompletionTelemetryDispatched()) trackCompletion(currentEpisodeId)
        } else {
            trackPause(currentEpisodeId, pauseReason, currentQueueSize, listen)
            if (isTransition && consumedAudioSeconds <= 30f) recordSmartQueueSkip(currentEpisodeId)
        }
        listenContext()?.let { context ->
            listens.end(context, endReasonOf(isCompleted, isTransition, reason), pauseReason, isCompleted, currentQueueSize)
        }
        recordRankingFeedback(currentEpisodeId, consumedAudioSeconds, isCompleted, isTransition)
        records.save(
            ListeningRecordWriter.Play(
                episodeId = currentEpisodeId,
                podcastId = podcastId,
                podcastName = podcastName,
                podcastGenre = podcastGenre,
                startedAt = startTimeMs,
                consumedMs = listen?.heardMs ?: 0L,
                wallMs = listen?.heardWallMs ?: 0L,
                completed = isCompleted,
                source = ListeningSessionRecordLogic.playSource(contextType, entryPoint),
                output = runCatching(listeningOutput).getOrNull(),
            ),
            deviceId = listeningDeviceId,
        )

        AnalyticsHelper.flush()
        reset()
    }

    private fun endReasonOf(isCompleted: Boolean, isTransition: Boolean, reason: String,): String = when {
        isCompleted -> PlaybackListenValues.END_FINISHED
        isTransition -> PlaybackListenValues.END_SWITCHED
        else -> reason
    }

    private fun queueSize(): Int = try {
        playerProvider()?.mediaItemCount ?: 0
    } catch (_: Exception) {
        0
    }

    private fun isCompletedByProgress(): Boolean = try {
        PlaybackSkipPolicy.shouldCompleteFromProgress(
            positionMs = playerProvider()?.currentPosition ?: 0L,
            durationMs = totalDurationMs,
            effectiveSkipEndingMs = effectiveSkipEndingMs(totalDurationMs),
        )
    } catch (e: Exception) {
        Log.w(
            "BoxLorePlaybackService",
            "Failed to evaluate playback completion; using fallback",
            e,
        )
        false
    }

    /** The 100% heartbeat, `playback_completed`, and removing the download when that setting is on. */
    private fun trackCompletion(currentEpisodeId: String) {
        if (!firedHeartbeats.contains("percent_100")) {
            firedHeartbeats.add("percent_100")
            AnalyticsHelper.trackPlaybackHeartbeat(
                podcastId = podcastId,
                podcastName = podcastName,
                episodeId = currentEpisodeId,
                episodeTitle = episodeTitle,
                currentPositionSeconds = totalDurationMs / 1000f,
                totalDurationSeconds = totalDurationMs / 1000f,
                heartbeatPercentage = 100,
                heartbeatType = "percent",
                entryPoint = entryPoint,
            )
        }

        AnalyticsHelper.trackPlaybackCompleted(
            podcastId = podcastId,
            podcastName = podcastName,
            podcastGenre = podcastGenre,
            episodeId = currentEpisodeId,
            episodeTitle = episodeTitle,
            totalDurationSeconds = totalDurationMs / 1000f,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
        )

        if (currentEpisodeId.isNotEmpty()) {
            scope.launch {
                try {
                    val shouldDelete = userPreferencesRepository.autoDownloadDeleteCompletedStream.first()
                    if (shouldDelete) {
                        removeCompletedDownload(currentEpisodeId)
                        Log.d("BoxLorePlaybackService", "Auto-deleted completed downloaded episode: $currentEpisodeId")
                    }
                } catch (e: Exception) {
                    Log.e("BoxLorePlaybackService", "Failed to auto-delete completed download", e)
                }
            }
        }
    }

    /** `playback_paused`: off by default since `playback_session_ended`, kept for the remote switch. */
    private fun trackPause(currentEpisodeId: String, pauseReason: String?, queueSize: Int, listen: ListenTally?,) {
        AnalyticsHelper.trackPlaybackPaused(
            podcastId = podcastId,
            podcastName = podcastName,
            podcastGenre = podcastGenre,
            episodeId = currentEpisodeId,
            episodeTitle = episodeTitle,
            durationPlayedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000f,
            totalBufferedTimeSeconds = (listen?.bufferingMs ?: 0L) / 1000f,
            totalDurationSeconds = totalDurationMs / 1000f,
            isCompleted = false,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
            queueSize = queueSize,
            pauseReason = pauseReason ?: "user_voluntary",
        )
    }

    /** An auto-filled episode skipped within 30 seconds: Smart Queue counts it and remembers the skip. */
    private fun recordSmartQueueSkip(currentEpisodeId: String) {
        if (contextType != "AUTO_FILL") return
        AnalyticsHelper.trackSmartQueueEpisodeSkipped(
            episodeId = currentEpisodeId,
            recommendationSource = contextSourceId ?: "unknown",
            positionInQueue = 0,
        )
        try {
            queueSkipMemory.recordSkip(
                episodeId = currentEpisodeId,
                podcastId = podcastId,
                source = contextSourceId,
            )
        } catch (e: Exception) {
            Log.e("AutoQueue", "Failed to record skip memory", e)
        }
    }

    /** What the adaptive ranking learns from the play: how much was heard, finished, or skipped early. */
    private fun recordRankingFeedback(currentEpisodeId: String, consumedAudioSeconds: Float, isCompleted: Boolean, isTransition: Boolean,) {
        val adaptiveSource =
            when (contextType) {
                "AUTO_FILL" -> CandidateSource.SERVER_RECOMMENDATION
                QueueMath.CONTEXT_TYPE_LORE -> CandidateSource.CURATED_INTENT
                else -> null
            }
        val isAdaptiveEarlySkip =
            isTransition &&
                consumedAudioSeconds <= 30f &&
                adaptiveSource != null
        val target =
            FeedbackTarget(
                episodeId = currentEpisodeId,
                podcastId = podcastId.orEmpty(),
                genre = podcastGenre,
                source = adaptiveSource,
            )
        val durationSeconds = (totalDurationMs / 1_000L).coerceAtLeast(0L)
        scope.launch {
            rankingFeedbackRepository.recordPlayback(
                target = target,
                listenSeconds = consumedAudioSeconds.toLong().coerceAtLeast(0L),
                durationSeconds = durationSeconds,
                completed = isCompleted,
                earlySkip = isAdaptiveEarlySkip,
            )
        }
    }

    private fun reset() {
        startTimeMs = 0L
        bufferingStartTimeMs = 0L
        episodeId = null
        episodeTitle = null
        podcastId = null
        podcastName = null
        totalDurationMs = 0L
        isRepeating = false
        entryPoint = null
        entryPointContext = null
    }

    /** Counts audio heard since the last sample (each second while playing), and refreshes the note. */
    fun updateConsumedAudio(player: Player) = listens.sample(player, ::listenContext)

    fun dispatchHeartbeatTelemetry(player: Player) {
        val episodeId = episodeId ?: return
        if (!player.isPlaying) return

        val currentPosMs = player.currentPosition
        val durationMs = player.duration
        if (durationMs <= 0) return

        val currentPosSec = currentPosMs / 1000f
        val durationSec = durationMs / 1000f
        val percent = (currentPosMs.toFloat() / durationMs.toFloat()) * 100f

        checkPercentHeartbeats(episodeId, currentPosSec, durationSec, percent)
        checkIntervalHeartbeats(episodeId, currentPosSec, durationSec)
    }

    private fun checkPercentHeartbeats(
        episodeId: String,
        currentPosSec: Float,
        durationSec: Float,
        percent: Float,
    ) {
        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone && !firedHeartbeats.contains("percent_$milestone")) {
                firedHeartbeats.add("percent_$milestone")
                AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = podcastId,
                    podcastName = podcastName,
                    episodeId = episodeId,
                    episodeTitle = episodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = milestone,
                    heartbeatType = "percent",
                    entryPoint = entryPoint,
                )
            }
        }
    }

    private fun checkIntervalHeartbeats(
        episodeId: String,
        currentPosSec: Float,
        durationSec: Float,
    ) {
        val fiveMinuteIntervals = (currentPosSec / 300f).toInt()
        if (fiveMinuteIntervals > 0) {
            val milestoneKey = "time_${fiveMinuteIntervals * 5}m"
            if (!firedHeartbeats.contains(milestoneKey)) {
                firedHeartbeats.add(milestoneKey)
                AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = podcastId,
                    podcastName = podcastName,
                    episodeId = episodeId,
                    episodeTitle = episodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = 0,
                    heartbeatType = "interval",
                    entryPoint = entryPoint,
                )
            }
        }
    }

    fun updateHeartbeatsForPosition(
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        val percent = (positionMs.toFloat() / durationMs.toFloat()) * 100f

        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone) {
                firedHeartbeats.add("percent_$milestone")
            }
        }

        val positionSec = positionMs / 1000f
        val fiveMinuteIntervals = (positionSec / 300f).toInt()
        for (i in 1..fiveMinuteIntervals) {
            firedHeartbeats.add("time_${i * 5}m")
        }
    }

    fun trackManualCompletion(
        episodeId: String,
        totalDurationSeconds: Float,
    ) {
        AnalyticsHelper.trackPlaybackCompleted(
            podcastId = podcastId,
            podcastName = podcastName,
            podcastGenre = podcastGenre,
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            totalDurationSeconds = totalDurationSeconds,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
        )
    }

    /** `playback_error` and error tracking, once per cause per episode until a listen starts ([PlaybackErrorOnce]). */
    fun trackPlayerError(error: androidx.media3.common.PlaybackException) {
        val failingEpisodeId = episodeId ?: playerProvider()?.currentMediaItem?.mediaId?.stripEpisodePrefix()
        if (!listens.isFirstError(failingEpisodeId, error.errorCodeName)) return
        AnalyticsHelper.trackPlaybackError(
            errorCode = error.errorCodeName,
            errorMessage = error.message ?: "Unknown",
            podcastId = podcastId,
            episodeId = failingEpisodeId,
            podcastName = podcastName,
            episodeTitle = episodeTitle,
            listenId = listens.current?.listenId,
            cause = error,
        )
    }
}

internal fun analyticsEntryPointContext(extras: Bundle?): Map<String, Any>? {
    if (extras == null) return null
    val context = mutableMapOf<String, Any>()
    extras.keySet().forEach { key ->
        @Suppress("DEPRECATION")
        val value = extras.get(key)
        if (value != null && key != "entry_point" && CastMediaMetadata.isAnalyticsSafeExtra(key)) {
            context[key] = value
        }
    }
    return context.ifEmpty { null }
}
