# `:core:testing`

## Purpose

Owns shared JVM test support for core and feature modules: fixtures, fakes for `:core:domain` ports, the main-dispatcher extension, the local phone-golden and tablet-capture rule, and the architecture guards that check the whole project. It is test-only and never ships in the app.

## Public API

- `MainDispatcherExtension`: a JUnit 5 extension that installs a test main dispatcher for each test.
- `TestFixtures`: small builders for podcasts, RSS podcasts, episodes, RSS episodes and history items with stable IDs.
- `fakes.FakeLocalEpisodeCatalogPort`: pages, sorts and searches episodes in memory; `findByCatalogKey` returns null when guid and enclosure are both blank. `refreshOutcome`, `publisherFeedUnchanged` and `lastRefreshRequest` drive and inspect `refresh`.
- `fakes.FakeLocalCatalogPort`, `FakePodcastCatalogPort`, `FakeEpisodeOfflineLookup`, `FakeRssSubscriptionPort`, `FakeRankingResetPort`, `FakeHistoryRecommendationSource` and `FakeConnectivityStatusPort`: in-memory fakes for the matching ports.
- `fakes.FakeDeviceIdentityPort`: a fixed or mutable device ID for sync tests.
- `PhoneGoldenRule` and `PhoneGoldenConfiguration`: the local capture rule and its matrix. Phone runs cover three sizes in light and dark; `-PtabletCaptures=before|after` switches to tablet, landscape and flat/tabletop fold sizes, and `audit` to the adaptive review matrix (`auditConfigurations`, filtered by `-PauditSizes`). `capture(screen, allWindows)` can composite sheets, dialogs and popups; `captureSequence` writes numbered motion frames, optionally with system animations off (`setReducedMotion`); `assumeAuditCapture` skips audit-only fixtures elsewhere.
- `BoxloreTestWindows`: named windows (phones, the Fold 8 cover, a split half, pairs either side of the 600, 480 and 840dp gates, book foldables with their hinges, tablets) and `walkWindows`, which moves a window state through steps and checks each once composition settles.
- `SystemInsets` and `applyFakeSystemInsets`: fake status and navigation bars, camera cutout and keyboard sent to a test window. `insets` and `tabletop-insets` audit sizes send the `SIDEWAYS_3_BUTTON` and `TABLETOP_STATUS` presets and tint the inset areas red in the capture.
- Phone verification compares dimensions and every ARGB pixel against `screenshots/baselines/phone`; recording refuses to overwrite an existing baseline. Tablet captures are review images only. How to run them: [`docs/TESTING.md`](../../docs/TESTING.md#screenshots).
- `PhoneGoldenRule.setContent` composes phone captures unchanged; tablet "after" captures get the shared window model, and `foldSafeViewport` places standalone screens in the shell's fold-safe region.
- Modules that declare `testImplementation(projects.core.testing)` also get its test libraries (see Dependencies).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/testing/
  MainDispatcherExtension.kt
  TestFixtures.kt
  PhoneGoldenRule.kt               # PhoneGoldenConfiguration and PhoneGoldenRule
  BoxloreTestWindows.kt            # named test windows and walkWindows
  SystemInsetsFixture.kt           # fake system insets and the capture overlay
  GoldenFoldSafeViewport.kt        # fold-safe region for standalone tablet captures
  fakes/FakePorts.kt
src/test/java/cx/aswin/boxlore/core/testing/
  architecture/
    ArchitectureGuardTest.kt       # module graph, imports, DI ban, READMEs, singleton allowlist
    GlossaryCoverageGuardTest.kt   # event glossary CSV, AnalyticsGlossary and emission inventory
    BrandNameGuardTest.kt          # listener-facing text spells boxlore in lowercase
    ModuleReadmeBudgetTest.kt      # README length, history markers, nothing after See also
    ScreenViewCoverageTest.kt      # every screen, page, sheet, dialog and tab sends a page view
  i18n/
    TranslationResources(GuardTest).kt  # translations keep placeholders, plural forms and lowercase boxlore
    HardcodedText(GuardTest).kt         # listener text in Kotlin, held to docs/i18n/hardcoded-text-baseline.txt
  fakes/FakePortsTest.kt
src/test/resources/module-readme-budget.txt   # allowances for READMEs not yet rewritten
```

## Dependencies

- `api`: `:core:model`, `:core:domain`, `:core:designsystem` (window and fold models, theme for captures).
- Test libraries exposed through `api`: JUnit Jupiter and JUnit 4, coroutines-test, Turbine, MockWebServer, Mockito, Robolectric, Compose UI testing, activity-compose, Roborazzi and Coil.
- `implementation`: core-ktx and Material 3 (capture backdrop). Konsist is used only by this module's own tests.
- Production modules use this module only from test configurations.
- Never add `:core:analytics` here: `:core:analytics` already uses this module in its tests, so the edge would form a cycle. Emission suites live in `:core:analytics`.

## Threading / lifecycle

- `MainDispatcherExtension` swaps `Dispatchers.Main` before each test and restores it afterwards.
- `PhoneGoldenRule` owns a test Activity for one test; it never starts the production Application or builds its dependency graph, and it restores the global Coil image loader after the test.

## Persistence & identity

- No app persistence is owned here.
- Architecture guards read project files (`settings.gradle.kts`, build files, sources, resources, module READMEs, `docs/analytics/*.csv`, `docs/i18n/hardcoded-text-baseline.txt`) and define no app storage. Translation rules: [`docs/i18n/TRANSLATING.md`](../../docs/i18n/TRANSLATING.md).
- System properties: `boxlore.projectRoot`, `boxlore.phoneGoldens` (`record` / `verify`), `boxlore.tabletCaptures` (`before` / `after` / `audit`) and `boxlore.auditSizes`, set by the root build from `-PphoneGoldens`, `-PtabletCaptures` and `-PauditSizes`.

## Testing notes

| Area | Tests |
| :--- | :--- |
| Feature isolation, catalog boundaries, DI ban, package names, README presence, `getInstance` allowlist (Room, WorkManager, MediaRouter, Firebase and others), matching test suites | `ArchitectureGuardTest` |
| Analytics glossary parity and emission inventory modes (`emission:*`, `sdk_backed:*`, `person_props_only`) | `GlossaryCoverageGuardTest` |
| Lowercase boxlore in resources, manifest labels and Kotlin literals; User-Agent tokens allowlisted by file | `BrandNameGuardTest` |
| Module README budget and template rules | `ModuleReadmeBudgetTest` |
| Page views on every named screen | `ScreenViewCoverageTest` |
| Translations in every module's `res/values-<language>` | `TranslationResourcesGuardTest` |
| Hard-coded listener text, against a baseline that only shrinks (`BOXLORE_I18N_BASELINE=write` regenerates it) | `HardcodedTextGuardTest` |
| Fakes and fixtures | `FakePortsTest` |
| Capture matrix and size filter, test windows, fake insets, en-XA in library resources | `PhoneGoldenConfigurationTest` in `:core:designsystem` |

```bash
./gradlew :core:testing:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these guards in the `core` shard.
- The guards complement the shell checks under `scripts/ci`.
- `*PhoneGoldenTest` and `*AuditCaptureTest` classes are excluded from every test run unless `-PphoneGoldens` or `-PtabletCaptures` is set, so captures stay local.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/MODULE_README_TEMPLATE.md`](../../docs/MODULE_README_TEMPLATE.md)
- [`core/model/README.md`](../model/README.md)
