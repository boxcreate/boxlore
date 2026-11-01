package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

data class HomeListeningHistoryItem(
    val episodeId: String,
    val podcastId: String,
    val podcastName: String,
    val podcastImageUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val isLiked: Boolean,
    val lastPlayedAt: Long,
    /** When it was played to the end; null when only marked played or not finished. */
    val finishedAt: Long? = null,
)

/**
 * Episodes played to the end in boxlore since it was installed on this device. Marked-played rows
 * and rows restored or synced from before the install don't count.
 */
internal fun List<HomeListeningHistoryItem>.countFinishedSince(installedAtMs: Long): Int =
    count { it.isCompleted && it.finishedAt?.let { at -> at >= installedAtMs } == true }

internal fun ListeningHistoryEntity.toHomeListeningHistoryItem(): HomeListeningHistoryItem = HomeListeningHistoryItem(
    episodeId = episodeId,
    podcastId = podcastId,
    podcastName = podcastName,
    podcastImageUrl = podcastImageUrl,
    progressMs = progressMs,
    durationMs = durationMs,
    isCompleted = isCompleted,
    isLiked = isLiked,
    lastPlayedAt = lastPlayedAt,
    finishedAt = finishedAt,
)
