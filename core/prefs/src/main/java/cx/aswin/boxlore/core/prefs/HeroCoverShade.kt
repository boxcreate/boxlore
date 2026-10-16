package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * The colours a hero card takes: [DARK] (plain dark), [ARTWORK] (from each cover's own colour, the
 * default) or [THEME] (from the app's accent colour). Cover fades its art into the shade, Duotone
 * redraws the cover in its two tones, and Block fills its card with it. One choice serves every
 * style; Block keeps the app theme until a colour has been picked ([forStyle]).
 */
object HeroCoverShade {
    const val DARK = "dark"
    const val ARTWORK = "artwork"
    const val THEME = "theme"
    const val DEFAULT = ARTWORK
    const val PREF_KEY = "home_hero_cover_shade"

    val entries = listOf(DARK, ARTWORK, THEME)

    /** What the setting reads as before a colour has been picked; [sanitize] treats it as [DEFAULT]. */
    const val UNSET = ""

    fun sanitize(value: String?): String = picked(value) ?: DEFAULT

    /**
     * The colours [style] shows for the stored [value]: the pick, once there is one; otherwise
     * [DEFAULT], except Block, which was always drawn in the app theme and stays so until picked.
     */
    fun forStyle(style: String?, value: String?): String =
        picked(value) ?: if (HeroCardStyle.sanitize(style) == HeroCardStyle.BLOCK) THEME else DEFAULT

    private fun picked(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries }
}
