package cx.aswin.boxlore.core.catalog

import java.security.MessageDigest

/**
 * RTDB tracking payload for catalogue IDs and device-owned RSS URL scopes.
 *
 * [feedUrl] is included only for HTTPS publisher feeds on shows the user opted into
 * for episode notifications — the checker then polls RSS instead of Podcast Index `max=1`.
 *
 * Live RTDB rules allow only `title`, `imageUrl`, and optional HTTPS `feedUrl`
 * (delete of `feedUrl` is allowed). Extra children are rejected.
 */
object TrackedPodcastRtdbLogic {
    /** FCM excludes colons; keep numeric topics unchanged and RSS payload ids canonical. */
    fun topic(podcastId: String): String = "new_ep_" + if (podcastId.startsWith("rss:")) "rss_" + podcastId.removePrefix("rss:") else podcastId

    /** RSS delivery follows the exact accepted URL, independently of the preserved show ID. */
    fun topic(podcastId: String, feedUrl: String): String =
        if (podcastId.startsWith("rss:")) "${topic(podcastId)}_${feedFingerprint(feedUrl)}" else topic(podcastId)

    fun feedFingerprint(feedUrl: String): String = MessageDigest.getInstance("SHA-256")
        .digest(feedUrl.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Per-device rows prevent one RSS listener's opt-out from disabling other listeners. */
    fun registrationKey(podcastId: String, deviceRegistrationId: String): String =
        if (podcastId.startsWith("rss:")) "$podcastId~$deviceRegistrationId" else podcastId

    fun registrationKey(podcastId: String, deviceRegistrationId: String, feedUrl: String): String =
        if (podcastId.startsWith("rss:")) "${registrationKey(podcastId, deviceRegistrationId)}~${feedFingerprint(feedUrl)}" else podcastId

    /** Reject a foreign or legacy URL-less RSS payload before presentation or hydration. */
    fun acceptsRelease(podcastId: String, savedFeedUrl: String?, payloadFeedUrl: String?): Boolean =
        !podcastId.startsWith("rss:") ||
            (httpsFeedUrl(savedFeedUrl)?.let { it == httpsFeedUrl(payloadFeedUrl) } == true)

    fun httpsFeedUrl(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        return url.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    /**
     * Catalogue registration attaches a publisher URL after a Room tip is available.
     * RSS registration uses its accepted saved URL directly, including an empty feed.
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
