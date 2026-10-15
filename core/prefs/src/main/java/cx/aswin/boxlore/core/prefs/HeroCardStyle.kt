package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * Home hero card presentation: [BLOCK] (solid colour card beside the cover, the default), [COVER]
 * (the cover fills the card and fades into a shade behind the words), [DUOTONE] (the cover fills
 * the card in two tones) or [HALO] (a cookie-shaped cover in a progress ring on a tinted card). The
 * styles other than Block take their colours from [HeroCoverShade]. Presentation only; hero
 * ordering, playback and details actions are the same.
 *
 * The stored values predate the current names: Block was "standard" and Cover replaced the
 * "classic" cards, so saved choices carry over unchanged.
 */
object HeroCardStyle {
    const val BLOCK = "standard"
    const val COVER = "classic"
    const val DUOTONE = "duotone"
    const val HALO = "halo"
    const val PREF_KEY = "home_hero_card_style"

    val entries = listOf(BLOCK, COVER, DUOTONE, HALO)

    fun sanitize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries } ?: BLOCK

    /** Every style but Block colours its card from the artwork, so it takes [HeroCoverShade] colours. */
    fun usesArtworkColors(style: String?): Boolean = sanitize(style) != BLOCK
}
