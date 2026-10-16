# `:core:analytics`

## Purpose

Owns analytics event capture and non-fatal error reporting for Boxlore. The module wraps PostHog behind application interfaces so callers do not depend on the SDK directly. It does not initialize PostHog, own UI, own repositories, or persist data beyond analytics first-launch preferences.

## Public API

- `trackHomeHeroStyleSelected` emits `home_hero_style_selected` when a Settings pick changes the Home hero look (style, colors, one-value combo, what changed, the previous combo); re-picking the current look sends nothing. `syncHomeHeroStylePersonProperties` keeps person properties `home_hero_card_style`, `home_hero_colors` and `home_hero_style_combo` on the current look, default included; it sends once per process and on each change, so activity recreation on rotation, fold or resize does not resend, and `resetIdentity` resends it to the new anonymous person. Both report the names in Settings (`block`, `cover`, …), not the stored keys, with colors `none` for Block in the app theme (its look before it had a colour choice, so the breakdown continues) and `block_artwork` / `block_dark` once a Block listener picks those (`AppearanceAnalyticsTracks`, `HomeHeroStyleAnalyticsTest`).
- `trackRecommendationSectionToggled` emits `recommendation_section_toggled` when a LeaveMeAlone switch (Settings › Library) flips or the Brief card's Always hide turns the Brief off: `section` (names listeners see: `brief`, `picked_for_you`, `because_you_like`, `time_of_day_picks`, `more_like_this`), `enabled`, `source` (`settings` / `brief_card`) and `sections_off` after the change (`RecommendationsAnalyticsTracks`, `RecommendationsAnalyticsTest`). It sends events only, no person properties.

- `trackCustomThemeEditorOpened` emits once per editor visit. `trackCustomThemeSaved` is called after custom theme preference persistence succeeds. Both use glossary-backed events with only entry point or background style; no color values are captured. Facade and inventory tests cover both emissions.

- `trackApiSessionStatus(ApiSessionStatus)` emits `api_session_status` for API sessions (`ApiSessionAnalyticsTracks`); null properties are left out. One event per `/auth/session` exchange (`kind=exchange`: `trigger`, `success`, `duration_ms`, `attestation`, `limited`, `app_check_attempted`, `failure_reason`, `server_reason`), per first renewal of a token or renewal problem (`kind=renew`, `renew_outcome`) and per Turnstile step-up (`kind=step_up`, `step_up_outcome`). Properties are categories only, never tokens, ids or keys. `trackAppCheckStatus` takes an optional `reason` (a short failure category such as `play_integrity_-9`); App Check now runs only inside a session exchange, so `app_check_status` arrives once per attempt rather than once per launch.

- `Analytics` is the interface used by production code and tests.
- `AnalyticsHelper` is the PostHog-backed production singleton.
- `RecordingAnalytics` is an in-memory test double.
- `ErrorReporter` is the non-fatal error facade; `:app` may install Crashlytics-backed reporting.
- `PendingEntryPoint` bridges playback entry-point context across MediaController boundaries.
- `PlayerSessionAggregator` batches per-episode player interaction events.
- Event names and property expectations are documented in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).
- `AnalyticsGlossary.normalizeEntryPoint` maps legacy/free-form strings onto the glossary sheet without renaming dashboard events (`learn_history` → `learn`; `briefing` stays `briefing`).
- Glossary façades include growth/session (`trackOnboardingAbandoned`, `trackSessionRestorePrompt`), library/player ops (`trackDownloadRequested`, `trackShareContent`, `trackBackupRestoreResult`, `trackQueueModified`, route-only `trackPlaybackRouteChanged` without receiver names), Home mix interactions (`trackPlayMixClicked`, `trackHomeMixModeChanged` with `mix_mode=daily|offline`), Video Spotlight funnel events (`trackVideoSpotlightImpression`, `trackVideoSpotlightPodcastTapped`, `trackVideoSpotlightPlayInitiated`), discovery (`trackExploreSearchPerformed` with `search_mode`, `trackSearchResultTapped`), onboarding (`trackOnboardingStepViewed` with optional `step_index`), and support development (`trackSupportEntryViewed`, `trackSupportPageViewed`, `trackSupportTierToggled`, `trackSupportDonateClicked`, `trackSupportPageLeft`). Support events optionally carry a `SupportWorldContext` (world and assignment source) for the `support-world` experiment, and `supportWorldVariant(knownVariants)` reads that PostHog flag once per visit. It reads without an exposure first and records PostHog's `$feature_flag_called` only for a known variant, so an unloaded, missing or unknown value never counts toward the experiment (`SupportWorldVariantTest`). Feature modules never call PostHog directly.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/analytics/
  Analytics.kt
  AnalyticsHelper.kt                                   # PostHog singleton + track façades
  AnalyticsEmit.kt
  *AnalyticsTracks.kt                                  # domain emit implementations
  SupportAnalyticsTracks.kt                            # Support events + support-world flag read
  ErrorReporter.kt
  PendingEntryPoint.kt
  RecordingAnalytics.kt
  SessionAggregator.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:prefs`.
- Libraries: PostHog Android SDK and AndroidX core.
- Reverse-edge rule: analytics must not depend on catalog, database, network, playback, downloads, designsystem, or feature modules. Callers that emit events declare this module directly.

## Threading / lifecycle

- `AnalyticsHelper` is process-scoped and its methods are callable from any thread; the SDK handles internal dispatch.
- `PendingEntryPoint` and `PlayerSessionAggregator` use synchronized or volatile state for cross-thread access.
- `RecordingAnalytics.flush()` is a no-op suitable for tests.
- PostHog setup runs off the main thread through `PostHogStartup` (it reads package info over binder and caused a startup ANR). Captures, person properties, flushes, resets and app-side `register` calls made before setup finishes are queued in order (bounded at 500) and replayed afterwards; environment super-properties are registered before the replay. Feature flags and `getDistinctId()` read as unset until setup finishes, and callers already fall back when they are. If setup throws (any `Throwable`), PostHog never counts as ready (`hasFailed`): the queue is dropped and later calls are ignored. The NPS remote trigger waits through `runWhenReady` and runs its check back on its own scope.

## Persistence & identity

- SharedPreferences file `boxlore_analytics_prefs` stores the first-launch flag and migrates from `boxcast_analytics_prefs` through `PrefsFileMigrator`.
- Preference key `is_first_launch` must remain stable.
- Event identity must remain aligned with [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Lifecycle / volume (no double-count)

- Opens / backgrounds / install **counts** come from PostHog SDK autocapture (`Application Opened`, `Application Backgrounded`, `Application Installed`) with `captureApplicationLifecycleEvents = true`.
- Do **not** emit glossary `app_open`, `app_background`, or volume `install_attributed`.
- Install channel: `$set_once` person `install_channel` only (`trackInstallChannelAttributed`).
- Deep links: custom `deep_link_opened` (`captureDeepLinks = false`).
- Weekly Pulse KPIs stay on **Application Opened** / **Application Installed**.
- Coverage inventory: [`docs/analytics/glossary_emission_coverage.csv`](../../docs/analytics/glossary_emission_coverage.csv) (`emission` | `sdk_backed` | `person_props_only`).
- Events whose callers are gone leave the glossary with their façades: `library_history_tracking_notice` (the History notice was removed) and `auto_chapters_lifecycle` / `auto_transcript_lifecycle` (the on-device chapters and transcript calls were removed). The unused launch enrichment façade (`subscription_count_bucket`) went with them. `RecordingAnalyticsTest` checks the retired names stay out of the allowlist.

## Testing notes

- Unit tests live under `src/test/java/cx/aswin/boxlore/core/analytics`.
- Use `RecordingAnalytics` when testing classes that accept `Analytics`.
- Use `AnalyticsHelper.installRecordingSink(recorder)` when testing classes or callbacks that interact directly with the singleton `AnalyticsHelper`.
- `GlossaryAllEventsEmissionTest` covers every `emission:` inventory row; `LifecycleSdkMappingTest` / `InstallChannelAttributionTest` assert no dual open/install emits.
- Architecture guards in `:core:testing` enforce CSV ↔ allowlist ↔ inventory parity.

```bash
./gradlew :core:analytics:testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest --tests '*GlossaryCoverageGuardTest'
```
- `PostHogStartupTest` covers ordered replay, immediate calls after setup, the queue bound, successful and failed setup (an `Error` keeps it not ready and drops calls), and the blank distinct id before setup.

## CI relevance

- `unit-tests.yml` runs the module JVM tests.
- The root Kover merged verification includes this module.
- `scripts/ci/check-feature-no-posthog.sh` prevents feature modules from importing or calling PostHog directly.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:core:prefs` README](../prefs/README.md)
