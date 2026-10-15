package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * Home hero card presentation: [STANDARD] (solid colour card beside the cover, the default) or
 * [CLASSIC] (the earlier cards, with the cover filling the card behind the text). Presentation only;
 * hero ordering, playback and details actions are the same.
 */
object HeroCardStyle {
    const val STANDARD = "standard"
    const val CLASSIC = "classic"
    const val PREF_KEY = "home_hero_card_style"

    fun sanitize(value: String?): String =
        if (value?.trim()?.lowercase(Locale.ROOT) == CLASSIC) CLASSIC else STANDARD
}
