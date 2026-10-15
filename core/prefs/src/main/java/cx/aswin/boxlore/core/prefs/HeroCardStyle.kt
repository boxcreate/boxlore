package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * Home hero card presentation: [BLOCK] (solid colour card beside the cover), [COVER] (the cover
 * fills the card and fades into a shade behind the words; the [DEFAULT]), [TIDE] (the whole cover above
 * a wavy panel with a Play disc), [DUOTONE] (the cover fills the card in two tones), [HALO] (a
 * cookie-shaped cover in a progress ring on a tinted card) or [MARQUEE] (a full-width hero under
 * the top bar, with the next cards' covers beside Play). The
 * styles other than Block take their colours from [HeroCoverShade]. Presentation only; hero
 * ordering, playback and details actions are the same.
 *
 * The stored values predate the current names: Block was "standard" and Cover replaced the
 * "classic" cards, so saved choices carry over unchanged.
 */
object HeroCardStyle {
    const val BLOCK = "standard"
    const val COVER = "classic"
    const val TIDE = "tide"
    const val DUOTONE = "duotone"
    const val HALO = "halo"
    const val MARQUEE = "marquee"
    const val PREF_KEY = "home_hero_card_style"

    /** What listeners who never picked a style see: the full cover, on the Artwork shade ([HeroCoverShade.DEFAULT]). */
    const val DEFAULT = COVER

    val entries = listOf(BLOCK, COVER, TIDE, DUOTONE, HALO, MARQUEE)

    fun sanitize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries } ?: DEFAULT

    /** Every style but Block colours its card from the artwork, so it takes [HeroCoverShade] colours. */
    fun usesArtworkColors(style: String?): Boolean = sanitize(style) != BLOCK
}
