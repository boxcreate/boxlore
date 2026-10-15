package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

/** Total remaining whole minutes, including hours; a partial last minute still displays one. */
internal fun remainingMixMinutes(durationSeconds: Int, progress: Float): Int {
    val remainingSeconds = ((1f - progress) * durationSeconds).toInt()
    return (remainingSeconds / 60).coerceAtLeast(1)
}

/** What Daily Mix's header summarises: episodes still to hear and the listening time they add up to. */
internal data class DailyMixSummary(val episodes: Int, val minutes: Int)

/**
 * Played episodes are left out. In-progress episodes count only the time left, episodes without a
 * known length count toward the episodes but not the time.
 */
internal fun dailyMixSummary(podcasts: List<Podcast>): DailyMixSummary {
    val toHear = podcasts.filter { it.latestEpisode != null && it.episodeStatus != EpisodeStatus.COMPLETED }
    return DailyMixSummary(episodes = toHear.size, minutes = toHear.sumOf(::minutesToHear))
}

private fun minutesToHear(podcast: Podcast): Int {
    val duration = podcast.latestEpisode?.duration?.takeIf { it > 0 } ?: return 0
    val progress = podcast.resumeProgress?.takeIf { podcast.episodeStatus == EpisodeStatus.IN_PROGRESS && it > 0f }
    return if (progress != null) remainingMixMinutes(duration, progress) else duration / 60
}
