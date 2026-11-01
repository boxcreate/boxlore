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

    /** Download or stream, once [PlaybackTelemetrySession] has looked it up. */
    var playbackMode: String = PlaybackListenValues.MODE_STREAM

    /** When the listener last asked to play, for the wait before sound; cleared when a listen starts. */
    private var playRequestedAtMs: Long? = null

    /** What sent the last play request, for the next listen; null when nothing new asked. */
    private var pendingController: String? = null

    private val errorsOnce = PlaybackErrorOnce()

    /** The listener asked to play; the next listen's wait before sound counts from here. */
    fun notePlayRequested() {
        playRequestedAtMs = clock()
    }

    /** What sent a play request ([PlaybackListenValues] `CONTROL_*`), for the next listen. */
    fun noteController(kind: String) {
        pendingController = kind
    }

    /** Opens a listen: its ID, its controller and the wait before sound. */
    fun begin(): ListenTally {
        val now = clock()
        val startWaitMs = playRequestedAtMs?.let { now - it }?.takeIf { it in 0L..MAX_START_WAIT_MS }
        playRequestedAtMs = null
        pendingController?.let { controlledBy = it }
        pendingController = null
        playbackMode = PlaybackListenValues.MODE_STREAM
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
     * counts, the jump doesn't. [userSeek] also counts it as one of the listener's seeks.
     */
    fun jump(fromMs: Long, toMs: Long, userSeek: Boolean,) {
        val listen = current ?: return
        val now = clock()
        playerProvider()?.let { player -> listen.samplePosition(now, fromMs, player.isPlaying, player.playbackParameters.speed) }
        if (userSeek) listen.countSeek(fromMs, toMs)
        listen.moveTo(now, toMs)
    }

    /** True the first time [cause] fails for [episodeId] since the last listen started. */
    fun isFirstError(episodeId: String?, cause: String,): Boolean = errorsOnce.firstTime(episodeId, cause)

    /** Sends the listen's one record and clears its note; nothing when no listen is open. */
    fun end(context: ListenContext, endReason: String, pauseReason: String?, isCompleted: Boolean, queueSize: Int,) {
        val listen = current ?: return
        current = null
        listenNotes?.ended()
        val player = playerProvider()
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
                endPositionMs = player?.currentPosition?.coerceAtLeast(0L) ?: listen.lastPositionMs,
                durationMs = context.durationMs,
                completed = isCompleted,
                previouslyFinished = context.previouslyFinished,
                playbackMode = playbackMode,
                speed = player?.playbackParameters?.speed ?: 1f,
                controlledBy = controlledBy,
                output = runCatching(analyticsOutput).getOrDefault(PlaybackListenValues.OUTPUT_OTHER),
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
    }
}
