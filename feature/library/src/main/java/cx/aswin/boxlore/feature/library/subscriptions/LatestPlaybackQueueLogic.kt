package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

/** Snapshots the visible order at tap time; later refreshes and rankings cannot reorder playback. */
internal fun latestPlaybackEpisodes(podcasts: List<Podcast>, selectedEpisodeId: String? = null): List<Episode> {
    val start = if (selectedEpisodeId == null) 0 else podcasts.indexOfFirst { it.latestEpisode?.id == selectedEpisodeId }
    if (start < 0) return emptyList()
    return podcasts.drop(start).mapNotNull { podcast ->
        val episode = podcast.latestEpisode ?: return@mapNotNull null
        if (podcast.episodeStatus == EpisodeStatus.COMPLETED && episode.id != selectedEpisodeId) {
            return@mapNotNull null
        }
        episode.copy(
            podcastId = podcast.id,
            podcastTitle = episode.podcastTitle?.takeIf(String::isNotBlank) ?: podcast.title,
            podcastImageUrl = episode.podcastImageUrl?.takeIf(String::isNotBlank) ?: podcast.imageUrl,
            podcastArtist = episode.podcastArtist?.takeIf(String::isNotBlank) ?: podcast.artist,
            podcastGenre = episode.podcastGenre?.takeIf(String::isNotBlank) ?: podcast.genre,
        )
    }.distinctBy { it.id }
}
