package cx.aswin.boxlore.tv.ui.show

import cx.aswin.boxlore.tv.model.TvEpisode

/** Search inside a show: the episodes whose title or notes hold every word typed, in any case. */
object EpisodeSearch {
    fun filter(episodes: List<TvEpisode>, query: String): List<TvEpisode> {
        val words = query.split(' ', '\t', '\n').filter(String::isNotBlank)
        if (words.isEmpty()) return episodes
        return episodes.filter { episode -> words.all { episode.title.contains(it, ignoreCase = true) || episode.description.contains(it, ignoreCase = true) } }
    }
}
