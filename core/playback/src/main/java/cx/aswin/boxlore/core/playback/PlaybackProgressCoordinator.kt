package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.playback.SleepTimerHolder
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class PlaybackProgressSnapshot(
    val sequence: Long,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val hasBeenPlayingFor10s: Boolean,
    val allowZeroPosition: Boolean,
    val activePlaybackEnded: Boolean = false,
    val episodeTitle: String?,
    val episodeImageUrl: String?,
    val episodeAudioUrl: String?,
    val podcastName: String?,
    val enclosureType: String?,
)

/**
 * Playback progress ticker: periodic save, sleep-timer enforcement, and heartbeat dispatch hooks.
 * Extracted from [cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService].
 */
internal class PlaybackProgressCoordinator(
    private val mainDispatcher: CoroutineDispatcher,
    private val database: BoxLoreDatabase,
    private val mediaSessionProvider: () -> MediaLibrarySession?,
    private val isEffectiveEndLatched: () -> Boolean,
    private val effectiveSkipEndingMs: (Long) -> Long,
    private val updateConsumedAudio: (androidx.media3.common.Player) -> Unit,
    private val dispatchHeartbeatTelemetry: (Player) -> Unit,
    private val missingHistorySeedProvider: suspend (PlaybackProgressSnapshot) -> ListeningHistoryEntity?,
) {
    var activePlaybackStartTimeMs: Long = 0L
    private var lastProgressAnomalyEpisodeId: String? = null
    private val progressSaveMutex = Mutex()
    private var nextSnapshotSequence = 0L
    private val lastAppliedSequenceByEpisode = mutableMapOf<String, Long>()
    private val metadataEnrichmentAttemptCountByEpisode = mutableMapOf<String, Int>()
    private val missingSeedAttemptCountByEpisode = mutableMapOf<String, Int>()

    /**
     * Periodically saves playback position and dispatches heartbeat telemetry (runs on Dispatchers.Main).
     * Also checks and enforces sleep timer expiration continuously while the foreground service is active.
     */
    suspend fun startPlaybackTicker(player: Player) {
        var tickCount = 0
        while (true) {
            delay(1_000)
            updateConsumedAudio(player)

            val sleepEnd = SleepTimerHolder.activeSleepTimerEndMs
            if (sleepEnd != null && System.currentTimeMillis() >= sleepEnd) {
                SleepTimerHolder.activeSleepTimerEndMs = null
                Log.d("BoxCastPlayer", "Foreground Service Sleep Timer: Expired! Pausing player.")
                withContext(mainDispatcher) {
                    if (player.isPlaying) player.pause()
                }
            }

            tickCount++
            if (tickCount % 10 == 0) {
                saveProgressOnce(player)
                dispatchHeartbeatTelemetry(player)
            }
        }
    }

    /**
     * Captures Player truth synchronously from a main-thread callback. This matters when a caller
     * stops and immediately clears the playlist: the later Room write must retain the pre-clear
     * media id and position.
     */
    fun captureProgressSnapshot(
        player: Player,
        allowZeroPosition: Boolean = false,
        activePlaybackEnded: Boolean = false,
    ): PlaybackProgressSnapshot? {
        if (isEffectiveEndLatched()) return null
        val currentItem = player.currentMediaItem ?: return null
        val episodeId = currentItem.mediaId.stripEpisodePrefix()
        val nowMs = System.currentTimeMillis()
        val zeroStartRequested =
            PlaybackLifecycleSignals.consumePendingZeroStart(episodeId)
        return PlaybackProgressSnapshot(
            sequence = ++nextSnapshotSequence,
            episodeId = episodeId,
            positionMs = player.currentPosition,
            durationMs = player.duration,
            hasBeenPlayingFor10s =
            activePlaybackStartTimeMs > 0 &&
                nowMs - activePlaybackStartTimeMs >= 10_000L,
            allowZeroPosition = allowZeroPosition || zeroStartRequested,
            activePlaybackEnded = activePlaybackEnded,
            episodeTitle = CastMediaMetadata.queueTitle(currentItem.mediaMetadata.title),
            episodeImageUrl = currentItem.mediaMetadata.artworkUri?.toString(),
            episodeAudioUrl = currentItem.localConfiguration?.uri?.toString(),
            podcastName =
            CastMediaMetadata.queueTitle(currentItem.mediaMetadata.albumTitle)
                ?: CastMediaMetadata.queueTitle(currentItem.mediaMetadata.artist)
                ?: CastMediaMetadata.queueTitle(currentItem.mediaMetadata.subtitle),
            enclosureType = currentItem.localConfiguration?.mimeType,
        )
    }

    /** Captures and saves the current playback position to Room once. */
    suspend fun saveProgressOnce(
        player: Player,
        allowZeroPosition: Boolean = false,
        activePlaybackEnded: Boolean = false,
    ) {
        val snapshot =
            withContext(mainDispatcher) {
                captureProgressSnapshot(player, allowZeroPosition, activePlaybackEnded)
            } ?: return
        saveProgressSnapshot(snapshot)
    }

    /**
     * Serializes event-driven and periodic writes. Sequence stamps reject an older suspended write
     * without blocking intentional backward seeks or restarted playback.
     */
    suspend fun saveProgressSnapshot(snapshot: PlaybackProgressSnapshot) {
        try {
            val didWrite =
                progressSaveMutex.withLock {
                    persistSnapshotLocked(snapshot)
                }
            if (didWrite) {
                withContext(mainDispatcher) {
                    try {
                        mediaSessionProvider()?.notifyChildrenChanged("home_continue_listening", 0, null)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            Log.e("AutoProgress", "Error saving progress once", e)
        }
    }

    private suspend fun persistSnapshotLocked(snapshot: PlaybackProgressSnapshot): Boolean {
        if (
            !PlaybackProgressPersistencePolicy.shouldApplySnapshot(
                incomingSequence = snapshot.sequence,
                lastAppliedSequence = lastAppliedSequenceByEpisode[snapshot.episodeId],
            )
        ) {
            return false
        }
        maybeReportProgressSyncAnomaly(
            snapshot.episodeId,
            snapshot.positionMs,
            snapshot.durationMs,
        )
        val dao = database.listeningHistoryDao()
        val existing = loadOrSeedHistory(dao, snapshot)
        val canPersistPosition =
            snapshot.positionMs > 0L || snapshot.allowZeroPosition
        val didWrite =
            if (existing != null && canPersistPosition) {
                persistExistingHistory(dao, existing, snapshot)
            } else {
                false
            }
        lastAppliedSequenceByEpisode[snapshot.episodeId] = snapshot.sequence
        return didWrite
    }

    private suspend fun loadOrSeedHistory(dao: ListeningHistoryDao, snapshot: PlaybackProgressSnapshot,): ListeningHistoryEntity? {
        val existing = dao.getHistoryItem(snapshot.episodeId)
        if (existing != null) {
            missingSeedAttemptCountByEpisode -= snapshot.episodeId
            return retryMetadataEnrichmentIfNeeded(dao, existing, snapshot)
        }

        val attemptCount =
            missingSeedAttemptCountByEpisode[snapshot.episodeId] ?: 0
        if (!PlaybackProgressPersistencePolicy.shouldAttemptMissingSeed(attemptCount)) {
            return null
        }
        missingSeedAttemptCountByEpisode[snapshot.episodeId] = attemptCount + 1
        val seed = missingHistorySeedProvider(snapshot) ?: return null
        missingSeedAttemptCountByEpisode -= snapshot.episodeId
        dao.insertIfAbsent(seed)
        dao.enrichFromSeed(seed)
        val inserted = dao.getHistoryItem(snapshot.episodeId) ?: return null
        return retryMetadataEnrichmentIfNeeded(dao, inserted, snapshot)
    }

    private suspend fun retryMetadataEnrichmentIfNeeded(
        dao: ListeningHistoryDao,
        existing: ListeningHistoryEntity,
        snapshot: PlaybackProgressSnapshot,
    ): ListeningHistoryEntity {
        if (!existing.hasIncompletePlaybackMetadata()) {
            metadataEnrichmentAttemptCountByEpisode -= snapshot.episodeId
            return existing
        }
        val attempts = metadataEnrichmentAttemptCountByEpisode[snapshot.episodeId] ?: 0
        if (!PlaybackProgressPersistencePolicy.shouldAttemptMissingSeed(attempts)) {
            return existing
        }
        metadataEnrichmentAttemptCountByEpisode[snapshot.episodeId] = attempts + 1
        val richerSeed = missingHistorySeedProvider(snapshot) ?: return existing
        dao.enrichFromSeed(richerSeed)
        val refreshed = dao.getHistoryItem(snapshot.episodeId) ?: existing
        if (!refreshed.hasIncompletePlaybackMetadata()) {
            metadataEnrichmentAttemptCountByEpisode -= snapshot.episodeId
        }
        return refreshed
    }

    private suspend fun persistExistingHistory(
        dao: ListeningHistoryDao,
        existing: ListeningHistoryEntity,
        snapshot: PlaybackProgressSnapshot,
    ): Boolean {
        val resolvedDurationMs =
            PlaybackProgressPersistencePolicy.resolveDurationMs(
                existingDurationMs = existing.durationMs,
                incomingDurationMs = snapshot.durationMs,
            )
        val shouldUpdateLastPlayedAt =
            PlaybackProgressPersistencePolicy.shouldUpdateLastPlayedAt(
                hasBeenPlayingFor10s = snapshot.hasBeenPlayingFor10s,
                allowZeroPosition = snapshot.allowZeroPosition,
                isCompleted = existing.isCompleted,
                activePlaybackEnded = snapshot.activePlaybackEnded,
            )
        val lastPlayedAt =
            if (shouldUpdateLastPlayedAt) {
                System.currentTimeMillis()
            } else {
                existing.lastPlayedAt
            }
        if (checkIsPlaybackCompleted(snapshot.positionMs, resolvedDurationMs)) {
            dao.completeFromPlayback(
                episodeId = snapshot.episodeId,
                durationMs = resolvedDurationMs,
                lastPlayedAt = lastPlayedAt,
                isManualCompletion = false,
            )
            Log.d("AutoProgress", "Saved completed: ${snapshot.episodeId}")
            return true
        }

        val resolvedPositionMs =
            PlaybackProgressPersistencePolicy.resolvePositionMs(snapshot.positionMs)
        val changed =
            resolvedPositionMs != existing.progressMs ||
                resolvedDurationMs != existing.durationMs ||
                lastPlayedAt != existing.lastPlayedAt
        if (!changed) return false
        val updatedRows =
            dao.updateProgress(
                episodeId = snapshot.episodeId,
                progressMs = resolvedPositionMs,
                durationMs = resolvedDurationMs,
                lastPlayedAt = lastPlayedAt,
            )
        if (updatedRows == 0) {
            if (existing.isCompleted) {
                val replayUpdatedRows =
                    dao.reopenProgress(
                        episodeId = snapshot.episodeId,
                        progressMs = resolvedPositionMs,
                        durationMs = resolvedDurationMs,
                        lastPlayedAt = lastPlayedAt,
                    )
                if (replayUpdatedRows == 0) return false
            } else {
                return false
            }
        }
        Log.d(
            "AutoProgress",
            "Saved progress: ${snapshot.episodeId} @ ${resolvedPositionMs / 1000}s / ${resolvedDurationMs / 1000}s",
        )
        return true
    }

    private fun maybeReportProgressSyncAnomaly(episodeId: String, positionMs: Long, durationMs: Long,) {
        if (durationMs <= 0 || positionMs <= durationMs) return
        if (lastProgressAnomalyEpisodeId == episodeId) return
        lastProgressAnomalyEpisodeId = episodeId
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackProgressSyncAnomaly(
            anomalyType = "position_exceeds_duration",
            episodeId = episodeId,
        )
    }

    private fun checkIsPlaybackCompleted(positionMs: Long, durationMs: Long,): Boolean = PlaybackSkipPolicy.shouldCompleteFromProgress(
        positionMs = positionMs,
        durationMs = durationMs,
        effectiveSkipEndingMs = effectiveSkipEndingMs(durationMs),
    )
}

private fun ListeningHistoryEntity.hasIncompletePlaybackMetadata(): Boolean = podcastId.isBlank() ||
    episodeTitle.isBlank() ||
    episodeAudioUrl.isNullOrBlank() ||
    podcastName.isBlank() ||
    durationMs <= 0L

private suspend fun ListeningHistoryDao.enrichFromSeed(seed: ListeningHistoryEntity) {
    enrichMetadataIfMissing(
        episodeId = seed.episodeId,
        podcastId = seed.podcastId,
        episodeTitle = seed.episodeTitle,
        episodeImageUrl = seed.episodeImageUrl,
        podcastImageUrl = seed.podcastImageUrl,
        episodeAudioUrl = seed.episodeAudioUrl,
        podcastName = seed.podcastName,
        durationMs = seed.durationMs,
        enclosureType = seed.enclosureType,
        episodeDescription = seed.episodeDescription,
    )
}
