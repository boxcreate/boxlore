package cx.aswin.boxlore.core.analytics

import java.time.LocalDate

/**
 * Wear OS usage, as the phone sees it through the watch link. The watch app sends nothing itself,
 * so listening on a watch that never meets this phone is not counted.
 *
 * Called directly, not through [AnalyticsHelper]: that file is past the source-size limit.
 */
object WatchAnalyticsTracks {
    /** boxlore on a watch linked with this phone for the first time. */
    fun trackWatchLinked() = AnalyticsEmit.event("watch_linked", emptyMap())

    /** The phone answered the watch's request to be signed in: `ok`, `signed_out`, `check_needed` or `failed`. */
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

    /** One day of listening and follows done on the watch that reached the phone's library, added up by [WatchListeningSummary]. */
    fun trackWatchChangesMerged(day: WatchListeningSummary.Day) =
        AnalyticsEmit.event(
            "watch_changes_merged",
            mapOf(
                "day" to LocalDate.ofEpochDay(day.epochDay).toString(),
                "merge_count" to day.merges,
                "episode_count" to day.episodes,
                "follow_change_count" to day.follows,
            ),
        )
}
