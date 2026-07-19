package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

internal fun isNewEpisode(
    episode: Episode,
    podcast: Podcast,
    status: EpisodeStatus,
): Boolean =
    status == EpisodeStatus.UNPLAYED &&
        podcast.subscribedAt > 0L &&
        episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)

internal fun denseDurationText(
    durationSeconds: Int,
    progress: Float,
    isInProgress: Boolean,
): String? {
    if (durationSeconds <= 0) return null
    if (isInProgress && progress > 0f) {
        val remaining = ((1f - progress) * durationSeconds).toInt()
        return formatDenseDuration(remaining, suffix = " left")
    }
    return formatDenseDuration(durationSeconds)
}

private fun formatDenseDuration(
    seconds: Int,
    suffix: String = "",
): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m$suffix" else "${minutes}m$suffix"
}
