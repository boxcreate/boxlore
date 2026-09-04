package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.model.Podcast

internal object SubscriptionSmartOrderLogic {
    fun sort(
        podcasts: List<Podcast>,
        scores: Map<String, Double>,
    ): List<Podcast> = podcasts.sortedWith(
        compareByDescending<Podcast> { podcast ->
            scores[podcast.id] ?: podcast.latestEpisode?.publishedDate?.toDouble() ?: 0.0
        }.thenBy { it.title },
    )
}
