package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.common.Player

/**
 * How the service treats a position jump. Only a seek can be the listener's own; trimmed silence,
 * auto-advance and the like move the playhead without counting as a seek or as heard audio.
 */
internal object PlaybackDiscontinuityPolicy {
    fun isSeek(reason: Int): Boolean = reason == Player.DISCONTINUITY_REASON_SEEK

    /** Trim silence reports a jump at every skipped pause; those aren't worth a log line each. */
    fun isWorthLogging(reason: Int): Boolean = reason != Player.DISCONTINUITY_REASON_SILENCE_SKIP

    /** Logs a jump, except the many small ones trim silence makes. */
    fun log(reason: Int, fromMs: Long, toMs: Long) {
        if (isWorthLogging(reason)) Log.d("BoxCastPlayer", "onPositionDiscontinuity: reason=$reason, from $fromMs to $toMs")
    }
}
