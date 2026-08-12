package cx.aswin.boxlore.core.catalog

/**
 * RTDB `tracked_podcasts/{podcastIndexId}` payload for the Check New Episodes Action.
 *
 * [feedUrl] is included only for HTTPS publisher feeds on shows the user opted into
 * via Missing episodes? — the checker then polls RSS instead of Podcast Index `max=1`.
 *
 * Live RTDB rules allow only `title`, `imageUrl`, and optional HTTPS `feedUrl`
 * (delete of `feedUrl` is allowed). Extra children are rejected.
 */
object TrackedPodcastRtdbLogic {
    fun httpsFeedUrl(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        return url.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    fun payload(
        title: String,
        imageUrl: String,
        feedUrl: String? = null,
    ): Map<String, String> {
        val data =
            linkedMapOf(
                "title" to title,
                "imageUrl" to imageUrl,
            )
        httpsFeedUrl(feedUrl)?.let { data["feedUrl"] = it }
        return data
    }
}
