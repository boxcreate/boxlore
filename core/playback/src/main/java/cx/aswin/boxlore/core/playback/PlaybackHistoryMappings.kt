package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningGroupStateSnapshot
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRowSnapshot
import cx.aswin.boxlore.core.model.ListeningRollupSnapshot
import cx.aswin.boxlore.core.model.ListeningSessionSnapshot

internal fun ListeningHistoryEntity.toListeningHistoryItem(): ListeningHistoryItem = ListeningHistoryItem(
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
    firstPlayedAt = firstPlayedAt,
    finishedAt = finishedAt,
)

internal fun ListeningHistoryItem.toListeningHistoryEntity(): ListeningHistoryEntity = ListeningHistoryEntity(
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
    firstPlayedAt = firstPlayedAt,
    finishedAt = finishedAt,
)

/** [this] row exactly as stored (raw completion flag, like time, sync state), for an Undo that restores it unchanged. */
internal fun ListeningHistoryEntity.toRowSnapshot(): ListeningHistoryRowSnapshot = ListeningHistoryRowSnapshot(
    item =
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
        isCompleted = isCompleted,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
        enclosureType = enclosureType,
        episodeDescription = episodeDescription,
        firstPlayedAt = firstPlayedAt,
        finishedAt = finishedAt,
    ),
    likedAt = likedAt,
    isDirty = isDirty,
    syncedAt = syncedAt,
    isManualCompletion = isManualCompletion,
    isBulkCompletion = isBulkCompletion,
)

/** The stored row [toRowSnapshot] captured, unchanged. */
internal fun ListeningHistoryRowSnapshot.toListeningHistoryEntity(): ListeningHistoryEntity = ListeningHistoryEntity(
    episodeId = item.episodeId,
    podcastId = item.podcastId,
    episodeTitle = item.episodeTitle,
    episodeImageUrl = item.episodeImageUrl,
    podcastImageUrl = item.podcastImageUrl,
    episodeAudioUrl = item.episodeAudioUrl,
    podcastName = item.podcastName,
    progressMs = item.progressMs,
    durationMs = item.durationMs,
    isCompleted = item.isCompleted,
    isLiked = item.isLiked,
    likedAt = likedAt,
    lastPlayedAt = item.lastPlayedAt,
    isDirty = isDirty,
    syncedAt = syncedAt,
    enclosureType = item.enclosureType,
    isManualCompletion = isManualCompletion,
    isBulkCompletion = isBulkCompletion,
    episodeDescription = item.episodeDescription,
    firstPlayedAt = item.firstPlayedAt,
    finishedAt = item.finishedAt,
)

internal fun EpisodeGroupStateEntity.toSnapshot(): ListeningGroupStateSnapshot = ListeningGroupStateSnapshot(
    podcastId = podcastId,
    catalogKey = catalogKey,
    likedAt = likedAt,
    likeClearedAt = likeClearedAt,
    completedAt = completedAt,
    completeClearedAt = completeClearedAt,
    version = version,
)

internal fun ListeningGroupStateSnapshot.toEntity(): EpisodeGroupStateEntity = EpisodeGroupStateEntity(
    podcastId = podcastId,
    catalogKey = catalogKey,
    likedAt = likedAt,
    likeClearedAt = likeClearedAt,
    completedAt = completedAt,
    completeClearedAt = completeClearedAt,
    version = version,
)

internal fun ListeningSessionEntity.toListeningSessionSnapshot(): ListeningSessionSnapshot = ListeningSessionSnapshot(
    sessionId = sessionId,
    episodeId = episodeId,
    podcastId = podcastId,
    startedAt = startedAt,
    endedAt = endedAt,
    consumedMs = consumedMs,
    completed = completed,
    localDay = localDay,
    timeBucket = timeBucket,
    deviceId = deviceId,
    wallMs = wallMs,
    source = source,
    output = output,
    episodePublishedAt = episodePublishedAt,
)

internal fun ListeningSessionSnapshot.toListeningSessionEntity(): ListeningSessionEntity = ListeningSessionEntity(
    sessionId = sessionId,
    episodeId = episodeId,
    podcastId = podcastId,
    startedAt = startedAt,
    endedAt = endedAt,
    consumedMs = consumedMs,
    completed = completed,
    localDay = localDay,
    timeBucket = timeBucket,
    deviceId = deviceId,
    wallMs = wallMs,
    source = source,
    output = output,
    episodePublishedAt = episodePublishedAt,
)

internal fun ListeningRollupEntity.toListeningRollupSnapshot(): ListeningRollupSnapshot = ListeningRollupSnapshot(
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
    deviceId = deviceId,
    wallMs = wallMs,
)

internal fun ListeningRollupSnapshot.toListeningRollupEntity(): ListeningRollupEntity = ListeningRollupEntity(
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
    deviceId = deviceId,
    wallMs = wallMs,
)
