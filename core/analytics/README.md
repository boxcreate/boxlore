# `:core:analytics`

## Purpose

Owns analytics capture and non-fatal error reporting. It wraps PostHog behind boxlore's own interfaces, so callers and feature modules never touch the SDK, and applies event levels, sampling, redaction and episode identity before anything is sent.

It does not configure the PostHog SDK (`:app` does, and installs `AnalyticsBeforeSend`), own UI or repositories, or store anything beyond its own preferences. Event names, properties, levels, the volume budget and the SDK-backed lifecycle events are defined in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md); this README does not repeat them.

## Public API

### Entry points

- `Analytics`: the interface production code and tests depend on.
- `AnalyticsHelper`: the process-wide PostHog-backed implementation and its `track*` façades, grouped by area in the `*AnalyticsTracks.kt` files.
- `RecordingAnalytics`: an in-memory `Analytics` for tests.
- `ErrorReporter`: the non-fatal error façade; `:app` installs the real reporter.
- `AnalyticsGlossary`: event sets and property names from the glossary, and `normalizeEntryPoint`, which maps free-form entry points onto glossary values.

### Levels and sending

- `AnalyticsPolicy` and `AnalyticsLevel`: each event is always sent, sampled or off. Defaults are `AnalyticsPolicy.DEFAULT_LEVELS`; the `analytics-policy-v1` flag overrides them without a release (payload shape in the `AnalyticsPolicy` KDoc).
- `AnalyticsEmit`: the one path app events take. It applies levels, adds `recipe_id` where known, and sends set-once person properties through `personSetOnce`, never nested under `personSet`.
- `AnalyticsBeforeSend`: the PostHog `beforeSend` hook. It applies levels to everything PostHog captures, SDK events included, and stamps the tags `:app` gives it on every event: the build's environment (`is_internal`, `app_environment`) and where this copy came from (`build_channel`, `install_source`, `installer_package`).
- `AnalyticsPolicy.ALWAYS_SENT` identity events ignore the switch; `admitCaptured` keeps a queued app event's original sample decision.
- `AnalyticsHelper.refreshAnalyticsPolicy` applies the flag whenever PostHog reports loaded flags; `initPolicy` restores the last switch at launch, for the same app version only.
- `AnalyticsHelper.reloadFeatureFlags` is the only caller of PostHog's flag reload, so every reload applies the switch.
- `PostHogStartup`: PostHog setup runs off the main thread. Calls made before it finishes are queued in order (bounded) and replayed; `runWhenReady` defers work until setup. If setup throws, `hasFailed` stays set and later calls are dropped.

### Episode identity

- `EpisodeRecipeIds` (through `AnalyticsEmit`): fills `recipe_id` on every event in `AnalyticsGlossary.EPISODE_ID_EVENTS` (29 events) and `recipe_ids` on episode surfaces of `home_surface_impression` and `explore_surface_impression`. Caller-provided values are kept.
- A recipe ID is the episode ID itself when it is in the recipe range, else one already known (`rememberEpisodeRecipeIds`). Unknown IDs are looked up in the background through the lookup `:app` installs (`installEpisodeRecipeIdLookup`), so an event is never held back. Caches are bounded.
- ID formats: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).

### Screens and entry points

- `ScreenViews`: tracks the current screen and the one before. Each showing screen holds a `Registration` (`TrackScreenView` in `:core:designsystem`); the newest one still showing is current.
- `trackScreenViewed` sends `screen_viewed` for screens without a view event of their own; page leaving summaries go out as `screen_left`.
- `PendingEntryPoint`: carries a play's entry-point context across the MediaController boundary. `noteRequest` records the screens showing when a play is asked for, and `withScreens` adds them to the play's events.
- `EntryPointContext.ALLOWED_KEYS`: the only context keys playback events accept; none overwrites a field the event set, and media item extras never reach analytics.

### Listening and errors

- `PlaybackListen.kt` (`ListenStart`, `PlaybackListenSummary`, `PlaybackInterruptedListen`, `PlaybackListenValues`): the one-record-per-listen model that `:core:playback` fills; `ListenAnalyticsTracks` sends it.
- `trackPlaybackError` also sends the cause to PostHog error tracking (`AnalyticsEmit.exceptionSink`).
- `AnalyticsRedaction` removes links and signed tokens from error messages and the whole cause chain; error tracking receives `RedactedException` copies, never the player's own exception.
- `PlayerSessionAggregator`: counts player interactions per episode into one summary event.

### Area façades

- API sessions (`trackApiSessionStatus`, `ApiSessionStatus`) and App Check (`trackAppCheckStatus`): categories only, never tokens, IDs or keys.
- Feeds (`trackFeedFetchResult`, `trackFeedUrlCorrected`, `trackOwnIdsUpgradeCompleted`): never carry a feed address. Catalog code reaches them through `:core:domain`'s `FeedTelemetryPort`, which `:app` wires.
- Appearance (`trackHomeHeroStyleSelected`, `syncHomeHeroStylePersonProperties`) and recommendations (`trackRecommendationSectionToggled`) report the names listeners see in Settings, not stored keys.
- Surveys (`SurveyAnalyticsTracks`): PostHog's standard survey events for boxlore's own NPS card; `surveyCopyOverrides` reads survey wording from a flag payload.
- Support (`SupportAnalyticsTracks`, `SupportWorldContext`): `supportWorldVariant` reads the support experiment flag once per visit and records an exposure only for a known variant.
- Onboarding, growth, discovery, library, queue and playback façades live in their matching `*AnalyticsTracks.kt` files; `GenrePersonaLogic` derives onboarding's genre persona properties.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/analytics/
  Analytics.kt                     # the interface
  AnalyticsHelper.kt               # PostHog-backed singleton and façades
  AnalyticsEmit.kt                 # levels, recipe IDs, sinks
  AnalyticsPolicy.kt               # levels, sampling, remote switch
  AnalyticsBeforeSend.kt           # PostHog beforeSend hook
  AnalyticsGlossary.kt             # event sets, property names, entry points
  AnalyticsRedaction.kt            # error message redaction
  EpisodeRecipeIds.kt              # recipe_id / recipe_ids filling
  PostHogStartup.kt                # off-main setup, queue and replay
  ScreenViews.kt
  PendingEntryPoint.kt
  EntryPointContext.kt             # allowed playback context keys
  PlaybackListen.kt                # listen record model and values
  SessionAggregator.kt             # PlayerSessionAggregator
  AnnouncementRef.kt               # which in-app announcement an event is about
  GenrePersonaLogic.kt
  ErrorReporter.kt
  RecordingAnalytics.kt
  *AnalyticsTracks.kt              # façades by area
```

## Dependencies

- `implementation`: `:core:model`, `:core:prefs`.
- Libraries: PostHog Android SDK, AndroidX core.
- Forbidden: catalog, database, network, playback, downloads, designsystem or feature modules. Callers that emit events depend on this module directly; features never import PostHog.

## Threading / lifecycle

- `AnalyticsHelper` is process-scoped and callable from any thread; the SDK dispatches internally.
- Calls made before PostHog setup finishes are queued and replayed in order; feature flags and `getDistinctId()` read as unset until then, and callers fall back.
- `ScreenViews` and `PendingEntryPoint` are synchronized; `PlayerSessionAggregator` is not, and the player UI calls it on the main thread.
- Person properties that follow a setting (the Home hero look) are sent once per process and on each change, so rotation, folding and resizing do not resend them.

## Persistence & identity

Never rename these:

- SharedPreferences file `boxlore_analytics_prefs` (`PrefsFileMigrator.Files.ANALYTICS`, migrated from `boxcast_analytics_prefs`).
- Keys `is_first_launch`, `analytics_sample_bucket` (this install's sample bucket, kept across a PostHog reset) and `analytics_policy` (the last remote switch with its app version). `AnalyticsPolicy.Store` is the storage seam.
- Event and property names are contracts with the glossary and its CSV ([`docs/analytics/event_glossary.csv`](../../docs/analytics/event_glossary.csv)) and the coverage inventory ([`docs/analytics/glossary_emission_coverage.csv`](../../docs/analytics/glossary_emission_coverage.csv)).

## Testing notes

Tests live in `core/analytics/src/test` (JUnit 5). Use `RecordingAnalytics` for classes that take `Analytics`, and `AnalyticsHelper.installRecordingSink(recorder)` for code that calls `AnalyticsHelper`; the sink records every event whatever its level unless `applyLevels = true`.

| Area | Test classes |
| :--- | :--- |
| Levels, switch, before-send | `AnalyticsPolicyTest`, `AnalyticsBudgetTest` |
| Glossary coverage | `GlossaryAllEventsEmissionTest` (with `LifecycleSdkMappingTest`, `InstallChannelAttributionTest`), `GlossaryRecipeIdTest` |
| Recipe IDs | `EpisodeRecipeIdsTest` |
| Startup queue | `PostHogStartupTest` |
| Screens | `ScreenViewsTest` |
| Listening and errors | `ListenAnalyticsTest`, `PlayerSessionAggregatorTest` |
| Façades | `AnalyticsTracksFacadeTest`, `RecordingAnalyticsTest`, `RecordingAnalyticsExtraTest`, `AnalyticsHelperStateTest` |
| Person properties | `PersonSetOnceTest`, `HomeHeroStyleAnalyticsTest` |
| Area events | `FeedAnalyticsTracksTest`, `RecommendationsAnalyticsTest`, `SurveyAnalyticsTest`, `SupportWorldVariantTest`, `GenrePersonaLogicTest` |

`GlossaryCoverageGuardTest` in `:core:testing` keeps the CSV, allowlist and inventory in step.

```bash
./gradlew :core:analytics:testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest --tests '*GlossaryCoverageGuardTest'
```

## CI relevance

- `unit-tests.yml` runs these tests, merges their Kover coverage, and runs `scripts/ci/check-feature-no-posthog.sh`, which stops feature modules from using PostHog directly.
- `:core:testing` architecture guards check the glossary parity and this README.

## See also

- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:core:network` README](../network/README.md)
