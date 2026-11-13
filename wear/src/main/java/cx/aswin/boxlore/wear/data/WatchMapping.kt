package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.PodcastFeed
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow

/** Turns the API's answers into what the watch screens show. */
internal object WatchMapping {
    private const val SECONDS_PER_MINUTE = 60
    private const val MILLIS_PER_SECOND = 1_000L

    fun show(feed: SearchFeed, subscribed: Boolean): WatchShow =
        WatchShow(
            id = feed.id.toString(),
            title = feed.title,
            publisher = feed.author.orEmpty(),
            artworkUrl = artwork(feed.artwork, feed.image),
            subscribed = subscribed,
        )

    fun show(feed: PodcastFeed, subscribed: Boolean): WatchShow =
        WatchShow(
            id = feed.id.toString(),
            title = feed.title,
            publisher = feed.author.orEmpty(),
            artworkUrl = artwork(feed.artwork, feed.image),
            subscribed = subscribed,
        )

    /** An episode the watch can play, or null when the API has no audio address for it. */
    fun episode(item: EpisodeItem, show: WatchShow?): WatchEpisode? {
        val audio = item.enclosureUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val showId = item.feedId?.toString() ?: show?.id ?: return null
        return WatchEpisode(
            id = item.id.toString(),
            showId = showId,
            title = item.title,
            showTitle = item.feedTitle?.takeIf { it.isNotBlank() } ?: show?.title.orEmpty(),
            artworkUrl = artwork(item.image, item.feedImage) ?: show?.artworkUrl,
            durationMinutes = minutes(item.duration),
            releasedAtMillis = item.datePublished?.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
            audioUrl = audio,
            catalogKey = item.catalogKey,
        )
    }

    /** An episode read from the show's own feed. Feed items give no show name, so [show] supplies it. */
    fun episode(feed: WatchFeedEpisode, showId: String, show: WatchShow?): WatchEpisode =
        WatchEpisode(
            id = feed.id.toString(),
            showId = showId,
            title = feed.title,
            showTitle = show?.title.orEmpty(),
            artworkUrl = feed.imageUrl ?: show?.artworkUrl,
            durationMinutes = minutes(feed.durationSeconds),
            releasedAtMillis = feed.publishedAtSeconds.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
            audioUrl = feed.audioUrl,
            catalogKey = feed.catalogKey,
        )

    /** Whole minutes, rounded up, so a short episode never reads "0 min". Unknown lengths are 0. */
    fun minutes(durationSeconds: Int?): Int {
        val seconds = durationSeconds ?: return 0
        return if (seconds <= 0) 0 else (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    }

    private fun artwork(first: String?, second: String?): String? =
        first?.takeIf { it.isNotBlank() } ?: second?.takeIf { it.isNotBlank() }
}
