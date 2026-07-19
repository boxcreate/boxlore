# Testing

How Boxlore is tested: layers, commands, coverage floors, architecture gates, and the max-coverage checklist.

## Status legend

| Status | Meaning |
| :--- | :--- |
| **Done** | Present and exercised at the max-coverage bar |
| **WIP** | Exists but below the max-coverage bar |
| **Yet to start** | Not implemented yet |
| **Excluded** | Irreducible exclusion with an alternate coverage layer |

## Goal

Maximum achievable automated coverage: every testable production path is exercised by JVM, Compose `androidTest`, Maestro, and/or Roborazzi. High Kover floors fail CI on drop. Architecture guards fail `merge-ci` on graph drift.

**Strategy:** hermetic JVM — constructors, domain ports, shared fakes in `:core:testing`, assemblers, Turbine. No MockK/Hilt. No Application-backed Home/Info suites (hermetic equivalents cover the same behaviors). Media3 service / `PlaybackRepository` stay out of the line gate; covered by policy unit tests + Maestro.

## Layers

| Layer | Command / location | Catches | Status |
| :--- | :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs | WIP |
| Architecture-as-code | `:core:testing` Konsist / scripts | Feature isolation, graph, allowlists | Done |
| Static analysis | `./gradlew detekt`; `./gradlew ktlintCheck` | Style / quality beyond baselines | Done |
| Android lint | `./gradlew lintDebug` | Manifest / resource / API lint | Done |
| Coverage (Kover) | `./gradlew :koverVerifyMerged` | Merged floor (ratchet toward 80%) | WIP |
| Compose UI | `androidTest` per feature | Dead controls, empty/error UI | WIP |
| Maestro | `maestro/` + nightly validate | Real-device flow regressions | WIP |
| Screenshots | `screenshots/baselines/` + Roborazzi | Visual regressions | Yet to start |

Architecture boundaries: [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Stack

- JUnit 5 (+ Vintage where leftovers remain)
- Turbine, MockWebServer, Robolectric
- Konsist (architecture guards in `:core:testing`)
- Shared fixtures / fakes: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`, `core.testing.fakes.*`)
- No MockK / Hilt
- Compose `androidTest` uses JUnit4 + `AndroidJUnitRunner`

## Max-coverage bars

| Layer | Bar | CI fail |
| :--- | :--- | :--- |
| JVM | Every public behavior-owning type has a suite (happy/empty/error/branches) or an irreducible exclusion | `testDebugUnitTest` |
| ViewModels | Every feature VM: load/success/empty/error + user events via Turbine | unit job |
| Instrumented | Every interactive feature: primary + secondary paths | instrumented matrix |
| App nav | Bottom-nav + deep-link smoke | instrumented / Maestro |
| Maestro | Strict critical journeys | YAML validate on merge; device nightly |
| Screenshots | Goldens for key screens per feature + verify | Roborazzi verify |
| Kover merged | **≥ 80%** end state (ratchet **40 → 55 → 70 → 80**) | `:koverVerifyMerged` |
| Kover per-module | **≥ 70%** on logic-heavy modules (ratchet as suites land) | module verify |
| Architecture | ARCHITECTURE.md boundaries | scripts + `ArchitectureGuardTest` + dependencyGuard |
| New code | New `*ViewModel` / `*Repository` need matching `*Test.kt` | Konsist/script |

### Current Kover floor

| Target | Status |
| :--- | :--- |
| Merged floor ≥ **40%** on full gated set | Done (enforced by `:koverVerifyMerged`) |
| Measured merged line coverage | **≈ 47.9%** (13,358 / 27,869 lines) |
| Per-module ≥ 70% on logic modules | Yet to start |
| Merged floor ≥ 55% / 70% / **80%** | Yet to start |

The floor stays at **40** for now (measured ≈ 47.9%, so we sit in the 40–55 band). Reaching the
next **55** rung requires exercising the Application-backed feature `*ViewModel`s and Media3-bound
components (`PlaybackRepository`, `PlaybackQueueCoordinator`, `PlaybackTelemetrySession`,
`PlaybackHistoryStore`, `DownloadRepository`, `SmartDownloadManager`) plus concrete repository
graphs (`PodcastRepository`, `RssPodcastRepository`, `LibraryBackupManager`), which cannot be
constructed hermetically without MockK/Hilt or a full Media3/Room stack. Those remain covered by
hermetic `logic/`-package suites, assembler + port suites, `androidTest`, and Maestro rather than
direct JVM instantiation. The measured line % above reflects the pure/hermetic ceiling reached with
the `:app` Compose nav / FCM / survey chrome excluded from the line gate (see
[`build.gradle.kts`](../build.gradle.kts) `kover { }`).

Recent hermetic additions raising the floor toward 55: `AdaptiveCandidateScorer` (ranking, Room via
Robolectric), `MixtapeEngine` (pure + adaptive branch), `AdaptiveContentCandidateRanker`,
`LearnCuriosityHistoryStore` (SharedPreferences via Robolectric), `HomeHeroLogic` branch coverage,
and the `feature:library` download-model formatters.

Gated modules: `:core:catalog`, `:core:domain`, `:core:analytics`, `:core:rss`, `:core:downloads`, `:core:playback`, `:core:ranking`, `:core:prefs`, `:core:network`, `:core:database`, `:core:model`, `:feature:home`, `:feature:info`, `:feature:explore`, `:feature:library`, `:feature:onboarding`, `:feature:briefing`, `:feature:player`, `:app`.

```bash
./gradlew testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest
./gradlew :koverVerifyMerged
./gradlew :koverHtmlReportMerged
./gradlew :koverXmlReportMerged
```

Reports: `build/reports/kover/`.

### Irreducible exclusions (line gate only)

| Exclusion | Alternate coverage |
| :--- | :--- |
| `PlaybackRepository` + `core.playback.service.*` / Auto | Policy unit tests + Maestro play/queue |
| `@Composable` / `@Preview` | `androidTest` and/or Roborazzi |
| PostHog / Firebase SDK internals | Not our code; features must not import PostHog |
| Generated `R` / `BuildConfig` / databinding | Generated |

## Architecture CI (fail on deviate)

`merge-ci` (`unit-tests.yml`) fails when architecture drifts:

| Guard | What it enforces |
| :--- | :--- |
| `ArchitectureGuardTest` | No feature→feature Gradle deps or imports; catalog↛designsystem; catalog↛playback; catalog must not `api` analytics/ranking; module READMEs; `getInstance` allowlist; package=module (+ `core.data` stubs); no Hilt/Koin/Dagger/MockK |
| `scripts/ci/check-feature-no-posthog.sh` | Features never import/capture via PostHog |
| `scripts/ci/check-feature-no-boxlore-database.sh` | Home/Info VMs/assemblers do not take `BoxLoreDatabase` |
| `dependencyGuard` | Locked dependency lists for `:app`, `:core:catalog`, `:core:playback` |

```bash
bash scripts/ci/check-feature-no-boxlore-database.sh
bash scripts/ci/check-feature-no-posthog.sh
./gradlew :core:testing:testDebugUnitTest
./gradlew :app:dependencyGuard :core:catalog:dependencyGuard :core:playback:dependencyGuard
```

## Static analysis

```bash
./gradlew detekt
./gradlew ktlintCheck
./gradlew lintDebug
```

Detekt: `config/detekt/{detekt.yml,baseline.xml}`.  
ktlint: per-project baselines under `config/ktlint/`.

## Module × layer checklist

| Module | JVM | VM Turbine | androidTest | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `:core:ranking` | WIP | n/a | n/a | Adaptive scoring / feedback |
| `:core:catalog` | WIP | n/a | n/a | Repos, Room ports, backup |
| `:core:playback` | WIP | n/a | n/a | Policy/queue in Kover; service excluded |
| `:core:downloads` | WIP | n/a | n/a | Repo + SmartDownload |
| `:core:prefs` | WIP | n/a | n/a | DataStore + migrator |
| `:core:database` | WIP | n/a | n/a | In-memory DAOs |
| `:core:rss` | WIP | n/a | n/a | Feed fixtures + repo |
| `:core:analytics` | WIP | n/a | n/a | Tracks + glossary |
| `:core:network` | Done | n/a | n/a | MockWebServer contracts |
| `:core:domain` | Done | n/a | n/a | Port contracts |
| `:core:model` | WIP | n/a | n/a | Behavior helpers only |
| `:feature:home` | WIP | WIP | WIP | Settings Done; Home VM hermetic WIP |
| `:feature:info` | WIP | WIP | Yet to start | Assembler Done |
| `:feature:explore` | Yet to start | Yet to start | Yet to start | |
| `:feature:library` | Yet to start | Yet to start | Yet to start | |
| `:feature:onboarding` | Yet to start | Yet to start | Yet to start | |
| `:feature:briefing` | Yet to start | Yet to start | Yet to start | |
| `:feature:player` | WIP | n/a | Yet to start | v2 logic JVM |
| `:app` | WIP | n/a | Yet to start | Workers, FCM, nav smoke |

Application-backed Home/Info suites are **not** pursued; hermetic assembler + port suites replace them.

## Compose UI (`androidTest`)

| Target | Status |
| :--- | :--- |
| Hermetic Add RSS / Downloads settings in `:feature:home` | Done |
| Deep home feed + empty/error hosts | WIP |
| Instrumented coverage in other feature modules | Yet to start |
| App bottom-nav / deep-link smoke | Yet to start |

```bash
./gradlew :feature:home:connectedDebugAndroidTest
```

## Maestro

| Target | Status |
| :--- | :--- |
| Flow YAML under `maestro/` | Done |
| Nightly YAML validate | Done |
| Strict smoke (launch/home) | Done |
| Strict flows: RSS, subscribe→library, play→mini, Learn, briefing, settings | Yet to start |
| Maestro Cloud required CI | Out of scope |

See [`maestro/README.md`](../maestro/README.md).

## Screenshots

| Target | Status |
| :--- | :--- |
| Reserved `screenshots/baselines/` | Done |
| Checked-in PNG goldens | Yet to start |
| Roborazzi CI gate | Yet to start |

See [`docs/screenshots/README.md`](screenshots/README.md).

## CI

| Workflow | Runs | When | Status |
| :--- | :--- | :--- | :--- |
| `unit-tests.yml` | Architecture guards + detekt + ktlint + unit + Kover + lint + Dependency Guard | `merge-ci` / dispatch | Done |
| `android-instrumented-tests.yml` | Feature `connectedDebugAndroidTest` matrix | Same merge gate | WIP (home only today) |
| `maestro-nightly.yml` | Validate Maestro YAML; optional Cloud | Nightly / manual | Done |

**Merge gate:** add `merge-ci` only when ready to merge.

Protected inputs: `app/google-services.json` is gitignored; CI writes a non-secret stub.

## Conventions

- Prefer constructor injection + fakes (`core.testing.fakes`) over `getInstance` in new tests.
- Hard ViewModels use assemblers + ports from `:core:domain` and Turbine + `MainDispatcherExtension`.
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners.
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures.
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` where required.
- Workers that need listen history use `HistoryRecommendationSource` / ports — not a second `PlaybackRepository`.

## Module README checklist

Every `app/`, `core/*/`, and `feature/*/` module keeps a folder README. Shape: [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md). Konsist fails if an included module lacks `README.md`.
