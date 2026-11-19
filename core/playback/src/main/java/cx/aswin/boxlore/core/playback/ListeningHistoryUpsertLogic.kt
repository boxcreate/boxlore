package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode

/**
 * Pure ListeningHistoryEntity builders for PlaybackRepository write paths.
 */
object ListeningHistoryUpsertLogic {
    data class ProgressSaveInput(
        val podcastId: String,
        val episodeId: String,
        val positionMs: Long,
        val durationMs: Long,
        val episodeTitle: String,
        val episodeImageUrl: String?,
        val podcastImageUrl: String?,
        val episodeAudioUrl: String,
        val podcastName: String,
        val isCompleted: Boolean,
        val isLiked: Boolean,
        val lastPlayedAt: Long,
        val enclosureType: String? = null,
        val episodeDescription: String? = null,
    )

    fun buildProgressSaveEntity(input: ProgressSaveInput): ListeningHistoryEntity = ListeningHistoryEntity(
        episodeId = input.episodeId,
        podcastId = input.podcastId,
        episodeTitle = input.episodeTitle,
        episodeImageUrl = input.episodeImageUrl,
        podcastImageUrl = input.podcastImageUrl,
        episodeAudioUrl = input.episodeAudioUrl,
        podcastName = input.podcastName,
        progressMs = input.positionMs,
        durationMs = input.durationMs,
        isCompleted = input.isCompleted,
        isLiked = input.isLiked,
        lastPlayedAt = input.lastPlayedAt,
        isDirty = true,
        enclosureType = input.enclosureType,
        isManualCompletion = false,
        isBulkCompletion = false,
        episodeDescription = input.episodeDescription,
        // A save from playback is a play (and, completed, a finish by listening); the DAO keeps a
        // stored row's earlier dates.
        firstPlayedAt = input.lastPlayedAt.takeIf { input.positionMs > 0L || input.isCompleted },
        finishedAt = input.lastPlayedAt.takeIf { input.isCompleted },
    )

    /**
     * The row "Mark all as played" writes for [episode], or null when [existing] is already
     * completed: a row finished by listening (or marked played before) stays exactly as it is, so
     * it keeps its place in Listening history and insights and isn't uploaded again.
     */
    fun buildBulkCompleteEntity(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
        existing: ListeningHistoryEntity?,
        nowMs: Long,
    ): ListeningHistoryEntity? {
        if (existing?.isCompleted == true) return null
        if (existing != null) {
            return existing.copy(
                isCompleted = true,
                progressMs = 0L,
                isBulkCompletion = true,
                lastPlayedAt = nowMs,
                isDirty = true,
                episodeDescription = existing.episodeDescription ?: episode.description,
            )
        }
        return ListeningHistoryEntity(
            episodeId = episode.id,
            podcastId = podcastId,
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = podcastImageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = podcastTitle,
            progressMs = 0L,
            durationMs = episode.duration * 1000L,
            isCompleted = true,
            isLiked = false,
            lastPlayedAt = nowMs,
            isDirty = true,
            enclosureType = episode.enclosureType,
            isManualCompletion = false,
            isBulkCompletion = true,
            episodeDescription = episode.description,
        )
    }

    /**
     * The row "Mark all as unplayed" writes for [existing], or null when it isn't completed. It
     * keeps its place in time: an episode heard last month stays under last month in Listening
     * history and ranking instead of moving to today. Its time moves on by one millisecond, so the
     * reset still wins over the completed copy on other devices and in the cloud, which keep the
     * newer `lastPlayedAt`.
     */
    fun buildBulkUncompleteEntity(existing: ListeningHistoryEntity): ListeningHistoryEntity? {
        if (!existing.isCompleted) return null
        return existing.copy(
            isCompleted = false,
            progressMs = 0L,
            isManualCompletion = false,
            isBulkCompletion = false,
            lastPlayedAt = existing.lastPlayedAt + 1L,
            isDirty = true,
        )
    }
}
