package cx.aswin.boxlore.core.prefs

import java.util.Locale

/** Presentation only; changing it does not alter playback or the miniplayer. */
object FullPlayerStyle {
    const val STANDARD = "standard"
    const val BUNPOD = "bunpod"
    const val PREF_KEY = "full_player_style"

    fun sanitize(value: String?): String =
        if (value?.trim()?.lowercase(Locale.ROOT) == BUNPOD) BUNPOD else STANDARD
}
