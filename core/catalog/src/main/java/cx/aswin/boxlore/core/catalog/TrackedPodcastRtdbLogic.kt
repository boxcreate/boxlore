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
    /** FCM excludes colons; keep numeric topics unchanged and RSS payload ids canonical. */
    fun topic(podcastId: String): String = "new_ep_" + if (podcastId.startsWith("rss:")) "rss_" + podcastId.removePrefix("rss:") else podcastId

    /** Per-device rows prevent one RSS listener's opt-out from disabling other listeners. */
    fun registrationKey(podcastId: String, deviceRegistrationId: String): String =
        if (podcastId.startsWith("rss:")) "$podcastId~$deviceRegistrationId" else podcastId

    fun httpsFeedUrl(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        return url.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    /**
     * Attach RTDB `feedUrl` only when we have a Room tip the Action can seed
     * `lastRssKey` from. No tip → stay on PI `lastEpisodeId`.
     */
    fun attachableFeedUrl(feedUrl: String?, latestEpisodeId: String?,): String? {
        if (latestEpisodeId.isNullOrBlank()) return null
        return httpsFeedUrl(feedUrl)
    }

    fun payload(title: String, imageUrl: String, feedUrl: String? = null,): Map<String, String> {
        val data =
            linkedMapOf(
                "title" to title,
                "imageUrl" to imageUrl,
            )
        httpsFeedUrl(feedUrl)?.let { data["feedUrl"] = it }
        return data
    }
}
