package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade

/**
 * Home hero card style and colours (glossary: `home_hero_style_selected`, plus person properties).
 * Values use the names listeners see in Settings, not the stored keys: Block is stored as
 * "standard" and Cover as "classic". Block draws a solid card, so its colours are always `none`.
 */
internal object AppearanceAnalyticsTracks {
    private const val NO_COLORS = "none"

    /** The look last written to person properties in this process, so window changes don't resend it. */
    @Volatile
    private var lastSyncedProperties: Map<String, Any>? = null

    fun heroStyleName(style: String?): String = when (val sanitized = HeroCardStyle.sanitize(style)) {
        HeroCardStyle.BLOCK -> "block"
        HeroCardStyle.COVER -> "cover"
        else -> sanitized
    }

    /** Block in the app theme (its look before it had a colour choice) keeps reporting no colours, so breakdowns continue. */
    fun heroColorsName(style: String?, shade: String?): String {
        val colors = HeroCoverShade.forStyle(style, shade)
        return if (!HeroCardStyle.usesArtworkColors(style) && colors == HeroCoverShade.THEME) NO_COLORS else colors
    }

    /** One value per card look, e.g. `cover_artwork` or `block`, for single-property breakdowns. */
    fun heroStyleCombo(style: String?, shade: String?): String {
        val colors = heroColorsName(style, shade)
        return if (colors == NO_COLORS) heroStyleName(style) else "${heroStyleName(style)}_$colors"
    }

    /** A pick in Settings › Appearance › Home. Skipped when the look stays the same, e.g. re-tapping the picked tile. */
    fun trackHomeHeroStyleSelected(previousStyle: String?, previousShade: String?, style: String, shade: String,) {
        val previousCombo = heroStyleCombo(previousStyle, previousShade)
        val combo = heroStyleCombo(style, shade)
        if (combo == previousCombo) return
        val changed = if (HeroCardStyle.sanitize(style) != HeroCardStyle.sanitize(previousStyle)) "style" else "colors"
        AnalyticsEmit.event(
            "home_hero_style_selected",
            mapOf(
                "hero_card_style" to heroStyleName(style),
                "hero_colors" to heroColorsName(style, shade),
                "hero_style_combo" to combo,
                "changed" to changed,
                "previous_hero_style_combo" to previousCombo,
            ),
        )
    }

    /** Keeps person properties on the current look, picked or default; sent once per process and on each change. */
    fun syncHomeHeroStylePersonProperties(style: String?, shade: String?) {
        val properties: Map<String, Any> =
            mapOf(
                "home_hero_card_style" to heroStyleName(style),
                "home_hero_colors" to heroColorsName(style, shade),
                "home_hero_style_combo" to heroStyleCombo(style, shade),
            )
        synchronized(this) {
            if (properties == lastSyncedProperties) return
            lastSyncedProperties = properties
        }
        AnalyticsEmit.personSet(properties)
    }

    /** An identity reset starts a new anonymous person, so it gets the current look again. */
    fun resendHomeHeroStyleAfterIdentityReset() {
        lastSyncedProperties?.let(AnalyticsEmit::personSet)
    }

    /** Test helper: forget the last synced look. */
    fun resetSyncedPersonPropertiesForTest() {
        lastSyncedProperties = null
    }
}
