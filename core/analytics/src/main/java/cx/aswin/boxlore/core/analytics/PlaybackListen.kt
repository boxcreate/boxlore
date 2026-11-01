package cx.aswin.boxlore.core.analytics

/** Values the one-record-per-listen events carry: [PlaybackListenSummary] and [PlaybackInterruptedListen]. */
object PlaybackListenValues {
    /** `end_reason`: the listener paused, including Android pausing for a call or unplugged headphones. */
    const val END_PAUSED = "paused"

    /** `end_reason`: the episode reached its end, or was marked played while playing. */
    const val END_FINISHED = "finished"

    /** `end_reason`: another episode took over, by skip or auto-advance. */
    const val END_SWITCHED = "switched_episode"

    /** `end_reason`: playback was stopped or the idle player was shut down. */
    const val END_STOPPED = "stopped"

    /** `end_reason`: the playback service closed while playing (app swiped away, system shutdown). */
    const val END_APP_CLOSED = "app_closed"

    /** `controlled_by`: boxlore's own screens and widgets. */
    const val CONTROL_APP = "app"

    /** `controlled_by`: Android Auto or a car running Android Automotive. */
    const val CONTROL_CAR = "car"

    /** `controlled_by`: the media notification or the system media controls. */
    const val CONTROL_NOTIFICATION = "notification"

    /** `controlled_by`: anything else, such as headset buttons, a watch or the Assistant. */
    const val CONTROL_OTHER = "other"

    const val OUTPUT_SPEAKER = "speaker"
    const val OUTPUT_WIRED = "wired"
    const val OUTPUT_BLUETOOTH = "bluetooth"
    const val OUTPUT_CAST = "cast"
    const val OUTPUT_OTHER = "other"

    const val MODE_DOWNLOAD = "download"
    const val MODE_STREAM = "stream"

    /** A listen with at least this much audio heard counts as listening (`qualified`). */
    const val QUALIFIED_LISTEN_SECONDS = 60

    /** Buffering at least this long is a stall, sent on its own as `playback_buffering`. */
    const val STALL_MS = 5_000L

    /** `client_surface` for a listen: the car when the car controls it, else the phone. */
    fun clientSurface(controlledBy: String): String = if (controlledBy == CONTROL_CAR) "car" else "phone"
}

/** What `playback_started` adds for a listen: its ID, to join with `playback_session_ended`, and its controller. */
data class ListenStart(val listenId: String, val controlledBy: String,)

/** Seeks inside one listen: how many each way and how far they jumped. */
data class ListenSeeks(val forward: Int = 0, val back: Int = 0, val forwardMs: Long = 0L, val backMs: Long = 0L,)

/** One listen, from sound starting until [endReason]: what `playback_session_ended` sends. */
data class PlaybackListenSummary(
    val listenId: String,
    val episodeId: String,
    val podcastId: String?,
    val podcastName: String?,
    val podcastGenre: String?,
    val episodeTitle: String?,
    val entryPoint: String?,
    val entryPointContext: Map<String, Any>?,
    val endReason: String,
    /** Why Android or the listener paused, when [endReason] is paused. */
    val pauseReason: String?,
    /** Audio heard, from the player's position rather than the clock. */
    val heardMs: Long,
    /** Time the listen took, from sound starting to its end. */
    val elapsedMs: Long,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val previouslyFinished: Boolean,
    val playbackMode: String,
    val speed: Float,
    val controlledBy: String,
    val output: String,
    val seeks: ListenSeeks,
    /** Time between asking to play and sound starting; null when the listen followed another. */
    val startWaitMs: Long?,
    val bufferingCount: Int,
    val bufferingMs: Long,
    val queueSize: Int?,
)

/** A listen the previous run left unfinished because Android ended the app: `playback_interrupted`. */
data class PlaybackInterruptedListen(
    val listenId: String,
    val episodeId: String,
    val podcastId: String?,
    val entryPoint: String?,
    val playbackMode: String,
    val controlledBy: String,
    /** Audio heard up to the last note. */
    val heardMs: Long,
    /** Time from sound starting to the last note. */
    val elapsedMs: Long,
    /** Whether boxlore's screens were out of sight at the last note. */
    val inBackground: Boolean,
)
