package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * Home hero card presentation: [BLOCK] (solid colour card beside the cover, the default) or
 * [COVER] (the cover fills the card and fades into a shade behind the words, see [HeroCoverShade]).
 * Presentation only; hero ordering, playback and details actions are the same.
 *
 * The stored values predate the current names: Block was "standard" and Cover replaced the
 * "classic" cards, so saved choices carry over unchanged.
 */
object HeroCardStyle {
    const val BLOCK = "standard"
    const val COVER = "classic"
    const val PREF_KEY = "home_hero_card_style"

    fun sanitize(value: String?): String =
        if (value?.trim()?.lowercase(Locale.ROOT) == COVER) COVER else BLOCK
}
