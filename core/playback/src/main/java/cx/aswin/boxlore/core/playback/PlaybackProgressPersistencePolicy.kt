package cx.aswin.boxlore.core.playback

/**
 * Pure conflict-resolution rules for durable playback progress.
 *
 * Sequence ordering prevents an older asynchronous write from replacing newer Player truth while
 * still allowing intentional restarts and backward seeks.
 */
internal object PlaybackProgressPersistencePolicy {
    const val MAX_MISSING_SEED_ATTEMPTS = 2

    fun shouldApplySnapshot(incomingSequence: Long, lastAppliedSequence: Long?,): Boolean = lastAppliedSequence == null || incomingSequence > lastAppliedSequence

    fun shouldAttemptMissingSeed(attemptCount: Int): Boolean = attemptCount < MAX_MISSING_SEED_ATTEMPTS

    fun resolvePositionMs(incomingPositionMs: Long): Long = incomingPositionMs.coerceAtLeast(0L)

    fun resolveDurationMs(existingDurationMs: Long, incomingDurationMs: Long,): Long = incomingDurationMs.takeIf { it > 0L } ?: existingDurationMs

    fun shouldUpdateLastPlayedAt(
        hasBeenPlayingFor10s: Boolean,
        allowZeroPosition: Boolean,
        isCompleted: Boolean,
        activePlaybackEnded: Boolean = false,
    ): Boolean = hasBeenPlayingFor10s || allowZeroPosition || isCompleted || activePlaybackEnded
}
