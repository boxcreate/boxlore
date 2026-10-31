package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.feature.info.InfoListeningProgressItem
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel.EpisodePlaybackState

/** Each episode row's progress and play state on the show page ([cx.aswin.boxlore.feature.info.PodcastInfoViewModel.episodePlaybackState]). */
internal object PodcastEpisodePlaybackStateLogic {
    /**
     * Started, unfinished episodes from [history] show their progress to resume; the episode
     * [player] has loaded overrides its entry with the live position, but only when it belongs to
     * [loadedPodcastId], the show this page lists.
     */
    fun episodeStates(
        player: PlayerState,
        history: List<InfoListeningProgressItem>,
        loadedPodcastId: String?,
    ): Map<String, EpisodePlaybackState> {
        val map = mutableMapOf<String, EpisodePlaybackState>()

        // 1. Map History (Resume State)
        history.forEach { item ->
            if (!item.isCompleted && item.progressMs > 0L && item.durationMs > 0L) {
                val progress = (item.progressMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                map[item.episodeId] =
                    EpisodePlaybackState(
                        isPlaying = false,
                        isResume = true,
                        progress = progress,
                        timeLeft = timeLeftLabel((item.durationMs - item.progressMs) / 1000),
                    )
            }
        }

        // 2. Override with Active Player State ONLY if the playing episode belongs to this podcast
        val currentEp = player.currentEpisode
        if (currentEp != null && (currentEp.podcastId == loadedPodcastId || player.currentPodcast?.id == loadedPodcastId)) {
            val progress = if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f
            val remainingSeconds = if (player.duration > 0) (player.duration - player.position) / 1000 else 0L
            map[currentEp.id] =
                EpisodePlaybackState(
                    isPlaying = player.isPlaying,
                    isResume = true, // Currently playing is technically "resumed" or "active"
                    progress = progress,
                    timeLeft = timeLeftLabel(remainingSeconds),
                )
        }

        return map
    }

    /** "1h 5m left" or "12m left"; null when no time is left. */
    fun timeLeftLabel(remainingSeconds: Long): String? {
        if (remainingSeconds <= 0) return null
        val h = remainingSeconds / 3600
        val m = (remainingSeconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m left" else "${m}m left"
    }
}
