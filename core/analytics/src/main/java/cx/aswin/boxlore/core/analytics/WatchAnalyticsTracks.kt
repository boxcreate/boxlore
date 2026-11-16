package cx.aswin.boxlore.core.analytics

/**
 * Wear OS usage, as the phone sees it through the watch link. The watch app sends nothing itself,
 * so listening on a watch that never meets this phone is not counted.
 *
 * Called directly, not through [AnalyticsHelper]: that file is past the source-size limit.
 */
object WatchAnalyticsTracks {
    /** boxlore on a watch linked with this phone for the first time. */
    fun trackWatchLinked() = AnalyticsEmit.event("watch_linked", emptyMap())

    /** The phone answered the watch's request to be signed in: `ok`, `signed_out` or `failed`. */
    fun trackWatchSignInAnswered(result: String) = AnalyticsEmit.event("watch_sign_in_answered", mapOf("result" to result))

    /** Send to watch or Remove from watch on an episode page. */
    fun trackWatchEpisodeOrder(download: Boolean, sent: Boolean) =
        AnalyticsEmit.event(
            "watch_episode_order",
            mapOf("action" to if (download) "download" else "remove", "result" to if (sent) "sent" else "out_of_reach"),
        )

    /** Playback moved between the phone and the watch. */
    fun trackWatchPlaybackMoved(toWatch: Boolean) =
        AnalyticsEmit.event("watch_playback_moved", mapOf("direction" to if (toWatch) "to_watch" else "to_phone"))

    /** Listening and follows done on the watch reached the phone's library. */
    fun trackWatchChangesMerged(episodes: Int, follows: Int) =
        AnalyticsEmit.event("watch_changes_merged", mapOf("episode_count" to episodes, "follow_change_count" to follows))
}
