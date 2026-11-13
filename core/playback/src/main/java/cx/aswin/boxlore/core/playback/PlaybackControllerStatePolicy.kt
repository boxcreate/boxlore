package cx.aswin.boxlore.core.playback

/**
 * Merges controller progress without letting an empty or unprepared MediaController erase a
 * valid Room-restored position and duration.
 */
internal object PlaybackControllerStatePolicy {
    data class Snapshot(val hasMedia: Boolean, val positionMs: Long, val bufferedPositionMs: Long, val durationMs: Long,)

    /**
     * Merges progress read once, outside the ticker, only when the player still holds the episode the
     * state names. While the player has moved to its next item and the state still shows the previous
     * episode, that progress belongs to the new item, so it waits for the state to catch up.
     */
    fun mergeProgressForCurrentItem(previous: PlayerState, snapshot: Snapshot, playerEpisodeId: String?): PlayerState =
        if (playerEpisodeId != null && playerEpisodeId == previous.currentEpisode?.id) mergeProgress(previous, snapshot) else previous

    fun mergeProgress(previous: PlayerState, snapshot: Snapshot,): PlayerState {
        if (!snapshot.hasMedia) return previous

        val validDurationMs = snapshot.durationMs.takeIf { it > 0L }
        val hasUsablePosition = snapshot.positionMs > 0L || validDurationMs != null
        val hasUsableBuffer = snapshot.bufferedPositionMs > 0L || validDurationMs != null

        return previous.copy(
            position =
            if (hasUsablePosition) {
                snapshot.positionMs.coerceAtLeast(0L)
            } else {
                previous.position
            },
            bufferedPosition =
            if (hasUsableBuffer) {
                snapshot.bufferedPositionMs.coerceAtLeast(0L)
            } else {
                previous.bufferedPosition
            },
            duration = validDurationMs ?: previous.duration,
        )
    }

    /**
     * [previous] after a seek inside the same episode. The progress ticker only runs while the app
     * is on screen, so a skip made from the notification, headphones, a car or a watch would
     * otherwise leave the old position in the state until the next start or stop. A move to another
     * item is not handled here: the item transition brings its own position.
     */
    fun afterSeek(previous: PlayerState, isSeek: Boolean, sameItem: Boolean, positionMs: Long): PlayerState =
        if (isSeek && sameItem) previous.copy(position = positionMs.coerceAtLeast(0L)) else previous

    fun resolveResumePositionMs(persistedPositionMs: Long?, restoredStatePositionMs: Long,): Long = persistedPositionMs
        ?.coerceAtLeast(0L)
        ?: restoredStatePositionMs.coerceAtLeast(0L)
}
