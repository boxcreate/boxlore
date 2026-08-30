package cx.aswin.boxlore.core.playback

import androidx.media3.common.Player

internal object PlaybackTaskRemovalPolicy {
    data class Plan(
        val persistBeforeStop: Boolean,
        val keepServiceRunning: Boolean,
    )

    fun plan(
        hasPlayer: Boolean,
        playWhenReady: Boolean,
        mediaItemCount: Int,
        playbackState: Int,
    ): Plan {
        if (!hasPlayer) {
            return Plan(
                persistBeforeStop = false,
                keepServiceRunning = false,
            )
        }

        val keepRunning =
            playWhenReady &&
                mediaItemCount > 0 &&
                playbackState != Player.STATE_ENDED &&
                playbackState != Player.STATE_IDLE
        return Plan(
            persistBeforeStop = !keepRunning && mediaItemCount > 0,
            keepServiceRunning = keepRunning,
        )
    }
}
