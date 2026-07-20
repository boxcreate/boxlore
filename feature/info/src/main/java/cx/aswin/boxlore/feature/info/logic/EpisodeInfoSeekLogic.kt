package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.ListeningHistoryUpsertLogic

/** Pure helpers for episode-info seek / progress-save wiring. */
object EpisodeInfoSeekLogic {
    fun progressSaveInputForSeek(
        podcastId: String,
        podcastTitle: String,
        episode: Episode,
        positionMs: Long,
        durationMs: Long,
        isLiked: Boolean,
        lastPlayedAt: Long,
    ): ListeningHistoryUpsertLogic.ProgressSaveInput =
        ListeningHistoryUpsertLogic.ProgressSaveInput(
            podcastId = podcastId,
            episodeId = episode.id,
            positionMs = positionMs,
            durationMs = durationMs,
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = episode.podcastImageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = podcastTitle,
            isCompleted = false,
            isLiked = isLiked,
            lastPlayedAt = lastPlayedAt,
            enclosureType = episode.enclosureType,
            episodeDescription = episode.description,
        )
}
