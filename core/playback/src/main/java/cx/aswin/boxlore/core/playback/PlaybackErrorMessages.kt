package cx.aswin.boxlore.core.playback

import androidx.annotation.StringRes

/** Toast copy for playback errors, chosen by what [PlaybackErrorSkipGuard] decided. */
internal object PlaybackErrorMessages {
    @StringRes
    fun forAction(action: PlaybackErrorSkipGuard.Action, isRemote: Boolean): Int = when {
        action == PlaybackErrorSkipGuard.Action.STOP && isRemote -> R.string.playback_error_stop_cast
        action == PlaybackErrorSkipGuard.Action.STOP -> R.string.playback_error_stop
        // Paused: the bad episode is dropped but nothing starts, so say the next one is ready rather than "skipping".
        action == PlaybackErrorSkipGuard.Action.SKIP_PAUSED && isRemote -> R.string.playback_error_skip_paused_cast
        action == PlaybackErrorSkipGuard.Action.SKIP_PAUSED -> R.string.playback_error_skip_paused
        isRemote -> R.string.playback_error_skip_cast
        else -> R.string.playback_error_skip
    }
}
