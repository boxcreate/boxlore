package cx.aswin.boxlore.core.analytics

/**
 * `screen_viewed`, the one page view for every screen, tab, sheet and dialog without a view event of
 * its own, and `screen_left`, the summary some screens send when the listener leaves them.
 */
internal object ScreenAnalyticsTracks {
    fun trackScreenViewed(screen: String, previousScreen: String?, properties: Map<String, Any>,) {
        val props = properties.toMutableMap()
        props["screen"] = screen
        previousScreen?.let { props["previous_screen"] = it }
        AnalyticsEmit.event("screen_viewed", props)
    }

    /**
     * A screen's leaving summary (time on it, what was done there). It used to go out under the
     * screen's view event, which counted every visit twice; it's sampled now.
     */
    fun trackScreenLeft(screen: String, properties: Map<String, Any>,) {
        AnalyticsEmit.event("screen_left", properties + ("screen" to screen))
    }
}
