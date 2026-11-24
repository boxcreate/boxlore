package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

/** The latest five distinct episodes from the show page, excluding the episode being viewed. */
internal fun selectMoreFromEpisodes(episodes: List<Episode>, currentEpisodeId: String): List<Episode> = episodes
    .filter { it.id != currentEpisodeId }
    .sortedByDescending { it.publishedDate }
    .distinctBy { it.id }
    .take(5)

/** What a "More from" row shows at its start. */
internal sealed interface MoreFromArt {
    /** The episode's own picture. */
    data class Image(val url: String) : MoreFromArt

    /** The release date, for an episode that only has the show's cover. */
    data class Date(val epochSeconds: Long) : MoreFromArt

    /** The episode number, for one with neither its own picture nor a date. */
    data class Number(val number: Int) : MoreFromArt

    /** Nothing better than the show's cover. */
    data object ShowCover : MoreFromArt
}

/**
 * A row's own picture when it differs from the show's cover ([showImageUrl]); otherwise its date or
 * number, so a list of one show's episodes isn't a wall of the same cover.
 */
internal fun moreFromRowArt(episode: cx.aswin.boxlore.core.model.Episode, showImageUrl: String?): MoreFromArt {
    val own = episode.imageUrl?.takeIf(String::isNotBlank)
    val covers = setOfNotNull(showImageUrl?.takeIf(String::isNotBlank), episode.podcastImageUrl?.takeIf(String::isNotBlank))
    return when {
        own != null && own !in covers -> MoreFromArt.Image(own)
        episode.publishedDate > 0L -> MoreFromArt.Date(episode.publishedDate)
        (episode.episodeNumber ?: 0) > 0 -> MoreFromArt.Number(requireNotNull(episode.episodeNumber))
        else -> MoreFromArt.ShowCover
    }
}
