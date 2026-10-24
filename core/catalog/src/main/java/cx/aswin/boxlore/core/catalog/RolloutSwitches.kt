package cx.aswin.boxlore.core.catalog

/**
 * Rollout switches for the v2 episode-ID build, passed in by the app from its PostHog flags so this
 * module never reads PostHog itself (`ARCHITECTURE.md`: catalog does not depend on analytics).
 *
 * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback). Nothing in this
 * module reads them yet; the show-page source, v2 discovery, the daily address check and the
 * one-time upgrade repair each read their switch when they land. The defaults are what a build
 * gets without the app's wiring (tests, previews): the feed-first show page off, every job on.
 */
data class RolloutSwitches(
    /**
     * Whether show pages read the show's own feed. False reads the server's list (`/v2/episodes`).
     * The app's value defaults off without a PostHog answer and stays fixed for the process, so a
     * mid-session flag reload never switches a page's episode source.
     *
     * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
     * `feed_first` has been at 100% for 30 days.
     */
    val feedFirst: () -> Boolean = { false },
    /**
     * Whether the one-time upgrade repair of saved feed addresses may run. False pauses it without
     * marking it done. The app's value stays on without a PostHog answer.
     */
    val upgradeRepair: () -> Boolean = { true },
    /**
     * Whether followed shows get the daily address check. False pauses it without marking the day
     * done. The app's value stays on without a PostHog answer.
     */
    val addressCheck: () -> Boolean = { true },
    /**
     * Whether discovery uses the `/v2` routes. False sends discovery back to the v1 routes.
     * The app's value stays on without a PostHog answer.
     *
     * LEGACY(own-ids): fallback to the v1 discovery routes. Remove at sunset step S7
     * (MIGRATION.md §6) once the v1 API client methods are removed.
     */
    val v2Discovery: () -> Boolean = { true },
)
