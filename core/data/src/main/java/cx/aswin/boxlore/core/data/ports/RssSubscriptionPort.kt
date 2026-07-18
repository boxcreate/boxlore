package cx.aswin.boxlore.core.data.ports

import cx.aswin.boxlore.core.data.RssSubscriptionResult
import cx.aswin.boxlore.core.model.Podcast

/**
 * Narrow RSS subscribe / Podcast Index link seam for Settings (and tests).
 *
 * Production: [cx.aswin.boxlore.core.data.RssPodcastRepository].
 */
interface RssSubscriptionPort {
    suspend fun addSubscription(rawUrl: String): RssSubscriptionResult

    suspend fun confirmPodcastIndexLink(
        rssPodcastId: String,
        podcastIndexId: String,
    ): Podcast
}
