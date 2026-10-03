package cx.aswin.boxlore.core.playback.service

import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.playback.CastMediaMetadata
import cx.aswin.boxlore.core.playback.PlaybackHistorySeedPolicy
import cx.aswin.boxlore.core.playback.PlaybackHistorySeedSource
import cx.aswin.boxlore.core.playback.PlaybackProgressSnapshot
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import cx.aswin.boxlore.core.playback.toPlaybackHistorySeedSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun BoxLorePlaybackService.persistNaturalCompletionFromLifecycle(episodeId: String, durationMs: Long,): kotlinx.coroutines.Job {
    val fallbackPodcastId = telemetrySession.podcastId
    val fallbackPodcastName = telemetrySession.podcastName
    val fallbackEpisodeTitle = telemetrySession.episodeTitle
    val fallbackMediaItem =
        playbackPlayer
            ?.currentMediaItem
            ?.takeIf { it.mediaId.stripEpisodePrefix() == episodeId }
    val resolvedDurationMs =
        durationMs.takeIf { it > 0L }
            ?: telemetrySession.totalDurationMs
    val persistenceJob =
        serviceScope.launch {
            persistNaturalCompletionOnce(
                episodeId = episodeId,
                durationMs = resolvedDurationMs,
                fallbackPodcastId = fallbackPodcastId,
                fallbackPodcastName = fallbackPodcastName,
                fallbackEpisodeTitle = fallbackEpisodeTitle,
                fallbackMediaItem = fallbackMediaItem,
            )
        }
    telemetrySession.end(forceCompleted = true, isTransition = false)
    return persistenceJob
}

// This completion implementation was moved unchanged from the service's existing baseline.
@Suppress("CyclomaticComplexMethod")
private suspend fun BoxLorePlaybackService.persistNaturalCompletionOnce(
    episodeId: String,
    durationMs: Long,
    fallbackPodcastId: String?,
    fallbackPodcastName: String?,
    fallbackEpisodeTitle: String?,
    fallbackMediaItem: MediaItem?,
) {
    val dao = database.listeningHistoryDao()
    val existing = dao.getHistoryItem(episodeId)
    val resolvedDurationMs =
        existing?.let { durationMs.takeIf { it > 0L } ?: it.durationMs }
            ?: durationMs.coerceAtLeast(0L)
    if (existing == null) {
        val queueItem =
            runCatching {
                queueRepository.getQueueItemByEpisodeId(episodeId)
            }.getOrNull()
        val podcastId =
            queueItem?.podcastId
                ?: fallbackPodcastId
                ?: ""
        val podcast =
            podcastId.takeIf { it.isNotBlank() }?.let {
                runCatching { database.podcastDao().getPodcast(it) }.getOrNull()
            }
        val episodeTitle =
            CastMediaMetadata.queueTitle(queueItem?.title)
                ?: CastMediaMetadata.queueTitle(fallbackEpisodeTitle)
                ?: CastMediaMetadata.queueTitle(fallbackMediaItem?.mediaMetadata?.title)
        if (episodeTitle == null) {
            android.util.Log.w(
                "BoxCastPlayer",
                "Skipping completion row for $episodeId until episode metadata is available",
            )
            return
        }
        dao.insertIfAbsent(
            cx.aswin.boxlore.core.database.ListeningHistoryEntity(
                episodeId = episodeId,
                podcastId = podcastId,
                episodeTitle = episodeTitle,
                episodeImageUrl =
                queueItem?.imageUrl
                    ?: fallbackMediaItem?.mediaMetadata?.artworkUri?.toString(),
                podcastImageUrl = queueItem?.podcastImageUrl ?: podcast?.imageUrl,
                episodeAudioUrl =
                queueItem?.audioUrl
                    ?: fallbackMediaItem?.localConfiguration?.uri?.toString(),
                podcastName =
                queueItem?.podcastTitle
                    ?: podcast?.title
                    ?: fallbackPodcastName
                    ?: fallbackMediaItem?.mediaMetadata?.artist?.toString()
                    ?: "",
                progressMs = 0L,
                durationMs = resolvedDurationMs,
                isCompleted = false,
                lastPlayedAt = System.currentTimeMillis(),
                enclosureType = queueItem?.enclosureType,
                isManualCompletion = false,
                episodeDescription = queueItem?.description,
            ),
        )
    }
    val persisted = existing ?: dao.getHistoryItem(episodeId) ?: return
    val completionDurationMs = durationMs.takeIf { it > 0L } ?: persisted.durationMs
    dao.completeFromPlayback(
        episodeId = episodeId,
        durationMs = completionDurationMs,
        lastPlayedAt = System.currentTimeMillis(),
        isManualCompletion = false,
    )
    mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_CONTINUE_ID, 20, null)
    mediaSession?.notifyChildrenChanged(AutoBrowseContract.LIBRARY_HISTORY_ID, 50, null)
    // Resume / Home collage tiles must track completed episodes, not stay on a stale PNG.
    requestAutoCollageRefresh(force = true)
}

internal suspend fun BoxLorePlaybackService.buildMissingProgressHistory(
    snapshot: PlaybackProgressSnapshot,
): cx.aswin.boxlore.core.database.ListeningHistoryEntity? {
    val sources = loadHistorySeedSources(snapshot.episodeId)
    val telemetry = telemetryHistorySeedSource(snapshot)
    val podcastId = PlaybackHistorySeedPolicy.resolvePodcastId(sources, telemetry)
    val podcast =
        podcastId.takeIf(String::isNotBlank)?.let { id ->
            runCatching { database.podcastDao().getPodcast(id) }.getOrNull()
        }
    return PlaybackHistorySeedPolicy.build(
        snapshot = snapshot,
        sources = sources,
        podcast =
        podcast?.let {
            PlaybackHistorySeedSource(
                podcastImageUrl = it.imageUrl,
                podcastName = it.title,
            )
        },
        telemetry = telemetry,
        nowMs = System.currentTimeMillis(),
    )
}

private suspend fun BoxLorePlaybackService.loadHistorySeedSources(episodeId: String): List<PlaybackHistorySeedSource> {
    val sources = mutableListOf<PlaybackHistorySeedSource>()

    runCatching { queueRepository.getQueueItemByEpisodeId(episodeId) }
        .getOrNull()
        ?.let { sources += it.toPlaybackHistorySeedSource() }

    runCatching { database.downloadedEpisodeDao().getDownload(episodeId) }
        .getOrNull()
        ?.let { sources += it.toPlaybackHistorySeedSource() }

    runCatching { database.localEpisodeCatalogDao().getEpisode(episodeId) }
        .getOrNull()
        ?.let {
            val podcast = runCatching { database.podcastDao().getPodcast(it.podcastId) }.getOrNull()
            sources += it.toPlaybackHistorySeedSource(podcastName = podcast?.title, podcastImageUrl = podcast?.imageUrl)
        }

    runCatching { database.rssEpisodeDao().getEpisode(episodeId) }
        .getOrNull()
        ?.let {
            val podcast = runCatching { database.podcastDao().getPodcast(it.podcastId) }.getOrNull()
            sources += it.toPlaybackHistorySeedSource(podcastName = podcast?.title, podcastImageUrl = podcast?.imageUrl)
        }

    runCatching { database.episodeSupplementDao().getEpisode(episodeId) }
        .getOrNull()
        ?.let {
            val podcast = runCatching { database.podcastDao().getPodcast(it.podcastId) }.getOrNull()
            sources += it.toPlaybackHistorySeedSource(podcastName = podcast?.title, podcastImageUrl = podcast?.imageUrl)
        }

    if (sources.none { !it.podcastName.isNullOrBlank() }) {
        runCatching { podcastRepository.getEpisode(episodeId) }
            .getOrNull()
            ?.let { sources += it.toPlaybackHistorySeedSource() }
    }

    return sources
}

private fun BoxLorePlaybackService.telemetryHistorySeedSource(snapshot: PlaybackProgressSnapshot): PlaybackHistorySeedSource? {
    if (telemetrySession.episodeId != snapshot.episodeId) return null
    return PlaybackHistorySeedSource(
        podcastId = telemetrySession.podcastId,
        episodeTitle = telemetrySession.episodeTitle,
        podcastName = telemetrySession.podcastName,
    )
}

internal suspend fun BoxLorePlaybackService.persistManualCompletion(episodeId: String, playerDurationMs: Long, progressSnapshot: PlaybackProgressSnapshot?,) {
    try {
        val existing = loadOrSeedManualCompletionHistory(episodeId, progressSnapshot) ?: return
        val completionDurationMs =
            playerDurationMs.takeIf { it > 0L }
                ?: existing.durationMs
        database.listeningHistoryDao().completeFromPlayback(
            episodeId = episodeId,
            durationMs = completionDurationMs,
            lastPlayedAt = System.currentTimeMillis(),
            isManualCompletion = true,
        )
        android.util.Log.d("BoxLorePlaybackService", "Marked current episode completed: $episodeId")
        requestAutoCollageRefresh(force = true)
        telemetrySession.trackManualCompletion(
            episodeId = episodeId,
            totalDurationSeconds = completionDurationMs / 1000f,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        android.util.Log.e(
            "BoxLorePlaybackService",
            "Failed to mark current episode completed",
            error,
        )
    }
}

private suspend fun BoxLorePlaybackService.loadOrSeedManualCompletionHistory(
    episodeId: String,
    progressSnapshot: PlaybackProgressSnapshot?,
): cx.aswin.boxlore.core.database.ListeningHistoryEntity? {
    val dao = database.listeningHistoryDao()
    dao.getHistoryItem(episodeId)?.let { return it }
    val seed = progressSnapshot?.let { buildMissingProgressHistory(it) } ?: return null
    dao.insertIfAbsent(seed)
    dao.enrichMetadataIfMissing(
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
    return dao.getHistoryItem(episodeId)
}
