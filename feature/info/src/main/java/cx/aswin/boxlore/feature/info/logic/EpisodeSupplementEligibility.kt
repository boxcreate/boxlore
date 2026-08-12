package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast

/**
 * Whether Podcast Info may offer a one-shot RSS feed supplement for missing PI episodes.
 * Never applies to RSS-owned library shows.
 */
object EpisodeSupplementEligibility {
    fun canLoadMissingEpisodes(podcast: Podcast): Boolean =
        !podcast.isRss &&
            runCatching {
                cx.aswin.boxlore.core.rss.RssIdGenerator.validateAndNormalizeFeedUrl(
                    podcast.feedUrl.orEmpty(),
                )
                true
            }.getOrDefault(false)
}
