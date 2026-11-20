package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.UiText
import java.util.Locale

internal suspend fun scoreLatestIfNeeded(
    useSmartRank: Boolean,
    podcasts: List<Podcast>,
    history: List<ListeningHistoryEntity>,
    scoreEpisodes: suspend (List<Podcast>, List<ListeningHistoryEntity>) -> Map<String, Double>,
): Map<String, Double> = if (useSmartRank) scoreEpisodes(podcasts, history) else emptyMap()

internal fun sortLatestDisplayPodcasts(
    podcasts: List<Podcast>,
    useSmartRank: Boolean,
    episodeScores: Map<String, Double>,
): List<Podcast> = if (useSmartRank) {
    podcasts.sortedByDescending { episodeScores[it.latestEpisode?.id] ?: 0.0 }
} else {
    podcasts.sortedByDescending { it.latestEpisode!!.publishedDate }
}

internal fun groupLatestByDateHeader(
    podcasts: List<Podcast>,
    useSmartRank: Boolean,
    locale: Locale,
): Map<UiText, List<Podcast>> = if (useSmartRank) {
    emptyMap()
} else {
    podcasts.groupBy { getChronologicalHeader(it.latestEpisode!!.publishedDate, locale) }
}
