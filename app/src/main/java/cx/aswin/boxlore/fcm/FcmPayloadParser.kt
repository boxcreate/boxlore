package cx.aswin.boxlore.fcm

/**
 * Parsed FCM payload data configuration.
 */
data class ParsedFcmNotification(
    val title: String,
    val body: String,
    val type: String,
    val route: String?,
    val imageUrl: String?,
    val sound: String,
    val actionLabel: String,
    val showActionInPush: Boolean,
    val showActionInApp: Boolean,
    val category: String,
    val podcastId: String? = null,
    val episodeId: String? = null,
    val feedUrl: String? = null,
    val guid: String? = null,
    val enclosureUrl: String? = null,
    val presentation: String = "compact",
    val tone: String = "primary",
    val imageStyle: String = "banner",
    val releaseAlert: Boolean? = null,
    val includePlay: Boolean = false,
    val testOnly: Boolean = false,
    val releaseVersionCode: Long = 0,
    val releaseUrl: String? = null,
)

/**
 * Pure Kotlin parser to extract notification configurations from raw FCM map data.
 * Built to be cleanly testable under JVM unit tests.
 */
object FcmPayloadParser {
    fun parse(data: Map<String, String>): ParsedFcmNotification {
        val title = data["title"] ?: "boxlore Update"
        val body = data["body"] ?: "Check out what's new in boxlore!"
        val type = data["type"] ?: "both"
        val route = data["route"]
        val imageUrl = data["image"]
        val sound = data["sound"] ?: "default"
        val actionLabel = data["action_label"] ?: "View"
        val showActionInPush = data["show_action_in_push"] != "false"
        val showActionInApp = data["show_action_in_app"] != "false"
        val category = data["category"] ?: "WHAT'S NEW"

        return ParsedFcmNotification(
            title = title,
            body = body,
            type = type,
            route = route,
            imageUrl = imageUrl,
            sound = sound,
            actionLabel = actionLabel,
            showActionInPush = showActionInPush,
            showActionInApp = showActionInApp,
            category = category,
            podcastId = podcastId(data),
            episodeId = episodeId(data),
            feedUrl = feedUrl(data),
            guid = guid(data),
            enclosureUrl = enclosureUrl(data),
            presentation = data["presentation"]?.takeIf { it == "fullscreen" } ?: "compact",
            tone = data["tone"]?.takeIf { it in setOf("primary", "secondary", "tertiary", "error") } ?: "primary",
            imageStyle = data["image_style"]?.takeIf { it == "cover" } ?: "banner",
            releaseAlert = data["release_alert"]?.toBooleanStrictOrNull(),
            includePlay = data["include_play"] == "true",
            testOnly = data["test_mode"] == "true",
            releaseVersionCode = data["release_version_code"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
            releaseUrl = data["release_url"],
        )
    }

    /** Snake or camel case podcast id from FCM data. */
    fun podcastId(data: Map<String, String>): String? = data["podcast_id"] ?: data["podcastId"]

    /** Snake or camel case episode id from FCM data. */
    fun episodeId(data: Map<String, String>): String? = data["episode_id"] ?: data["episodeId"]

    fun feedUrl(data: Map<String, String>): String? = data["feedUrl"]?.trim()?.takeIf { it.isNotEmpty() }

    fun guid(data: Map<String, String>): String? = data["guid"]?.trim()?.takeIf { it.isNotEmpty() }

    fun enclosureUrl(data: Map<String, String>): String? = (data["enclosureUrl"] ?: data["enclosure_url"])?.trim()?.takeIf { it.isNotEmpty() }
}
