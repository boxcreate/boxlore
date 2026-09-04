package cx.aswin.boxlore.core.playback

import android.os.Bundle
import cx.aswin.boxlore.core.model.PlaybackEntryPoint

/**
 * Maps the fine-grained analytics `entry_point` string on a play source bundle to the
 * coarse [PlaybackEntryPoint] used for queue/mixtape policy. The full string is still
 * forwarded as sourceContext for `playback_*` attribution.
 */
internal object PlaybackEntryPointResolve {
    fun fromEntryPointString(raw: String?): PlaybackEntryPoint = when (raw?.trim()?.lowercase()) {
        "home_mixtape" -> PlaybackEntryPoint.HOME_MIXTAPE
        "learn", "learn_history" -> PlaybackEntryPoint.LEARN
        "briefing" -> PlaybackEntryPoint.BRIEFING
        else -> PlaybackEntryPoint.GENERIC
    }

    fun fromSourceContext(entryPointContext: Bundle?): PlaybackEntryPoint = fromEntryPointString(entryPointContext?.getString("entry_point"))
}
