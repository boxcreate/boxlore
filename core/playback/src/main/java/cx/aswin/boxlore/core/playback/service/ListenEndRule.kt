package cx.aswin.boxlore.core.playback.service

import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.PlaybackListenValues

/**
 * Whether a player that isn't making sound ends the listen, and why. Rebuffering (still wanting to
 * play, nothing suppressing it) keeps the listen open; a pause, a suppression such as a call, the
 * end of the media or a stop ends it. The service asks when sound stops, and again when the listener
 * pauses or stops while it was already rebuffering, which sends no second "stopped playing" callback.
 */
internal object ListenEndRule {
    /** The end reason ([PlaybackListenValues] `END_*`), or null while the listen should stay open. */
    fun reason(playWhenReady: Boolean, playbackState: Int, suppressed: Boolean,): String? = when {
        playbackState == Player.STATE_ENDED -> PlaybackListenValues.END_FINISHED
        playbackState == Player.STATE_IDLE -> PlaybackListenValues.END_STOPPED
        !playWhenReady || suppressed -> PlaybackListenValues.END_PAUSED
        else -> null
    }

    fun reason(player: Player): String? =
        reason(
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            suppressed = player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        )
}
