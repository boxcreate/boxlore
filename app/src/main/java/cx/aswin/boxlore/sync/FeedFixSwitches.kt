package cx.aswin.boxlore.sync

import com.posthog.PostHog
import cx.aswin.boxlore.core.analytics.PostHogStartup

/**
 * PostHog off switches for the feed and episode fixes and the v2 episode-ID build's jobs, so one
 * can be turned off without an app update. Each one runs unless its flag is on: with no answer
 * (PostHog not ready, the flag missing or inactive, an error) it stays on. To turn one off, enable
 * its flag for everyone. The v2 build's switches ([UPGRADE_REPAIR_OFF], [ADDRESS_CHECK_OFF],
 * [V2_DISCOVERY_OFF]) are read through [SavedKillSwitch], which uses the last saved answer while
 * PostHog is still starting. The `feed_first` rollout works the other way round: see [FeedFirstSwitch].
 */
internal object FeedFixSwitches {
    /** A dead saved feed address is re-checked against the server's confirmed one (once a day per show). */
    const val ADDRESS_RECHECK_OFF = "feed-fix-address-recheck-off"

    /** Shows whose own feed failed get their latest episode from `/sync`. */
    const val SYNC_FALLBACK_OFF = "feed-fix-sync-fallback-off"

    /** A feed that moved for good (permanent redirects or `itunes:new-feed-url`, same title) gets its new address saved. */
    const val MOVED_FEED_OFF = "feed-fix-moved-feed-off"

    /**
     * Pauses the one-time repair of saved feed addresses after the update, without marking it done.
     * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
     */
    const val UPGRADE_REPAIR_OFF = "upgrade-repair-off"

    /**
     * Pauses the daily address check of followed shows, without marking the day done.
     * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
     */
    const val ADDRESS_CHECK_OFF = "address-check-off"

    /**
     * Sends discovery back to the v1 routes.
     * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
     *
     * LEGACY(own-ids): fallback to the v1 discovery routes. Remove at sunset step S7
     * (MIGRATION.md §6) once the v1 API client methods are removed.
     */
    const val V2_DISCOVERY_OFF = "v2-discovery-off"

    /** True unless [offFlag] is on. [isFlagOn] is the PostHog read, replaced in tests. */
    fun isOn(offFlag: String, isFlagOn: (String) -> Boolean = ::postHogFlagOn,): Boolean =
        !runCatching { isFlagOn(offFlag) }.getOrDefault(false)

    private fun postHogFlagOn(flag: String): Boolean = PostHogStartup.isReady && PostHog.isFeatureEnabled(flag, false)
}
