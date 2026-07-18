package cx.aswin.boxlore.core.domain

import cx.aswin.boxlore.core.model.Podcast

data class RssSubscriptionResult(
    val podcast: Podcast,
    val episodeCount: Int,
    val automaticUpdateChecksSupported: Boolean,
    val potentialPodcastIndexMatch: Podcast? = null,
    val linkedPodcastIndexId: String? = null,
)
