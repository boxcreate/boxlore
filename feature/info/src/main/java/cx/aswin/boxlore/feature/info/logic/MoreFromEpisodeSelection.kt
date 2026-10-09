package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode

/** The latest five distinct episodes from the show page, excluding the episode being viewed. */
internal fun selectMoreFromEpisodes(episodes: List<Episode>, currentEpisodeId: String): List<Episode> = episodes
    .filter { it.id != currentEpisodeId }
    .sortedByDescending { it.publishedDate }
    .distinctBy { it.id }
    .take(5)
