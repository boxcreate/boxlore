package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import cx.aswin.boxlore.feature.home.SmartHeroItem

internal object HomePlaybackStateLogic {
    fun progressRatio(
        progressMs: Long,
        durationMs: Long,
    ): Float =
        if (durationMs > 0) {
            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun statusForHistory(
        isCompleted: Boolean,
        progressMs: Long,
    ): EpisodeStatus =
        when {
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

    fun isBriefingDisplayedInResume(
        heroList: List<SmartHeroItem>,
        briefingPodcastId: String,
    ): Boolean =
        heroList.any { item ->
            when (item.type) {
                HeroType.RESUME -> item.podcast.id == briefingPodcastId
                HeroType.RESUME_GRID -> item.gridItems.any { it.id == briefingPodcastId }
                else -> false
            }
        }

    fun shouldShowBriefing(
        rawBriefing: Briefing?,
        completedEpisodeIds: Set<String>,
        briefingDismissedDate: String,
        briefingDismissedForever: Boolean,
        heroList: List<SmartHeroItem>,
    ): Boolean {
        if (rawBriefing == null) return false
        val briefingEpisodeId = "briefing_${rawBriefing.region}_${rawBriefing.date}"
        val isCompleted = completedEpisodeIds.contains(briefingEpisodeId)
        val isDismissed = rawBriefing.date == briefingDismissedDate
        val isDisplayedInResume =
            isBriefingDisplayedInResume(
                heroList = heroList,
                briefingPodcastId = "briefing_${rawBriefing.region}",
            )
        return !isCompleted && !isDismissed && !briefingDismissedForever && !isDisplayedInResume
    }
}
