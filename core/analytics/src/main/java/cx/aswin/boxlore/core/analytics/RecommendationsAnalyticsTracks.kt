package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.prefs.DiscoverySection

/**
 * LeaveMeAlone switches (Settings › Library) and the Brief card's Always hide (glossary:
 * `recommendation_section_toggled`). Sections use the names listeners see, not the stored keys.
 */
internal object RecommendationsAnalyticsTracks {
    fun sectionName(section: DiscoverySection): String = when (section) {
        DiscoverySection.DAILY_BRIEF -> "brief"
        DiscoverySection.PICKED_FOR_YOU -> "picked_for_you"
        DiscoverySection.BECAUSE_YOU_LIKE -> "because_you_like"
        DiscoverySection.CURATED_ROWS -> "time_of_day_picks"
        DiscoverySection.MORE_LIKE_THIS -> "more_like_this"
    }

    /** One switch flipped; [sectionsOff] counts the sections off after it, out of every [DiscoverySection]. */
    fun trackSectionToggled(section: DiscoverySection, enabled: Boolean, source: String, sectionsOff: Int) {
        AnalyticsEmit.event(
            "recommendation_section_toggled",
            mapOf(
                "section" to sectionName(section),
                "enabled" to enabled,
                "source" to source,
                "sections_off" to sectionsOff,
            ),
        )
    }
}
