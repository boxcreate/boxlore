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
 * Episodes played to the end after boxlore was installed here, on this phone or another one synced to
 * the same account. Marked-played rows and listening from before the install don't count; a first
 * sign-in or a library import starts the review prompt's quiet days, so synced history can't
 * trigger it at once.
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
