package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.core.model.EpisodeRecipeId

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
    /** boxlore's own ID of the episode a new-episode push announces, when the push carries it (P4 PR 13). */
    val recipeId: Long? = null,
    /** The trimmed GUID, else enclosure URL, of that episode, when the push carries it (P4 PR 13). */
    val catalogKey: String? = null,
    val presentation: String = "compact",
    val tone: String = "primary",
    val imageStyle: String = "banner",
    val releaseAlert: Boolean? = null,
    val includePlay: Boolean = false,
    val testOnly: Boolean = false,
    val releaseVersionCode: Long = 0,
    val releaseUrl: String? = null,
)

/** The listener-language text an announcement push falls back to when it leaves its own out. */
data class PushTextFallbacks(val title: String, val body: String, val actionLabel: String) {
    companion object {
        fun from(context: android.content.Context): PushTextFallbacks = PushTextFallbacks(
            title = context.getString(cx.aswin.boxlore.R.string.app_push_fallback_title),
            body = context.getString(cx.aswin.boxlore.R.string.app_push_fallback_body),
            actionLabel = context.getString(cx.aswin.boxlore.R.string.app_push_fallback_action),
        )
    }
}

/**
 * Pure Kotlin parser to extract notification configurations from raw FCM map data.
 * Built to be cleanly testable under JVM unit tests.
 */
object FcmPayloadParser {
    /** Reads [data]; a title, text or button label the push leaves out comes from [fallbacks]. */
    fun parse(data: Map<String, String>, fallbacks: PushTextFallbacks): ParsedFcmNotification {
        val title = data["title"] ?: fallbacks.title
        val body = data["body"] ?: fallbacks.body
        val type = data["type"] ?: "both"
        val route = data["route"]
        val imageUrl = data["image"]
        val sound = data["sound"] ?: "default"
        val actionLabel = data["action_label"] ?: fallbacks.actionLabel
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
            recipeId = recipeId(data),
            catalogKey = catalogKey(data),
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

    /** Snake or camel case podcast id from FCM data, else the show ID newer pushes send ([showId]). */
    fun podcastId(data: Map<String, String>): String? = data["podcast_id"] ?: data["podcastId"] ?: showId(data)

    /**
     * The Podcast Index show of a new-episode push (`showId`), only as a canonical show ID below
     * 2^52; anything else is ignored.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, notifications).
     */
    fun showId(data: Map<String, String>): String? = (data["showId"] ?: data["show_id"])?.trim()
        ?.let(EpisodeRecipeId::showIdOrNull)
        ?.takeIf { it < EpisodeRecipeId.MIN }
        ?.toString()

    /**
     * The recipe ID of a new-episode push's episode (`recipeId`), only when it is in the recipe range.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, notifications).
     */
    fun recipeId(data: Map<String, String>): Long? = (data["recipeId"] ?: data["recipe_id"])?.trim()
        ?.let(EpisodeRecipeId::showIdOrNull)
        ?.takeIf(EpisodeRecipeId::isRecipeId)

    /**
     * The catalog key of a new-episode push's episode (`catalogKey`), trimmed like every catalog key.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, notifications).
     */
    fun catalogKey(data: Map<String, String>): String? = EpisodeRecipeId.trimKey(data["catalogKey"] ?: data["catalog_key"])

    /** Snake or camel case episode id from FCM data. */
    fun episodeId(data: Map<String, String>): String? = data["episode_id"] ?: data["episodeId"]

    fun feedUrl(data: Map<String, String>): String? = data["feedUrl"]?.trim()?.takeIf { it.isNotEmpty() }

    fun guid(data: Map<String, String>): String? = data["guid"]?.trim()?.takeIf { it.isNotEmpty() }

    fun enclosureUrl(data: Map<String, String>): String? = (data["enclosureUrl"] ?: data["enclosure_url"])?.trim()?.takeIf { it.isNotEmpty() }
}
