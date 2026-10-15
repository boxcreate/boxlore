package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * Home hero card presentation: [BLOCK] (solid colour card beside the cover, the default), [COVER]
 * (the cover fills the card and fades into a shade behind the words) or [DUOTONE] (the cover fills
 * the card in two tones). Cover and Duotone take their colours from [HeroCoverShade]. Presentation
 * only; hero ordering, playback and details actions are the same.
 *
 * The stored values predate the current names: Block was "standard" and Cover replaced the
 * "classic" cards, so saved choices carry over unchanged.
 */
object HeroCardStyle {
    const val BLOCK = "standard"
    const val COVER = "classic"
    const val DUOTONE = "duotone"
    const val PREF_KEY = "home_hero_card_style"

    val entries = listOf(BLOCK, COVER, DUOTONE)

    fun sanitize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries } ?: BLOCK

    /** Cover and Duotone draw the artwork across the whole card, so they take [HeroCoverShade] colours. */
    fun usesArtworkColors(style: String?): Boolean = sanitize(style) != BLOCK
}
