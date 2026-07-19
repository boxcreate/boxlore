package cx.aswin.boxlore.core.data.analytics

import com.posthog.PostHog

internal object PlaybackAnalyticsTracks {
    fun trackPlaybackStarted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        startPositionSeconds: Float,
        totalDurationSeconds: Float,
        isRepeating: Boolean,
        isSubscribed: Boolean,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "start_position_seconds" to startPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "is_repeating" to isRepeating,
            "is_subscribed" to isSubscribed
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPoint?.let { props["entry_point"] = it }
        entryPointContext?.let { props.putAll(it) }

        PostHog.capture(event = "playback_started", properties = props)
    }

    fun trackPlaybackPaused(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        durationPlayedSeconds: Float,
        totalBufferedTimeSeconds: Float,
        totalDurationSeconds: Float,
        isCompleted: Boolean,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
        queueSize: Int? = null,
        pauseReason: String = "user_voluntary"
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "duration_played_seconds" to durationPlayedSeconds,
            "total_buffered_time_seconds" to totalBufferedTimeSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "is_completed" to isCompleted,
            "pause_reason" to pauseReason
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPoint?.let { props["entry_point"] = it }
        entryPointContext?.let { props.putAll(it) }
        queueSize?.let { props["queue_size"] = it }

        PostHog.capture(event = "playback_paused", properties = props)
    }

    fun trackPlaybackCompleted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        totalDurationSeconds: Float,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "total_duration_seconds" to totalDurationSeconds
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPoint?.let { props["entry_point"] = it }
        entryPointContext?.let { props.putAll(it) }

        PostHog.capture(event = "playback_completed", properties = props)
    }

    fun trackPlaybackHeartbeat(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        currentPositionSeconds: Float,
        totalDurationSeconds: Float,
        heartbeatPercentage: Int,
        heartbeatType: String
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "current_position_seconds" to currentPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "heartbeat_percentage" to heartbeatPercentage,
            "heartbeat_type" to heartbeatType
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }

        PostHog.capture(event = "playback_heartbeat", properties = props)
    }

    fun trackPlaybackSeeked(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        fromPositionSeconds: Float,
        toPositionSeconds: Float,
        totalDurationSeconds: Float,
        seekSource: String
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "from_position_seconds" to fromPositionSeconds,
            "to_position_seconds" to toPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "seek_source" to seekSource
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }

        PostHog.capture(event = "playback_seeked", properties = props)
    }

    fun trackPlaybackError(
        errorCode: String,
        errorMessage: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null
    ) {
        val props = mutableMapOf<String, Any>(
            "error_code" to errorCode,
            "error_message" to errorMessage
        )
        podcastId?.let { props["podcast_id"] = it }
        episodeId?.let { props["episode_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        PostHog.capture(event = "playback_error", properties = props)
    }

    fun trackExploreScreenViewed(sourceEntryPoint: String? = null) {
        val props = mutableMapOf<String, Any>()
        sourceEntryPoint?.let { props["source_entry_point"] = it }
        PostHog.capture(event = "explore_screen_viewed", properties = props)
    }

    fun trackExploreSearchPerformed(query: String, resultsCount: Int) {
        PostHog.capture(
            event = "explore_search_performed",
            properties = mapOf(
                "search_query" to query,
                "results_count" to resultsCount
            )
        )
    }

    fun trackExploreScreenSession(
        timeSpentSeconds: Float,
        categoriesClickedCount: Int,
        vibesClickedCount: Int,
        searchesPerformedCount: Int,
        podcastsClickedCount: Int,
        maxScrollDepth: Int,
        finalCategoryState: String,
        finalVibeState: String?,
        finalSearchQuery: String?
    ) {
        val props = mutableMapOf<String, Any>(
            "time_spent_seconds" to timeSpentSeconds,
            "categories_clicked_count" to categoriesClickedCount,
            "vibes_clicked_count" to vibesClickedCount,
            "searches_performed_count" to searchesPerformedCount,
            "podcasts_clicked_count" to podcastsClickedCount,
            "max_scroll_depth" to maxScrollDepth,
            "final_category_state" to finalCategoryState
        )
        finalVibeState?.let { props["final_vibe_state"] = it }
        finalSearchQuery?.let { props["final_search_query"] = it }

        PostHog.capture(event = "explore_screen_session", properties = props)
    }
    internal object PlaybackAnalyticsTracks {
    }

}
