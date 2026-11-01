package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.analytics.ListenSeeks

/**
 * Accounting for one listen, from sound starting until it ends, on a clock the caller passes in
 * (`SystemClock.elapsedRealtime` in the service, a fake in tests). [ListenRecorder] feeds it player
 * samples, jumps, seeks and buffering, and reads it when the listen ends.
 *
 * - Heard audio comes from the player's position, not the clock: an advance counts only up to what
 *   the speed allows in the time that passed (plus slack), so seeks, jumps and paused time never count.
 * - [sampledMs] is what the once-a-second samples alone count, exactly as the local listening record
 *   and ranking always have. [heardMs] adds the edges that only analytics counts: the audio between
 *   the last sample and a jump, and between the last sample and the end.
 * - Seeks count only the ones the listener made; the caller leaves out intro and outro skips.
 * - Buffering counts each spell from its start to its end while the listen lasts.
 */
internal class ListenTally(
    val listenId: String,
    private val startedAtMs: Long,
    val startPositionMs: Long,
    /** Time between asking to play and sound starting; null when the listen followed another. */
    val startWaitMs: Long?,
) {
    /** Audio heard per the samples alone, in media time (the local listening record's count). */
    var sampledMs: Long = 0L
        private set

    /** Wall time the sampled audio took at its speed ([ListeningSessionRecordLogic.listenedWallMs]). */
    var sampledWallMs: Long = 0L
        private set

    /** Audio heard, edges included: what `playback_session_ended` reports. */
    val heardMs: Long get() = sampledMs + edgeMs

    var seeks = ListenSeeks()
        private set

    var bufferingCount: Int = 0
        private set

    var bufferingMs: Long = 0L
        private set

    var lastPositionMs: Long = startPositionMs
        private set

    /** Where the listen left its own episode when the player moved to another one, else null. */
    var leftEpisodeAtMs: Long? = null
        private set

    private var edgeMs: Long = 0L
    private var lastSampleMs: Long = startedAtMs
    private var bufferingSinceMs: Long? = null

    /** Counts the position advance since the last sample when it's natural playback. */
    fun samplePosition(nowMs: Long, positionMs: Long, isPlaying: Boolean, speed: Float,) {
        val position = positionMs.coerceAtLeast(0L)
        if (isPlaying) {
            naturalAdvance(nowMs, position, speed)?.let { advance ->
                sampledMs += advance
                sampledWallMs += ListeningSessionRecordLogic.listenedWallMs(advance, speed)
            }
        }
        lastPositionMs = position
        lastSampleMs = nowMs
    }

    /**
     * Counts, for analytics only, the natural advance up to [positionMs] since the last sample: the
     * audio heard right before a jump, or before the listen ended. Paused or buffering time adds
     * nothing because the position doesn't move.
     */
    fun edge(nowMs: Long, positionMs: Long, speed: Float,) {
        val position = positionMs.coerceAtLeast(0L)
        naturalAdvance(nowMs, position, speed)?.let { edgeMs += it }
        lastPositionMs = position
        lastSampleMs = nowMs
    }

    /** A jump the listener didn't hear (any discontinuity): the next sample starts from [positionMs]. */
    fun moveTo(nowMs: Long, positionMs: Long,) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        lastSampleMs = nowMs
    }

    /** The player moved on to another episode while the listen was at [positionMs]. */
    fun leaveEpisode(positionMs: Long) {
        if (leftEpisodeAtMs == null) leftEpisodeAtMs = positionMs.coerceAtLeast(0L)
    }

    /** Counts a seek the listener made, from [fromMs] to [toMs]; [moveTo] handles the jump itself. */
    fun countSeek(fromMs: Long, toMs: Long,) {
        val jump = toMs - fromMs
        seeks =
            when {
                jump > 0 -> seeks.copy(forward = seeks.forward + 1, forwardMs = seeks.forwardMs + jump)
                jump < 0 -> seeks.copy(back = seeks.back + 1, backMs = seeks.backMs - jump)
                else -> seeks
            }
    }

    fun bufferingStarted(nowMs: Long) {
        if (bufferingSinceMs == null) bufferingSinceMs = nowMs
    }

    /** Ends a buffering spell; returns how long it lasted, or null when none was running. */
    fun bufferingEnded(nowMs: Long): Long? {
        val since = bufferingSinceMs ?: return null
        bufferingSinceMs = null
        val spell = (nowMs - since).coerceAtLeast(0L)
        bufferingCount += 1
        bufferingMs += spell
        return spell
    }

    /** Time the listen has taken so far. */
    fun elapsedMs(nowMs: Long): Long = (nowMs - startedAtMs).coerceAtLeast(0L)

    /** Closes a buffering spell still running when the listen ends. */
    fun finish(nowMs: Long) {
        bufferingEnded(nowMs)
    }

    private fun naturalAdvance(nowMs: Long, position: Long, speed: Float,): Long? {
        val advance = position - lastPositionMs
        val elapsed = (nowMs - lastSampleMs).coerceAtLeast(0L)
        val maximumNaturalAdvance = (elapsed * speed * NATURAL_ADVANCE_SLACK).toLong() + NATURAL_ADVANCE_GRACE_MS
        return advance.takeIf { it in 0..maximumNaturalAdvance }
    }

    private companion object {
        const val NATURAL_ADVANCE_SLACK = 1.5f
        const val NATURAL_ADVANCE_GRACE_MS = 1_000L
    }
}
