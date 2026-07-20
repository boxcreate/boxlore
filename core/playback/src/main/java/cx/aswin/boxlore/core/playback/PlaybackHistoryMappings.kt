package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningRollupSnapshot
import cx.aswin.boxlore.core.model.ListeningSessionSnapshot

internal fun ListeningHistoryEntity.toListeningHistoryItem(): ListeningHistoryItem =
    ListeningHistoryItem(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = episodeTitle,
        episodeImageUrl = episodeImageUrl,
        podcastImageUrl = podcastImageUrl,
        episodeAudioUrl = episodeAudioUrl,
        podcastName = podcastName,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = ListeningCompletionLogic.isCompleted(isCompleted, progressMs, durationMs),
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
        enclosureType = enclosureType,
        episodeDescription = episodeDescription,
    )

internal fun ListeningHistoryItem.toListeningHistoryEntity(): ListeningHistoryEntity =
    ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = episodeTitle,
        episodeImageUrl = episodeImageUrl,
        podcastImageUrl = podcastImageUrl,
        episodeAudioUrl = episodeAudioUrl,
        podcastName = podcastName,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
        isDirty = true,
        enclosureType = enclosureType,
        episodeDescription = episodeDescription,
    )

internal fun ListeningSessionEntity.toListeningSessionSnapshot(): ListeningSessionSnapshot =
    ListeningSessionSnapshot(
        sessionId = sessionId,
        episodeId = episodeId,
        podcastId = podcastId,
        startedAt = startedAt,
        endedAt = endedAt,
        consumedMs = consumedMs,
        completed = completed,
        localDay = localDay,
        timeBucket = timeBucket,
    )

internal fun ListeningSessionSnapshot.toListeningSessionEntity(): ListeningSessionEntity =
    ListeningSessionEntity(
        sessionId = sessionId,
        episodeId = episodeId,
        podcastId = podcastId,
        startedAt = startedAt,
        endedAt = endedAt,
        consumedMs = consumedMs,
        completed = completed,
        localDay = localDay,
        timeBucket = timeBucket,
    )

internal fun ListeningRollupEntity.toListeningRollupSnapshot(): ListeningRollupSnapshot =
    ListeningRollupSnapshot(
        localDay = localDay,
        episodeId = episodeId,
        podcastId = podcastId,
        consumedMs = consumedMs,
        sessionCount = sessionCount,
        completionCount = completionCount,
        lastListenedAt = lastListenedAt,
        morningMs = morningMs,
        afternoonMs = afternoonMs,
        eveningMs = eveningMs,
        nightMs = nightMs,
    )

internal fun ListeningRollupSnapshot.toListeningRollupEntity(): ListeningRollupEntity =
    ListeningRollupEntity(
        localDay = localDay,
        episodeId = episodeId,
        podcastId = podcastId,
        consumedMs = consumedMs,
        sessionCount = sessionCount,
        completionCount = completionCount,
        lastListenedAt = lastListenedAt,
        morningMs = morningMs,
        afternoonMs = afternoonMs,
        eveningMs = eveningMs,
        nightMs = nightMs,
    )
