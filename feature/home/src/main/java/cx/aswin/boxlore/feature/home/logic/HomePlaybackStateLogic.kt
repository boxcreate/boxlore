package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem

internal object HomePlaybackStateLogic {
    fun progressRatio(
        progressMs: Long,
        durationMs: Long,
    ): Float = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    fun statusForHistory(
        isCompleted: Boolean,
        progressMs: Long,
    ): EpisodeStatus = when {
        isCompleted -> EpisodeStatus.COMPLETED
        progressMs > 0L -> EpisodeStatus.IN_PROGRESS
        else -> EpisodeStatus.UNPLAYED
    }

    fun buildEpisodePlaybackState(
        allHistory: List<HomeListeningHistoryItem>,
        completedEpisodeIds: Set<String>,
    ): Map<String, Pair<EpisodeStatus, Float>> {
        val episodePlaybackState =
            allHistory
                .associate { history ->
                    val ratio = progressRatio(history.progressMs, history.durationMs)
                    val status = statusForHistory(history.isCompleted, history.progressMs)
                    history.episodeId to (status to ratio)
                }.toMutableMap()

        completedEpisodeIds.forEach { completedId ->
            if (!episodePlaybackState.containsKey(completedId)) {
                episodePlaybackState[completedId] = (EpisodeStatus.COMPLETED to 1f)
            }
        }
        return episodePlaybackState
    }

    fun isHeroItemCurrent(
        itemEpisodeId: String?,
        itemPodcastId: String,
        currentPlayingEpisodeId: String?,
        currentPlayingPodcastId: String?,
    ): Boolean {
        val episodeId = itemEpisodeId?.takeIf { it.isNotBlank() }
        if (episodeId != null) {
            return episodeId == currentPlayingEpisodeId
        }
        return itemPodcastId.isNotBlank() && itemPodcastId == currentPlayingPodcastId
    }

    fun isHeroItemPlaying(
        itemEpisodeId: String?,
        itemPodcastId: String,
        currentPlayingEpisodeId: String?,
        currentPlayingPodcastId: String?,
        isPlaying: Boolean,
    ): Boolean = isPlaying &&
        isHeroItemCurrent(
            itemEpisodeId = itemEpisodeId,
            itemPodcastId = itemPodcastId,
            currentPlayingEpisodeId = currentPlayingEpisodeId,
            currentPlayingPodcastId = currentPlayingPodcastId,
        )

    /**
     * The Home brief card stays up until the listener finishes the edition or hides it. It also
     * stays when the same brief appears in a resume hero card or grid.
     */
    fun shouldShowBriefing(
        rawBriefing: Briefing?,
        completedEpisodeIds: Set<String>,
        briefingDismissedDate: String,
        briefingDismissedForever: Boolean,
    ): Boolean {
        if (rawBriefing == null) return false
        val briefingEpisodeId = "briefing_${rawBriefing.region}_${rawBriefing.date}"
        val isCompleted = completedEpisodeIds.contains(briefingEpisodeId)
        val isDismissed = rawBriefing.date == briefingDismissedDate
        return !isCompleted && !isDismissed && !briefingDismissedForever
    }
}
