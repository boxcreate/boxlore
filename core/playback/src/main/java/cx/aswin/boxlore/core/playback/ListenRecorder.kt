package cx.aswin.boxlore.core.playback

import android.os.SystemClock
import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.PlaybackListenSummary
import cx.aswin.boxlore.core.analytics.PlaybackListenValues

/** What [PlaybackTelemetrySession] knows about the episode a listen is playing. */
internal data class ListenContext(
    val episodeId: String,
    val podcastId: String?,
    val podcastName: String?,
    val podcastGenre: String?,
    val episodeTitle: String?,
    val entryPoint: String?,
    val entryPointContext: Map<String, Any>?,
    val durationMs: Long,
    val previouslyFinished: Boolean,
)

/**
 * The listen half of [PlaybackTelemetrySession]: the [ListenTally] of the listen in progress, what
 * asked to play and when, the cut-off-listen note ([ListenNoteKeeper]), errors once per cause, and
 * the one `playback_session_ended` per listen.
 */
internal class ListenRecorder(private val playerProvider: () -> Player?) {
    /** Clock for listen accounting: elapsed realtime, replaced in tests. */
    var clock: () -> Long = SystemClock::elapsedRealtime

    /** Where the sound goes for a listen's `output` ([PlaybackListenValues] `OUTPUT_*`); set by the service. */
    var analyticsOutput: () -> String = { PlaybackListenValues.OUTPUT_OTHER }

    /** Whether boxlore's screens are out of sight, for the note; set by the service. */
    var isAppInBackground: () -> Boolean = { false }

    /** Keeps the note of the listen in progress ([ListenNote]); set by the service. */
    var listenNotes: ListenNoteKeeper? = null

    /** The listen in progress, or null between listens. */
    var current: ListenTally? = null
        private set

    /** What controls the current listen; a listen that follows another keeps its controller. */
    var controlledBy: String = PlaybackListenValues.CONTROL_OTHER
        private set

    /** Download or stream once [PlaybackTelemetrySession] has looked it up, else unknown. */
    var playbackMode: String = PlaybackListenValues.MODE_UNKNOWN

    /** When the listener asked to play, for the wait before sound; cleared when a listen starts. */
    private var playRequestedAtMs: Long? = null

    /** What sent the last play request, for the next listen; null when nothing new asked. */
    private var pendingController: String? = null

    /** When a media button named its real sender, which the session then hides behind the notification. */
    private var mediaButtonAtMs: Long? = null

    private val errorsOnce = PlaybackErrorOnce()

    /**
     * The listener asked to play; the next listen's wait before sound counts from here. A request
     * already waiting from the last few seconds keeps its time, so resolving a resumed session
     * (which asks before the player gets its items) counts in the wait.
     */
    fun notePlayRequested() {
        val now = clock()
        val pending = playRequestedAtMs
        if (pending == null || now - pending > REQUEST_GROUP_MS) playRequestedAtMs = now
    }

    /**
     * What sent a play request ([PlaybackListenValues] `CONTROL_*`), for the next listen. Media3
     * hands media buttons (a headset's play key) to the session as its notification controller, so
     * [viaNotificationController] doesn't overwrite what [noteMediaButton] just recorded.
     */
    fun noteController(kind: String, viaNotificationController: Boolean = false,) {
        val button = mediaButtonAtMs
        if (viaNotificationController && button != null && clock() - button <= REQUEST_GROUP_MS) return
        pendingController = kind
    }

    /** A media button arrived from [kind]; the play it causes reaches the session as the notification. */
    fun noteMediaButton(kind: String) {
        pendingController = kind
        mediaButtonAtMs = clock()
    }

    /** Opens a listen: its ID, its controller and the wait before sound. */
    fun begin(): ListenTally {
        val now = clock()
        val startWaitMs = playRequestedAtMs?.let { now - it }?.takeIf { it in 0L..MAX_START_WAIT_MS }
        playRequestedAtMs = null
        pendingController?.let { controlledBy = it }
        pendingController = null
        mediaButtonAtMs = null
        playbackMode = PlaybackListenValues.MODE_UNKNOWN
        errorsOnce.reset()
        return ListenTally(
            listenId =
                java.util.UUID
                    .randomUUID()
                    .toString(),
            startedAtMs = now,
            startPositionMs = playerProvider()?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            startWaitMs = startWaitMs,
        ).also { current = it }
    }

    /** Download or stream for [listenId], when it's still the listen in progress. */
    fun resolvedPlaybackMode(listenId: String, mode: String,) {
        if (current?.listenId == listenId) playbackMode = mode
    }

    /** Writes the note of the listen just opened. */
    fun started(context: ListenContext) {
        val listen = current ?: return
        listenNotes?.started(clock(), note(listen, context))
    }

    /** Counts audio heard since the last sample, and refreshes the note when it's due. */
    fun sample(player: Player, context: () -> ListenContext?,) {
        val listen = current ?: return
        val now = clock()
        listen.samplePosition(now, player.currentPosition, player.isPlaying, player.playbackParameters.speed)
        listenNotes?.progressed(now) { context()?.let { note(listen, it) } }
    }

    /**
     * The player jumped from [fromMs] to [toMs] (a seek, a skip or a new item): audio up to [fromMs]
     * counts for analytics, the jump doesn't. [userSeek] also counts it as one of the listener's
     * seeks; [sameEpisode] false means the player moved on to another episode.
     */
    fun jump(fromMs: Long, toMs: Long, userSeek: Boolean, sameEpisode: Boolean = true,) {
        val listen = current ?: return
        val now = clock()
        listen.edge(now, fromMs, playerProvider()?.playbackParameters?.speed ?: 1f)
        if (userSeek) listen.countSeek(fromMs, toMs)
        if (!sameEpisode) listen.leaveEpisode(fromMs)
        listen.moveTo(now, toMs)
    }

    /**
     * The last sample of a listen that's ending. The local count samples exactly as it always has;
     * when playback already stopped, analytics also counts the audio since the last sample, unless
     * the player has moved on to another episode.
     */
    fun close(player: Player) {
        val listen = current ?: return
        val now = clock()
        val speed = player.playbackParameters.speed
        if (!player.isPlaying && listen.leftEpisodeAtMs == null) listen.edge(now, player.currentPosition, speed)
        listen.samplePosition(now, player.currentPosition, player.isPlaying, speed)
        listen.finish(now)
    }

    /** True the first time [cause] fails for [episodeId] since the last listen started. */
    fun isFirstError(episodeId: String?, cause: String,): Boolean = errorsOnce.firstTime(episodeId, cause)

    /** Sends the listen's one record and clears its note; nothing when no listen is open. */
    fun end(context: ListenContext, endReason: String, pauseReason: String?, isCompleted: Boolean, queueSize: Int,) {
        val listen = current ?: return
        current = null
        listenNotes?.ended()
        val player = playerProvider()
        val output = runCatching(analyticsOutput).getOrDefault(PlaybackListenValues.OUTPUT_OTHER)
        AnalyticsHelper.trackPlaybackSessionEnded(
            PlaybackListenSummary(
                listenId = listen.listenId,
                episodeId = context.episodeId,
                podcastId = context.podcastId,
                podcastName = context.podcastName,
                podcastGenre = context.podcastGenre,
                episodeTitle = context.episodeTitle,
                entryPoint = context.entryPoint,
                entryPointContext = context.entryPointContext,
                endReason = endReason,
                pauseReason = pauseReason.takeIf { endReason == PlaybackListenValues.END_PAUSED },
                heardMs = listen.heardMs,
                elapsedMs = listen.elapsedMs(clock()),
                startPositionMs = listen.startPositionMs,
                endPositionMs = listen.leftEpisodeAtMs ?: player?.currentPosition?.coerceAtLeast(0L) ?: listen.lastPositionMs,
                durationMs = context.durationMs,
                completed = isCompleted,
                previouslyFinished = context.previouslyFinished,
                // A Cast device streams the episode even when this phone holds a download.
                playbackMode = if (output == PlaybackListenValues.OUTPUT_CAST) PlaybackListenValues.MODE_STREAM else playbackMode,
                speed = player?.playbackParameters?.speed ?: 1f,
                controlledBy = controlledBy,
                output = output,
                seeks = listen.seeks,
                startWaitMs = listen.startWaitMs,
                bufferingCount = listen.bufferingCount,
                bufferingMs = listen.bufferingMs,
                queueSize = queueSize,
            ),
        )
    }

    private fun note(listen: ListenTally, context: ListenContext,) =
        ListenNote(
            listenId = listen.listenId,
            episodeId = context.episodeId,
            podcastId = context.podcastId,
            entryPoint = context.entryPoint,
            playbackMode = playbackMode,
            controlledBy = controlledBy,
            heardMs = listen.heardMs,
            elapsedMs = listen.elapsedMs(clock()),
            inBackground = runCatching(isAppInBackground).getOrDefault(false),
        )

    private companion object {
        /** A play request older than this isn't what started the listen, so it gives no wait before sound. */
        const val MAX_START_WAIT_MS = 120_000L

        /** Requests this close together belong to one press (resumption, a media button and its play). */
        const val REQUEST_GROUP_MS = 5_000L
    }
}
