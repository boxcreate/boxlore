package cx.aswin.boxlore.core.analytics

/** The one-record-per-listen events: `playback_session_ended` and `playback_interrupted`. */
internal object ListenAnalyticsTracks {
    fun trackPlaybackSessionEnded(listen: PlaybackListenSummary) {
        val heardSeconds = listen.heardMs / MS_PER_SECOND
        val props =
            mutableMapOf<String, Any>(
                "listen_id" to listen.listenId,
                "episode_id" to listen.episodeId,
                "entry_point" to AnalyticsGlossary.normalizeEntryPoint(listen.entryPoint),
                "end_reason" to listen.endReason,
                "heard_seconds" to heardSeconds,
                "elapsed_seconds" to listen.elapsedMs / MS_PER_SECOND,
                "qualified" to (heardSeconds >= PlaybackListenValues.QUALIFIED_LISTEN_SECONDS),
                "completed" to listen.completed,
                "previously_finished" to listen.previouslyFinished,
                "playback_mode" to listen.playbackMode,
                "client_surface" to PlaybackListenValues.clientSurface(listen.controlledBy),
                "controlled_by" to listen.controlledBy,
                "output" to listen.output,
                "speed" to listen.speed,
                "seeks_forward" to listen.seeks.forward,
                "seeks_back" to listen.seeks.back,
                "skipped_forward_seconds" to listen.seeks.forwardMs / MS_PER_SECOND,
                "skipped_back_seconds" to listen.seeks.backMs / MS_PER_SECOND,
                "buffering_count" to listen.bufferingCount,
                "buffering_seconds" to listen.bufferingMs / MS_PER_SECOND,
                "start_position_seconds" to listen.startPositionMs / MS_PER_SECOND,
                "end_position_seconds" to listen.endPositionMs / MS_PER_SECOND,
                "duration_seconds" to listen.durationMs / MS_PER_SECOND,
            )
        listen.startWaitMs?.let { props["start_wait_ms"] = it }
        listen.pauseReason?.let { props["pause_reason"] = it }
        listen.queueSize?.let { props["queue_size"] = it }
        listen.podcastId?.let { props["podcast_id"] = it }
        listen.podcastName?.let { props["podcast_name"] = it }
        listen.podcastGenre?.let { props["podcast_genre"] = it }
        listen.episodeTitle?.let { props["episode_title"] = it }
        listen.entryPointContext?.forEach { (key, value) -> if (key !in props) props[key] = value }

        AnalyticsEmit.event("playback_session_ended", props)
    }

    fun trackPlaybackInterrupted(listen: PlaybackInterruptedListen) {
        val props =
            mutableMapOf<String, Any>(
                "listen_id" to listen.listenId,
                "episode_id" to listen.episodeId,
                "entry_point" to AnalyticsGlossary.normalizeEntryPoint(listen.entryPoint),
                "heard_seconds" to listen.heardMs / MS_PER_SECOND,
                "elapsed_seconds" to listen.elapsedMs / MS_PER_SECOND,
                "in_background" to listen.inBackground,
                "playback_mode" to listen.playbackMode,
                "controlled_by" to listen.controlledBy,
                "client_surface" to PlaybackListenValues.clientSurface(listen.controlledBy),
            )
        listen.podcastId?.let { props["podcast_id"] = it }

        AnalyticsEmit.event("playback_interrupted", props)
    }

    private const val MS_PER_SECOND = 1000f
}
