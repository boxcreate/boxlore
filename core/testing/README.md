# `:core:testing`

## Purpose

Owns shared JVM test fixtures, dispatcher helpers, and architecture guards used by core and feature modules. It is a test-support module and is not shipped in the app APK.

## Public API

- `MainDispatcherExtension` installs a test main dispatcher for a test scope.
- `TestFixtures` provides minimal podcast and episode builders.
- `PhoneGoldenRule` and `PhoneGoldenConfiguration` provide the local, six-configuration phone capture matrix. Roborazzi writes actual renders; verification compares dimensions and every ARGB pixel. Recording refuses to overwrite an existing phone baseline.
- The same fixtures support `-PtabletCaptures=before|after`: 600×960, 840×1280, 1280×800, 1600×1000 and 673×841 flat/tabletop, in light/dark. `setContent` supplies the shared window model for tablet after captures; before captures select the preserved legacy presentation. Phone captures retain their original composition and animation start frame. These review images are separate from immutable phone baselines. Standalone feature captures can opt into the same fold-safe viewport selection used by hidden-navigation shell routes; the full player retains its separate art/control regions. The phone branch still passes the original content directly to Compose.
- A single private constant owns the existing `boxlore.tabletCaptures` JVM property key across matrix selection, content setup, and output routing; its values and capture behavior remain unchanged.
- `fakes.FakeLocalEpisodeCatalogPort` pages/sorts/searches in memory and returns null from `findByCatalogKey` when guid and enclosure are both blank. `refreshOutcome`, `publisherFeedUnchanged` and `lastRefreshRequest` let a test drive and inspect `refresh`.
- `fakes.FakeDeviceIdentityPort` provides a deterministic or mutable device ID fake for sync tests.
- Test dependencies such as JUnit Jupiter, coroutines-test, Turbine, and MockWebServer are exposed for modules that declare `testImplementation(projects.core.testing)`.
- Architecture guards under `src/test` validate project structure and dependency rules.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/testing/
  MainDispatcherExtension.kt
  TestFixtures.kt
  fakes/FakePorts.kt
src/test/java/cx/aswin/boxlore/core/testing/architecture/
  ArchitectureGuardTest.kt
  GlossaryCoverageGuardTest.kt   # CSV ↔ AnalyticsGlossary ↔ emission inventory
  BrandNameGuardTest.kt          # listener-facing text spells boxlore in lowercase
```

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, and `:core:designsystem` for the test-only adaptive capture provider.
- Libraries exposed for tests: JUnit Jupiter/Vintage-compatible JUnit 4, coroutines-test, Turbine, MockWebServer, Mockito, Robolectric, Compose UI testing and Roborazzi. These dependencies remain outside production configurations.
- Test-only libraries: Konsist.
- Reverse-edge rule: production modules may use this only from test configurations.

## Threading / lifecycle

- `MainDispatcherExtension` swaps `Dispatchers.Main` for the duration of each test and restores it afterward.
- The screenshot rule owns a test Activity for one test only; it never starts the production Application or constructs its dependency graph.

## Persistence & identity

- No app persistence is owned here.
- Architecture tests read project files such as `settings.gradle.kts` and module README locations but do not define app storage identity.

## Testing notes

- Run this module to execute architecture guards.
- Guards cover feature isolation, selected singleton allowlists (including Android framework entry points such as `MediaRouter`), catalog-to-designsystem boundaries, direct database access restrictions, and README presence for included app/core/feature modules.
- `GlossaryCoverageGuardTest` asserts every `docs/analytics/event_glossary.csv` name (snake case, or PostHog's spaced survey names such as `survey sent`) is in `AnalyticsGlossary`, and every name has a row in `docs/analytics/glossary_emission_coverage.csv` with mode `emission:*`, `sdk_backed:*`, or `person_props_only` (opens/installs must not be dual-counted as custom emits).
- `BrandNameGuardTest` scans `src/main` string resources, manifest labels and Kotlin string literals in `app`, `core` and `feature` for `BoxLore`, `Boxlore`, `BOXLORE` and the retired BoxCast name. Word boundaries skip identifiers, log tags and constants; lowercase `boxcast` deep links and legacy migration names stay allowed in Kotlin. HTTP User-Agent product tokens are allowlisted by file because podcast hosts identify the app by them.
- Do **not** add `api(projects.core.analytics)` here — `:core:analytics` already `testImplementation`s this module (cycle risk). Emission suites live in `:core:analytics`.

```bash
./gradlew :core:testing:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs the architecture guards with the root JVM suite.
- The guards complement shell-based architecture checks under `scripts/ci`.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:model` README](../model/README.md)

Standalone tablet capture frames use an outer matching light/dark theme and solid surface for unused fold regions. The optional native fixture support depends on Material 3 for that backdrop; this adds no application release dependency. Phone capture composition bypasses the tablet frame.
