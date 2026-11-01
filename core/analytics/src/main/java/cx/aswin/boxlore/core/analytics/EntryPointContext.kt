package cx.aswin.boxlore.core.analytics

/**
 * The entry-point details a play may carry onto playback events beside `entry_point`. Media items
 * hold other extras too (artwork links, Android Auto's own keys, the stream address, show IDs);
 * those never reach analytics, and a detail never replaces a field the event already set.
 */
object EntryPointContext {
    val ALLOWED_KEYS: Set<String> =
        setOf(
            "source_entry_point",
            "screen",
            "previous_screen",
            "ep_layout_type",
            "ep_carousel_position",
            "ep_is_subscribed",
            "curated_vibe_id",
            "curated_carousel_position",
            "recommendation_id",
        )

    fun isAllowed(key: String): Boolean = key in ALLOWED_KEYS

    /** Adds the allowed details of [context] that [props] doesn't already have. */
    internal fun addTo(props: MutableMap<String, Any>, context: Map<String, Any>?) {
        context?.forEach { (key, value) -> if (isAllowed(key) && key !in props) props[key] = value }
    }
}
