package cx.aswin.boxlore.sync

import com.posthog.PostHog
import cx.aswin.boxlore.core.analytics.PostHogStartup

/**
 * PostHog off switches for the feed and episode fixes, so one can be turned off without an app
 * update. Each fix runs unless its flag is on: with no answer (PostHog not ready, the flag missing
 * or inactive, an error) the fix stays on. To turn one off, enable its flag for everyone.
 */
internal object FeedFixSwitches {
    /** Baselines read up to 1,000 episodes from `/episodes/match` instead of the 100-item page. */
    const val MATCH_BASELINE_OFF = "feed-fix-match-baseline-off"

    /** A dead saved feed address is re-checked against the server's confirmed one (once a day per show). */
    const val ADDRESS_RECHECK_OFF = "feed-fix-address-recheck-off"

    /** Shows whose own feed failed get their latest episode from `/sync`. */
    const val SYNC_FALLBACK_OFF = "feed-fix-sync-fallback-off"

    /** A feed that moved for good (permanent redirects or `itunes:new-feed-url`, same title) gets its new address saved. */
    const val MOVED_FEED_OFF = "feed-fix-moved-feed-off"

    /** True unless [offFlag] is on. [isFlagOn] is the PostHog read, replaced in tests. */
    fun isOn(offFlag: String, isFlagOn: (String) -> Boolean = ::postHogFlagOn,): Boolean =
        !runCatching { isFlagOn(offFlag) }.getOrDefault(false)

    private fun postHogFlagOn(flag: String): Boolean = PostHogStartup.isReady && PostHog.isFeatureEnabled(flag, false)
}
