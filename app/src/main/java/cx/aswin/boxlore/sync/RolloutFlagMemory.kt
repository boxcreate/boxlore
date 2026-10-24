package cx.aswin.boxlore.sync

/**
 * Saves the v2 build's flag answers each time PostHog finishes loading flags, so the next process
 * can use them before PostHog is ready: `feed_first` ([FeedFirstSwitch.remember]) and the saved
 * kill switches ([SavedKillSwitch.remember]). This process's `feed_first` value never changes.
 *
 * Wired to `PostHogAndroidConfig.onFeatureFlags`, which PostHog 6.15 calls after the setup preload,
 * `identify` and `reset`. Explicit `PostHog.reloadFeatureFlags` calls don't reach it, so an answer
 * that changes mid-session is saved at the next of those loads (normally the next launch).
 *
 * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
 *
 * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
 * `feed_first` has been at 100% for 30 days, keeping only the kill switches that still guard jobs.
 */
internal class RolloutFlagMemory(
    private val feedFirst: FeedFirstSwitch,
    private val killSwitches: List<SavedKillSwitch>,
) {
    /**
     * Saves each switch's answer from [read], PostHog's raw flag value without an exposure. PostHog
     * is set up whenever it reports loaded flags, so [read] must not wait for or check
     * `PostHogStartup.isReady`: the callback can run while setup is still returning, before
     * `isReady` flips. A failing read keeps that switch's saved answer.
     */
    fun onFlagsLoaded(read: (flag: String) -> Any?) {
        runCatching { feedFirst.remember(read(FeedFirstSwitch.FLAG)) }
        killSwitches.forEach { switch -> runCatching { switch.remember(read(switch.flag)) } }
    }
}
