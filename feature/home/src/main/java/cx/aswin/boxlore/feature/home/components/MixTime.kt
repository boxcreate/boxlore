package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.ListeningTime

/** Minutes left, including hours, rounded as the hero and mini player round them; a partial minute counts. */
internal fun remainingMixMinutes(durationSeconds: Int, progress: Float): Int =
    ListeningTime.minutesLeft(durationSeconds.toLong(), progress)?.toInt() ?: 1

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
    return remainingMixMinutes(duration, progress ?: 0f)
}
