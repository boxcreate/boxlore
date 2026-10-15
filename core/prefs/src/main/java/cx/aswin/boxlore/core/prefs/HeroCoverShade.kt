package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * The shade a Cover hero card's artwork fades into behind its words: [DARK] (a plain dark shade),
 * [ARTWORK] (a deep shade of each cover's own colour, the default) or [THEME] (a deep shade of the
 * app's accent colour).
 */
object HeroCoverShade {
    const val DARK = "dark"
    const val ARTWORK = "artwork"
    const val THEME = "theme"
    const val DEFAULT = ARTWORK
    const val PREF_KEY = "home_hero_cover_shade"

    val entries = listOf(DARK, ARTWORK, THEME)

    fun sanitize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries } ?: DEFAULT
}
