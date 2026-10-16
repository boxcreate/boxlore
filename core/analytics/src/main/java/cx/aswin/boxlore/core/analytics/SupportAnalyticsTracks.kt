package cx.aswin.boxlore.core.analytics

import com.posthog.PostHog

/**
 * Which Support page world a listener saw and how it was assigned. [assignmentSource] is
 * `experiment`, `fallback` or `debug_override`; only `experiment` rows count toward results.
 */
data class SupportWorldContext(
    val supportWorld: String,
    val assignmentSource: String,
)

/**
 * Support boxlore page events and the `support-world` experiment flag.
 *
 * World, assignment and timing properties are optional so the original power-cell page and older
 * callers keep emitting the same base shape.
 */
internal object SupportAnalyticsTracks {
    /** Multivariate PostHog flag whose variants name the Support page world to show. */
    const val SUPPORT_WORLD_FLAG = "support-world"

    /**
     * Reads the assigned world variant once, when the Support page opens. The flag is read without
     * an exposure first; PostHog's `$feature_flag_called` is recorded only when the value is one of
     * [knownVariants], so a missing, unloaded or unknown value never counts toward the experiment.
     */
    fun supportWorldVariant(knownVariants: Set<String>): String? =
        try {
            resolveSupportWorldVariant(knownVariants) { sendExposure ->
                // Before setup the flag is unknown, which shows the default world without exposure.
                if (PostHogStartup.isReady) PostHog.getFeatureFlag(SUPPORT_WORLD_FLAG, null, sendExposure) else null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * [readFlag] returns the flag value and records an exposure when asked to. Returns the
     * normalised variant when it is known, otherwise null without an exposure.
     */
    internal fun resolveSupportWorldVariant(knownVariants: Set<String>, readFlag: (sendExposure: Boolean) -> Any?): String? {
        val variant = (readFlag(false) as? String)?.trim()?.lowercase() ?: return null
        if (variant !in knownVariants) return null
        readFlag(true)
        return variant
    }

    /**
     * The Support boxlore entry was on screen: the Settings hub's top-bar button on phones
     * (`settings_top_bar`) or the tablet category list (`settings_category_list`).
     */
    fun trackSupportEntryViewed(surface: String) {
        AnalyticsEmit.event("support_entry_viewed", mapOf("surface" to surface))
    }

    fun trackSupportPageViewed(
        world: SupportWorldContext? = null,
        entryPoint: String? = null,
        localHour: Int? = null,
        weekday: Int? = null,
    ) {
        val props = buildMap<String, Any> {
            putWorld(world)
            entryPoint?.let { put("entry_point", it) }
            localHour?.let { put("local_hour", it) }
            weekday?.let { put("weekday", it) }
        }
        AnalyticsEmit.event("support_page_viewed", props)
        AnalyticsEmit.event(
            "settings_interaction",
            mapOf(
                "action" to "support_page_viewed",
                "setting_key" to "support_development",
            ),
        )
    }

    fun trackSupportTierToggled(
        tierTitle: String,
        codename: String,
        amount: String,
        isLoreInspect: Boolean = false,
        world: SupportWorldContext? = null,
        tierIndex: Int? = null,
    ) {
        val props = buildMap<String, Any> {
            put("tier_title", tierTitle)
            put("codename", codename)
            put("amount", amount)
            put("is_lore_inspect", isLoreInspect)
            putWorld(world)
            tierIndex?.let { put("tier_index", it) }
        }
        AnalyticsEmit.event("support_tier_toggled", props)
        AnalyticsEmit.event(
            "settings_interaction",
            mapOf(
                "action" to "support_tier_toggled",
                "setting_key" to "support_development",
                "value" to tierTitle,
            ),
        )
    }

    fun trackSupportDonateClicked(
        tierTitle: String,
        codename: String,
        amount: String,
        world: SupportWorldContext? = null,
        tierIndex: Int? = null,
        amountUsd: Double? = null,
        paymentsLive: Boolean? = null,
    ) {
        val props = buildMap<String, Any> {
            put("tier_title", tierTitle)
            put("codename", codename)
            put("amount", amount)
            putWorld(world)
            tierIndex?.let { put("tier_index", it) }
            amountUsd?.let { put("amount_usd", it) }
            paymentsLive?.let { put("payments_live", it) }
        }
        AnalyticsEmit.event("support_donate_clicked", props)
        AnalyticsEmit.event(
            "settings_interaction",
            mapOf(
                "action" to "support_donate_clicked",
                "setting_key" to "support_development",
                "value" to tierTitle,
            ),
        )
    }

    fun trackSupportPageLeft(
        world: SupportWorldContext,
        dwellMs: Long,
        tiersViewed: Int,
        payTapped: Boolean,
    ) {
        AnalyticsEmit.event(
            "support_page_left",
            mapOf(
                "support_world" to world.supportWorld,
                "assignment_source" to world.assignmentSource,
                "dwell_ms" to dwellMs,
                "tiers_viewed" to tiersViewed,
                "pay_tapped" to payTapped,
            ),
        )
    }

    private fun MutableMap<String, Any>.putWorld(world: SupportWorldContext?) {
        world ?: return
        put("support_world", world.supportWorld)
        put("assignment_source", world.assignmentSource)
    }
}
