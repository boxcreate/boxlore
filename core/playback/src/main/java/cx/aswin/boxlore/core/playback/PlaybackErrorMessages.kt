package cx.aswin.boxlore.core.playback

/** Toast copy for playback errors, chosen by what [PlaybackErrorSkipGuard] decided. */
internal object PlaybackErrorMessages {
    fun forAction(action: PlaybackErrorSkipGuard.Action, isRemote: Boolean): String = when {
        action == PlaybackErrorSkipGuard.Action.STOP && isRemote ->
            "These episodes couldn't play on this Cast device. Playback is paused."
        action == PlaybackErrorSkipGuard.Action.STOP ->
            "These episodes couldn't play. Check your connection, then try again."
        isRemote -> "That episode could not play on this Cast device."
        else -> "Stream unavailable, skipping…"
    }
}
