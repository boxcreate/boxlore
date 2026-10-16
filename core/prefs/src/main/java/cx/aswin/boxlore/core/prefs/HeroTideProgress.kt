package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * The shape of the Resume progress ring around a Tide hero card's Play disc: [WAVY] (the Material
 * wavy ring that ripples while the episode plays, the default) or [CIRCLE] (a plain round ring).
 */
object HeroTideProgress {
    const val WAVY = "wavy"
    const val CIRCLE = "circle"
    const val DEFAULT = WAVY
    const val PREF_KEY = "home_hero_tide_progress"

    val entries = listOf(WAVY, CIRCLE)

    fun sanitize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries } ?: DEFAULT
}
