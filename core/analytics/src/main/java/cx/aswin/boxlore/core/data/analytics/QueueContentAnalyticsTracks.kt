package cx.aswin.boxlore.core.data.analytics

import com.posthog.PostHog

internal object QueueContentAnalyticsTracks {
    fun trackSmartQueueRefilled(event: AnalyticsHelper.SmartQueueRefillEvent) {
        val props = mutableMapOf<String, Any>(
            "triggering_episode_id" to event.triggeringEpisodeId,
            "triggering_podcast_genre" to event.triggeringPodcastGenre,
            "refilled_count" to event.refilledCount,
            "recommendation_sources" to event.recommendationSources,
            "refilled_episode_ids" to event.refilledEpisodeIds,
            "used_server_recommendations" to event.usedServerRecommendations
        )
        event.region?.let { props["region"] = it }
        event.sourceCounts.forEach { (source, count) -> props["source_count_$source"] = count }
        PostHog.capture(
            event = "smart_queue_refilled",
            properties = props
        )
    }

    fun trackQueueReordered(
        episodeId: String,
        fromPosition: Int,
        toPosition: Int,
        contextType: String?
    ) {
        PostHog.capture(
            event = "queue_reordered",
            properties = mapOf(
                "episode_id" to episodeId,
                "from_position" to fromPosition,
                "to_position" to toPosition,
                "context_type" to (contextType ?: "unknown")
            )
        )
    }

    fun trackLoreQueueConflictShown(episodeId: String, normalQueueSize: Int) {
        PostHog.capture(
            event = "lore_queue_conflict_shown",
            properties = mapOf(
                "episode_id" to episodeId,
                "normal_queue_size" to normalQueueSize
            )
        )
    }

    /** @param result "start_lore_queue" or "cancelled" */

    fun trackLoreQueueConflictResult(episodeId: String, result: String) {
        PostHog.capture(
            event = "lore_queue_conflict_result",
            properties = mapOf(
                "episode_id" to episodeId,
                "result" to result
            )
        )
    }

    fun trackSmartQueueEpisodeSkipped(
        episodeId: String,
        recommendationSource: String,
        positionInQueue: Int
    ) {
        PostHog.capture(
            event = "smart_queue_episode_skipped",
            properties = mapOf(
                "episode_id" to episodeId,
                "recommendation_source" to recommendationSource,
                "position_in_queue" to positionInQueue
            )
        )
    }

    fun trackOfflineModeEntered() {
        PostHog.capture(
            event = "offline_mode_entered"
        )
    }

    fun trackDiscoverCategoryFiltered(categoryName: String) {
        PostHog.capture(
            event = "discover_category_filtered",
            properties = mapOf("category_name" to categoryName)
        )
    }

    // ── 14. AI Chapters & Transcripts ──────────────────────────────

    fun trackAutoChaptersRequested(episodeId: String, podcastId: String?, audioUrl: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "audio_url" to audioUrl
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_chapters_requested", properties = props)
    }

    fun trackAutoChaptersCompleted(episodeId: String, podcastId: String?, durationSeconds: Float, chaptersCount: Int) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "duration_seconds" to durationSeconds,
            "chapters_count" to chaptersCount
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_chapters_completed", properties = props)
    }

    fun trackAutoChaptersFailed(episodeId: String, podcastId: String?, errorMessage: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "error_message" to errorMessage
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_chapters_failed", properties = props)
    }

    fun trackAutoTranscriptRequested(episodeId: String, podcastId: String?, audioUrl: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "audio_url" to audioUrl
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_transcript_requested", properties = props)
    }

    fun trackAutoTranscriptCompleted(episodeId: String, podcastId: String?, durationSeconds: Float, linesCount: Int) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "duration_seconds" to durationSeconds,
            "lines_count" to linesCount
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_transcript_completed", properties = props)
    }

    fun trackAutoTranscriptFailed(episodeId: String, podcastId: String?, errorMessage: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "error_message" to errorMessage
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_transcript_failed", properties = props)
    }

    fun trackDailyBriefingBannerTapped(region: String, date: String) {
        PostHog.capture(
            event = "daily_briefing_banner_tapped",
            properties = mapOf(
                "region" to region,
                "date" to date
            )
        )
    }

    fun trackDailyBriefingPlayClicked(region: String, date: String, source: String) {
        PostHog.capture(
            event = "daily_briefing_play_clicked",
            properties = mapOf(
                "region" to region,
                "date" to date,
                "source" to source
            )
        )
    }

    fun trackDailyBriefingPauseClicked(region: String, date: String, source: String) {
        PostHog.capture(
            event = "daily_briefing_pause_clicked",
            properties = mapOf(
                "region" to region,
                "date" to date,
                "source" to source
            )
        )
    }

    /**
     * Consolidated event for low-volume briefing interactions (dismiss flow, feedback,
     * sources sheet, story play/pause, chapter navigation). Discriminated by [action],
     * mirroring the settings_interaction / mini_player_interaction pattern.
     */

    fun trackDailyBriefingInteraction(
        action: String,
        region: String,
        date: String,
        extraProps: Map<String, Any> = emptyMap()
    ) {
        val props = mutableMapOf<String, Any>(
            "action" to action,
            "region" to region,
            "date" to date
        )
        props.putAll(extraProps)
        PostHog.capture(event = "daily_briefing_interaction", properties = props)
    }

    fun trackDailyBriefingRegionChanged(previousRegion: String, newRegion: String, date: String) {
        PostHog.capture(
            event = "daily_briefing_region_changed",
            properties = mapOf(
                "previous_region" to previousRegion,
                "new_region" to newRegion,
                "date" to date
            )
        )
    }

    fun trackDailyBriefingRelatedEpisodeClicked(
        region: String,
        date: String,
        chapterIndex: Int,
        episodeId: String,
        episodeTitle: String,
        podcastId: String,
        podcastTitle: String
    ) {
        PostHog.capture(
            event = "daily_briefing_related_episode_clicked",
            properties = mapOf(
                "region" to region,
                "date" to date,
                "chapter_index" to chapterIndex,
                "episode_id" to episodeId,
                "episode_title" to episodeTitle,
                "podcast_id" to podcastId,
                "podcast_title" to podcastTitle
            )
        )
    }

    fun trackDailyBriefingCardImpression(region: String, date: String, playbackStatus: String) {
        PostHog.capture(
            event = "daily_briefing_card_impression",
            properties = mapOf(
                "region" to region,
                "date" to date,
                "playback_status" to playbackStatus
            )
        )
    }

    fun trackDailyBriefingScreenViewed(region: String, date: String, source: String? = null) {
        val props = mutableMapOf<String, Any>(
            "region" to region,
            "date" to date
        )
        source?.let { props["source_entry_point"] = it }
        PostHog.capture(event = "daily_briefing_screen_viewed", properties = props)
    }

    // ── 15. Learn Screen Telemetry ──────────────────────────────────

    fun trackNavTabClicked(tabName: String) {
        PostHog.capture(
            event = "nav_tab_clicked",
            properties = mapOf("tab" to tabName)
        )
    }

    fun trackLearnScreenViewed() {
        PostHog.capture(event = "learn_screen_viewed")
    }

    private fun trackLearnCardEvent(
        eventName: String,
        episodeId: String,
        episodeTitle: String?,
        podcastId: String?,
        podcastTitle: String?
    ) {
        val props = mutableMapOf<String, Any>("episode_id" to episodeId)
        episodeTitle?.let { props["episode_title"] = it }
        podcastId?.let { props["podcast_id"] = it }
        podcastTitle?.let { props["podcast_title"] = it }
        PostHog.capture(event = eventName, properties = props)
    }

    fun trackLearnCardDismissed(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardEvent("learn_card_dismissed", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnCardQueued(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardEvent("learn_card_queued", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnCardInfoClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardEvent("learn_card_info_clicked", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnCardPodcastClicked(podcastId: String?, podcastTitle: String?) {
        val props = mutableMapOf<String, Any>()
        podcastId?.let { props["podcast_id"] = it }
        podcastTitle?.let { props["podcast_title"] = it }
        PostHog.capture(event = "learn_card_podcast_clicked", properties = props)
    }

    fun trackLearnCardPlayClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardEvent("learn_card_play_clicked", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnScreenSession(
        timeSpentSeconds: Float,
        cardsDismissedCount: Int,
        cardsQueuedCount: Int,
        playsCount: Int,
        podcastsClickedCount: Int,
        infosClickedCount: Int
    ) {
        PostHog.capture(
            event = "learn_screen_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "cards_dismissed_count" to cardsDismissedCount,
                "cards_queued_count" to cardsQueuedCount,
                "plays_count" to playsCount,
                "podcasts_clicked_count" to podcastsClickedCount,
                "infos_clicked_count" to infosClickedCount
            )
        )
    }
    internal object QueueContentAnalyticsTracks {
    }

}
