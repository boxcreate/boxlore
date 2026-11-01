# `:core:analytics`

## Purpose

Owns analytics event capture and non-fatal error reporting for Boxlore. The module wraps PostHog behind application interfaces so callers do not depend on the SDK directly. It does not initialize PostHog, own UI, own repositories, or persist data beyond analytics first-launch preferences.

## Public API

- **Event levels** (`AnalyticsPolicy`, `AnalyticsLevel`). Every event is always on, sampled, or off.
  - Defaults are in `AnalyticsPolicy.DEFAULT_LEVELS`; anything unlisted is always on. Off: `playback_heartbeat`, `playback_paused` (replaced by `playback_session_ended`), `adaptive_ranking_status`, `android_auto_browse`, `session_restore_prompt`, and the SDK's `Application Backgrounded`. Sampled: `playback_seeked`.
  - **Remote switch:** the PostHog flag `analytics-policy-v1` changes levels with no release. Its JSON payload is `{"sample_percent": 10, "events": {"<event>": "always|sampled|off"}, "versions": {"<app version>": {...}}}`; a version entry applies on top for that version only. Targeting a flag variant at one person changes events for that person alone. A missing flag or a payload that isn't an object means the defaults. `AnalyticsHelper.refreshAnalyticsPolicy` applies it whenever PostHog reports loaded flags (its configured callback at startup, `identify` and `reset`, and `AnalyticsHelper.reloadFeatureFlags`, which every explicit reload uses because PostHog doesn't run that callback for them) and remembers it with the app version, and `initPolicy` restores it at the next launch before PostHog is set up, only on that same version, so a version entry never carries over an update.
  - **Sampling:** an install is in the sample when its bucket (0–99, picked once) is below `sample_percent` (default 10). Sampled events carry `sample_percent`, so charts can scale them back up.
  - **Where levels apply:** `AnalyticsEmit` applies them to app events. `AnalyticsBeforeSend`, added to the PostHog config by `:app`, applies them to everything PostHog captures, SDK lifecycle events included, and stamps this build's environment tags (`is_internal`, `app_environment`), replacing any an event carries: setup events such as `Application Updated` otherwise inherit the tags the previous build saved. It runs at capture, before the SDK's upload queue, so events already queued (an offline stretch) aren't checked again. An app event that waited for PostHog setup keeps the sample decision and `sample_percent` it was emitted with, even if the switch changed meanwhile, since charts weigh it by that share; it is dropped only if the event is now off (`admitCaptured`). The identity events `$identify`, `$create_alias`, `$set` and `$groupidentify` (`ALWAYS_SENT`) ignore the switch, because the SDK updates its identity state before an event is checked. `Application Installed` is captured during SDK setup, before `register` runs, so it used to arrive untagged.
  - **Glossary columns:** `docs/analytics/event_glossary.csv` mirrors the defaults in its `level` column, and gives each event's measured `monthly_per_install`.

- `trackHomeHeroStyleSelected` emits `home_hero_style_selected` when a Settings pick changes the Home hero look (style, colors, one-value combo, what changed, the previous combo); re-picking the current look sends nothing. `syncHomeHeroStylePersonProperties` keeps person properties `home_hero_card_style`, `home_hero_colors` and `home_hero_style_combo` on the current look, default included; it sends once per process and on each change, so activity recreation on rotation, fold or resize does not resend, and `resetIdentity` resends it to the new anonymous person. Both report the names in Settings (`block`, `cover`, …), not the stored keys, with colors `none` for Block in the app theme (its look before it had a colour choice, so the breakdown continues) and `block_artwork` / `block_dark` once a Block listener picks those (`AppearanceAnalyticsTracks`, `HomeHeroStyleAnalyticsTest`).
- `trackRecommendationSectionToggled` emits `recommendation_section_toggled` when a LeaveMeAlone switch (Settings › Library) flips or the Brief card's Always hide turns the Brief off: `section` (names listeners see: `brief`, `picked_for_you`, `because_you_like`, `time_of_day_picks`, `more_like_this`), `enabled`, `source` (`settings` / `brief_card`) and `sections_off` after the change (`RecommendationsAnalyticsTracks`, `RecommendationsAnalyticsTest`). It sends events only, no person properties.

- `trackCustomThemeEditorOpened` emits once per editor visit. `trackCustomThemeSaved` is called after custom theme preference persistence succeeds. Both use glossary-backed events with only entry point or background style; no color values are captured. Facade and inventory tests cover both emissions.

- `trackApiSessionStatus(ApiSessionStatus)` emits `api_session_status` for API sessions (`ApiSessionAnalyticsTracks`); null properties are left out. One event per `/auth/session` exchange (`kind=exchange`: `trigger`, `success`, `duration_ms`, `attestation`, `limited`, `app_check_attempted`, `failure_reason`, `server_reason`), per first renewal of a token or renewal problem (`kind=renew`, `renew_outcome`) and per Turnstile step-up (`kind=step_up`, `step_up_outcome`). Properties are categories only, never tokens, ids or keys. `trackAppCheckStatus` takes an optional `reason` (a short failure category such as `play_integrity_-9`); App Check now runs only inside a session exchange, so `app_check_status` arrives once per attempt rather than once per launch.

- `trackFeedFetchResult(FeedFetchReport)` emits `feed_fetch_result`: once per show-page read (`context=show_page`, `show_id`, `source`, `result`, `completeness` on successful reads, `http_status` only on `http_error` and `gone`) and once per background pass and source as a summary (`context=background_pass`, `result` `ok`/`mixed`/`failed`, `shows_count`, `ok_count`, `not_modified_count`, `failed_count`, and `namespace_mismatch_count` only when the server reported another episode namespace for shows read in the pass; a pass that read nothing sends nothing). `trackFeedUrlCorrected(FeedUrlCorrection)` emits `feed_url_corrected` once per saved-address swap (`show_id`, `reason`, `step`). Neither carries an address: a show ID that looks like one is left out, and `feed_url_corrected` is then dropped (`FeedAnalyticsTracks`, `FeedAnalyticsTracksTest`). Catalog code reaches them through `:core:domain`'s `FeedTelemetryPort`, which `:app` wires.
- `recipe_id` beside `episode_id` (P4 PR 18, D11): `AnalyticsEmit` fills `recipe_id` on every event in `AnalyticsGlossary.EPISODE_ID_EVENTS` and `recipe_ids` on episode surfaces of `home_surface_impression` (`EpisodeRecipeIds`). The value is the episode ID itself when it is in the recipe range, else one already known (`rememberEpisodeRecipeIds`, or found earlier); unknown IDs are looked up in the background through the lookup `:app` installs (`installEpisodeRecipeIdLookup`, trusted keyed aliases only), so an event is never held back and the next event of that episode carries it. A miss isn't asked again for 10 minutes; caches are bounded. `trackOwnIdsUpgradeCompleted(UpgradeRepairReport)` emits `own_ids_upgrade_completed` once per upgraded install: `outcome` (`done` / `gave_up`), `shows_count`, `shows_gave_up_count`, `addresses_swapped_count`, `notifications_reregistered_count`, `runs_count`, `elapsed_hours` (tenths); counts only, no show IDs or addresses. Tests: `EpisodeRecipeIdsTest`, `GlossaryAllEventsEmissionTest`.
- **Screens** (`ScreenViews`, `ScreenAnalyticsTracks`):
  - `ScreenViews` holds the current screen and the one before. Each showing screen holds a `Registration` (`TrackScreenView` in `:core:designsystem`): the newest one still showing is current; one that changes screen (the next step, another card) replaces itself, so the step it showed is the one before; one that goes away hands current back to the newest screen still showing.
  - `trackScreenViewed(screen, previousScreen, properties)` sends `screen_viewed` for every screen, tab, sheet and dialog without a view event of its own.
  - Leaving summaries (`track*Session` for the show, episode, Explore, Lore and Library pages) go out as `screen_left` with `screen`, the same names those pages register (`podcast_detail`, `episode_detail`, `explore`, `learn`, `library`, `subscriptions`, `liked_episodes`, `downloads`, `listening_history`), sampled (`DEFAULT_LEVELS`). They used to share the view event's name, which counted each visit twice; the view events now count visits only.
  - `PendingEntryPoint.noteRequest` remembers the screens showing when a play is asked for (the playback API calls it while the app's screens are showing); `withScreens` adds them as `screen` and `previous_screen` for 10 seconds, so a play published after preparing still names the screen it was asked from. Without a noted request (a widget, the car, voice) nothing is added.
- **One record per listen** (`PlaybackListen.kt`, `ListenAnalyticsTracks`):
  - `trackPlaybackSessionEnded(PlaybackListenSummary)` sends `playback_session_ended` once per listen: `listen_id`, heard and elapsed seconds, `qualified` (60 s or more heard), why it ended, download or stream, speed, `controlled_by`, `output`, seeks each way with seconds skipped, the wait before sound, buffering totals, positions and duration. Entry-point context never overwrites these fields.
  - `trackPlaybackInterrupted(PlaybackInterruptedListen)` sends `playback_interrupted` for a listen the last run left unfinished.
  - `trackPlaybackStarted` takes the listen (`ListenStart`: `listen_id`, `controlled_by`), adds `previously_finished` beside `is_resume` (which has always meant finished before), and sends the real `playback_mode` and `speed`.
  - `trackPlaybackError` takes the listen ID and the cause; with a cause it also goes to PostHog error tracking through `AnalyticsEmit.exception` (`exceptionSink`; a recording sink records it as `$exception`). `AnalyticsRedaction` removes links and signed tokens from `error_message` and from every message in the error chain; error tracking gets `RedactedException` copies that name the original type and keep the stack, never the player's own exception.
  - `PlaybackListenValues` holds the values (`END_*`, `CONTROL_*`, `OUTPUT_*`, `MODE_*`), the qualified-listen threshold and the 5-second stall threshold; `:core:playback` fills them.
- `AnalyticsGlossary.EPISODE_ID_EVENTS` lists the 27 events that carry `episode_id`; each also declares an optional `recipe_id` (`RECIPE_ID_PROPERTY`), and `home_surface_impression` declares `recipe_ids` (`RECIPE_IDS_PROPERTY`). The façades do not send them yet. `GlossaryRecipeIdTest` keeps the set, the CSV and the Markdown glossary in step.
- `Analytics` is the interface used by production code and tests.
- `AnalyticsHelper` is the PostHog-backed production singleton.
- `RecordingAnalytics` is an in-memory test double.
- `ErrorReporter` is the non-fatal error facade; `:app` may install Crashlytics-backed reporting.
- `PendingEntryPoint` bridges playback entry-point context across MediaController boundaries.
- `PlayerSessionAggregator` batches per-episode player interaction events.
- Event names and property expectations are documented in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).
- `AnalyticsGlossary.normalizeEntryPoint` maps legacy/free-form strings onto the glossary sheet without renaming dashboard events (`learn_history` → `learn`; `briefing` stays `briefing`).
- Glossary façades include growth/session (`trackOnboardingAbandoned`, `trackSessionRestorePrompt`), library/player ops (`trackDownloadRequested`, `trackShareContent`, `trackBackupRestoreResult`, `trackQueueModified`, route-only `trackPlaybackRouteChanged` without receiver names), Home mix interactions (`trackPlayMixClicked`, `trackHomeMixModeChanged` with `mix_mode=daily|offline`), Video Spotlight funnel events (`trackVideoSpotlightImpression`, `trackVideoSpotlightPodcastTapped`, `trackVideoSpotlightPlayInitiated`), discovery (`trackExploreSearchPerformed` with `search_mode`, `trackSearchResultTapped`), onboarding (`trackOnboardingStepViewed` with optional `step_index`; `trackOnboardingSimilarChoice` → `onboarding_similar_choice` with `path`, `choice` and `seed_count` for the search/import choice before suggestions), and support development (`trackSupportEntryViewed`, `trackSupportPageViewed`, `trackSupportTierToggled`, `trackSupportDonateClicked`, `trackSupportPageLeft`). Support events optionally carry a `SupportWorldContext` (world and assignment source) for the `support-world` experiment, and `supportWorldVariant(knownVariants)` reads that PostHog flag once per visit. It reads without an exposure first and records PostHog's `$feature_flag_called` only for a known variant, so an unloaded, missing or unknown value never counts toward the experiment (`SupportWorldVariantTest`). Feature modules never call PostHog directly.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/analytics/
  Analytics.kt
  AnalyticsHelper.kt                                   # PostHog singleton + track façades
  AnalyticsEmit.kt
  AnalyticsPolicy.kt                                   # event levels, sampling, analytics-policy-v1 switch
  AnalyticsBeforeSend.kt                               # PostHog beforeSend: levels + environment tags on every event
  *AnalyticsTracks.kt                                  # domain emit implementations
  SupportAnalyticsTracks.kt                            # Support events + support-world flag read
  FeedAnalyticsTracks.kt                               # feed_fetch_result / feed_url_corrected
  ErrorReporter.kt
  EntryPointContext.kt                                 # the entry details allowed onto playback events
  AnnouncementRef.kt                                   # which in-app alert an announcement event is about
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
- Event levels keep `analytics_sample_bucket` (this install's sample bucket, 0–99; a PostHog reset keeps it, clearing app data picks a new one) and `analytics_policy` (the last remote switch, `<app version>|<percent>|<event>=<level>,…`; a value from another version, or the older form without one, is ignored) in the same file. `AnalyticsPolicy.Store` is the seam: the app's `SharedPreferences` store in `AnalyticsHelper`, a fake in tests. Keep both keys stable.
- Event identity must remain aligned with [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Lifecycle / volume (no double-count)

- Opens / backgrounds / install **counts** come from PostHog SDK autocapture (`Application Opened`, `Application Backgrounded`, `Application Installed`) with `captureApplicationLifecycleEvents = true`.
- Do **not** emit glossary `app_open`, `app_background`, or volume `install_attributed`.
- Install channel: set-once person `install_channel` only (`trackInstallChannelAttributed`). Set-once properties go through `AnalyticsEmit.personSetOnce` (the SDK's set-once slot); nested as `$set_once` under `personSet` they landed as one person property named `$set_once`, which hid `install_channel` from every person before this fix.
- Setup route: `onboarding_method` and `user_intent` follow the latest route (`$set`); `first_onboarding_method`, `first_user_intent` and `initial_podcasts_subscribed` are set once, so a later import from Home doesn't overwrite how the listener first set up.
- Feedback: `feedback_submitted` is only the NPS survey's trigger (`nps_eligible`, `nps_manual`), because the survey shows on any of them. A Home review prompt is `engagement_prompt_shown`, a hand-off to Play `store_review_opened` (`in_app_review` or `store_page`), and an accepted feedback form `feedback_sent` (categories only, never the text or email).
- Explore: the For You rail being shown is `explore_surface_impression`; `explore_recommendation_tapped` is taps only and now carries `recipe_id` beside `episode_id` (29 episode events). Home's category chips are `home_surface_tapped` (`category_filter`).
- Playback events take only `EntryPointContext.ALLOWED_KEYS` from a play's context, and none replaces a field the event set; media item extras (artwork links, Android Auto keys, the stream address) never reach analytics.
- Echo's chat sends what the listener typed capped at 500 characters, and never the reply text.
- Deep links: custom `deep_link_opened` (`captureDeepLinks = false`).
- Weekly Pulse KPIs stay on **Application Opened** / **Application Installed**.
- Coverage inventory: [`docs/analytics/glossary_emission_coverage.csv`](../../docs/analytics/glossary_emission_coverage.csv) (`emission` | `sdk_backed` | `person_props_only`).
- Events whose callers are gone leave the glossary with their façades: `library_history_tracking_notice` (the History notice was removed) and `auto_chapters_lifecycle` / `auto_transcript_lifecycle` (the on-device chapters and transcript calls were removed). The unused launch enrichment façade (`subscription_count_bucket`) went with them. `RecordingAnalyticsTest` checks the retired names stay out of the allowlist.

## Testing notes

- Unit tests live under `src/test/java/cx/aswin/boxlore/core/analytics`.
- Use `RecordingAnalytics` when testing classes that accept `Analytics`.
- Use `AnalyticsHelper.installRecordingSink(recorder)` when testing classes or callbacks that interact directly with the singleton `AnalyticsHelper`. It records every event whatever its level, so wiring tests still see events that are off; pass `applyLevels = true` to test the levels.
- `ScreenViewsTest` covers the screen trail, `screen_viewed`, every leaving summary going out as `screen_left`, and the screens added to plays. `PlayerSessionAggregatorTest` covers the transcript counter (regression: it read a name the player never logged).
- `ListenAnalyticsTest` covers redacted error messages and error-tracking copies (a nested playlist error with a signed URL), the listen record's fields, the qualified threshold, the car as a client surface, context not overwriting the record, `playback_interrupted`, the listen fields on `playback_started`, and errors reaching error tracking.
- `AnalyticsPolicyTest` covers the default levels, sampling by bucket, the switch payload (base, per version, bad input), the remembered switch and its version check, bucket persistence through `Store` (a saved bucket is reused; a missing or out-of-range one is picked and saved), `AnalyticsEmit` dropping and tagging, and `AnalyticsBeforeSend` tagging (replacing another build's tags), dropping SDK events and keeping a queued event's sample share, the identity events that ignore the switch, and an explicit flag reload applying the switch; a source scan keeps every `PostHog.reloadFeatureFlags` call inside `AnalyticsHelper`. `AnalyticsBudgetTest` checks that every glossary event has a level matching `DEFAULT_LEVELS` and a monthly estimate, and that estimated monthly events per active install stay within the budget (225; about 212 with the listening record and page views; `screen_viewed`'s 20 a month is an estimate to re-measure). The budget counts glossary events only, not SDK identity, flag, survey or exception events.
- `PersonSetOnceTest` covers the install channel and the first setup route going through the set-once slot. `AnalyticsHelperStateTest` checks review prompts never send the survey trigger; `RecordingAnalyticsTest` covers the entry-detail allowlist, the capped Echo text and the queue and Library entry points.
- `GlossaryAllEventsEmissionTest` covers every `emission:` inventory row; `LifecycleSdkMappingTest` / `InstallChannelAttributionTest` assert no dual open/install emits.
- Architecture guards in `:core:testing` enforce CSV ↔ allowlist ↔ inventory parity.

```bash
./gradlew :core:analytics:testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest --tests '*GlossaryCoverageGuardTest'
```
- `FeedAnalyticsTracksTest` covers both feed events' properties, pass summaries, empty passes and address-like show IDs; `GlossaryRecipeIdTest` covers the `recipe_id` / `recipe_ids` declarations.
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
