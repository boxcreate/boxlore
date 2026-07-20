package cx.aswin.boxlore.core.playback

/**
 * Keeps player UI transport controls aligned with the live Media3 player / prefs
 * when [PlayerState] is cleared or a new queue starts.
 *
 * Clearing to a blank [PlayerState] used to reset [PlayerState.playbackSpeed] to 1×
 * while ExoPlayer kept the persisted rate — the UI then showed 1× while audio ran at 2×.
 */
object PlaybackControlSync {
    /**
     * Empty session state after dismiss/clear that preserves speed and seek sizes.
     */
    fun clearedStatePreservingControls(
        previous: PlayerState,
        controllerSpeed: Float?,
    ): PlayerState =
        PlayerState(
            playbackSpeed = resolvePlaybackSpeed(controllerSpeed, previous.playbackSpeed),
            seekBackwardMs = previous.seekBackwardMs,
            seekForwardMs = previous.seekForwardMs,
        )

    /**
     * Prefer the live controller rate, then in-memory state, then 1×.
     */
    fun resolvePlaybackSpeed(
        controllerSpeed: Float?,
        stateSpeed: Float,
        fallback: Float = 1.0f,
    ): Float {
        val fromController = controllerSpeed?.takeIf { it.isFinite() && it > 0f }
        if (fromController != null) return fromController
        return stateSpeed.takeIf { it.isFinite() && it > 0f } ?: fallback
    }

    /**
     * Clamp user-requested / persisted speed to a finite positive value in the supported range.
     */
    fun sanitizePlaybackSpeed(
        speed: Float,
        fallback: Float = 1.0f,
        min: Float = 0.5f,
        max: Float = 3.0f,
    ): Float {
        if (!speed.isFinite() || speed <= 0f) return fallback
        return speed.coerceIn(min, max)
    }

    /**
     * Media3 [Player.Listener.onPlaybackParametersChanged] → UI state.
     * No-op when the reported speed already matches [PlayerState.playbackSpeed].
     */
    fun applyPlaybackParametersSpeed(
        state: PlayerState,
        speed: Float,
    ): PlayerState {
        if (state.playbackSpeed == speed) return state
        return state.copy(playbackSpeed = speed)
    }

    /**
     * Optimistic [playQueue] / restore copy: keep episode metadata updates but sync speed
     * from the live controller when available.
     */
    fun withSyncedPlaybackSpeed(
        state: PlayerState,
        controllerSpeed: Float?,
    ): PlayerState =
        state.copy(
            playbackSpeed = resolvePlaybackSpeed(controllerSpeed, state.playbackSpeed),
        )
}
