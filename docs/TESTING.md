# Testing

How Boxlore is tested: layers, commands, coverage floors, architecture gates, and the coverage checklist.

## Status legend

| Status | Meaning |
| :--- | :--- |
| **Done** | Present and exercised at the bar |
| **WIP** | Exists but below the bar |
| **Yet to start** | Not implemented yet |
| **Excluded** | Irreducible exclusion with an alternate coverage layer |

## Goal

Automated coverage focused on **hermetic JVM** for product logic (queue fill, ranking, catalog, prefs, feature `logic/`). High Kover floors fail CI on drop. Architecture guards fail the unit PR job on graph drift.

**Strategy:** constructors, domain ports, shared fakes in `:core:testing`, assemblers, Turbine. No MockK/Hilt. No Application-backed Home/Info suites. Media3 service / `PlaybackRepository` stay out of the line gate; covered by policy unit tests. Maestro YAML is validated nightly; the existing optional Cloud job runs only when its credentials are configured and is not a PR gate.

## Layers

| Layer | Command / location | Catches | Status |
| :--- | :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs | WIP |
| Architecture-as-code | `:core:testing` Konsist / scripts | Feature isolation, graph, allowlists, new-code tests | Done |
| Static analysis | `./gradlew detekt` | Style / quality beyond baselines | Done |
| Android lint | `./gradlew lintDebug` | Manifest / resource / API lint | Done |
| Coverage (Kover) | `./gradlew :koverVerifyMerged` | Merged floor (ratchet toward 80%) | WIP |
| Screenshots | `screenshots/baselines/` + optional Roborazzi (local) | Visual regressions | Optional |
| Maestro | `maestro/` YAML validate | Flow file presence/syntax | Done |

Architecture boundaries: [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Choose checks for a change

Run commands from the repository root. Start with the affected behavior and broaden to shared consumers when a change crosses module boundaries. See [local setup](../CONTRIBUTING.md#local-development--personal-use) for tool and configuration requirements.

| Change | Checks |
| :--- | :--- |
| Android logic or state | Add or extend JVM regression coverage; run `./gradlew :<module>:testDebugUnitTest` for affected modules (for example, `:core:playback:testDebugUnitTest`), then `./gradlew testDebugUnitTest --continue` for shared behavior |
| Module boundaries, dependencies, or composition | Run both `scripts/ci/check-feature-no-boxlore-database.sh` and `scripts/ci/check-feature-no-posthog.sh` with Bash; run `./gradlew :core:testing:testDebugUnitTest :app:dependencyGuard :core:catalog:dependencyGuard :core:playback:dependencyGuard` |
| Kotlin, Android resources, or manifests | Run `./gradlew detekt ktlintCheck lintDebug`; include affected JVM tests for changed behavior |
| App UI or runtime behavior | Build/install with `./gradlew installDebug` on an available device; verify the affected screen and action; use optional local Roborazzi when appropriate |
| Catalog sync logic | Run `npm ci --prefix scripts` once, then `npm run test:sync --prefix scripts`; deployment is a separate operation requiring the internal runbook |
| New-episode notification checker | Run `npm ci --prefix scripts` once, then `npm run test:check-new-episodes --prefix scripts` |
| Tracked-feed repair | Run `npm ci --prefix scripts` once, then `node --test scripts/backfill-tracked-podcast-feeds-lib.test.js` |
| Changelog or release tooling | Run `python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v` |
| Documentation only | Review local links and instruction destinations; run `git diff --check` |

For Android code changes, the full CI command is `./gradlew detekt testDebugUnitTest :koverVerifyMerged :app:dependencyGuard :core:catalog:dependencyGuard :core:playback:dependencyGuard --continue`, with `./gradlew lintDebug` in a separate job. The unit workflow also runs the architecture shell guards and Python release-tooling tests. `ktlintCheck` is a local check; it is not part of the current PR workflow.

For release-copy and announcement changes, the Python suite covers required copy, preserved wording/order, historical backfill, batch failure before writes, UTF-8 payload limits, provider dry-run flags and test-mode audience rejection with a fake sender. **Offline release rehearsal** (manual dispatch) runs the same suite and writes reviewable candidate files without credentials, publishing or messages. **Release copy** checks the PR's body and impact labels without an Android build. See [release-copy and rehearsal recovery](../scripts/README.md#authored-release-copy-and-isolated-rehearsals) before testing live notifications. These are offline checks; APK installation and actual notification receipt require separate device acceptance.

Report which checks completed and distinguish automated coverage from manual checks or paths that still need verification. Script tests exercise local logic; they do not verify a production deployment.

For updater work, run `./gradlew :app:testDebugUnitTest --tests 'cx.aswin.boxlore.updates.*'` for offline/fake HTTP fixtures. These tests never publish releases, deliver FCM or install APKs. The optimized preflight builds the direct APK and `bundlePlayRelease`, checks each variant's R8 contracts, and rejects installer code/permission in Play. A real new-version installation still needs device acceptance with a separately approved staged APK; do not create a production release or alert for testing. On-device acceptance: Settings → Check for updates → inspect available/current/error state; for an approved available candidate, Download update → verify progress → Install update → confirm Android installation. Repeat interruption, permission return and cancellation, checking that listening data survives.

## Isolated update and announcement testing

No production release or shared production alert is needed. Use a dedicated test phone where possible. updateTest keeps the normal package/signature: installation preserves listening data, but installing a higher-code test candidate can prevent returning to an older production APK without a downgrade. Plan the exit build before testing installation; never uninstall merely to bypass this.

```sh
./gradlew :app:assembleUpdateTest
cp app/build/outputs/apk/updateTest/app-updateTest.apk /tmp/boxlore-test-current.apk
./gradlew :app:assembleUpdateTest -PboxloreUpdateTestVersionCode=<current-code-plus-one>
cp app/build/outputs/apk/updateTest/app-updateTest.apk /tmp/boxlore-test-next.apk
adb install -r /tmp/boxlore-test-current.apk
adb reverse tcp:8765 tcp:8765
python3 .github/scripts/serve_update_test.py --apk /tmp/boxlore-test-next.apk --version-code <current-code-plus-one>
```

Install the current test APK, open boxlore, then Settings → Check for updates. The available icon appears only after a valid newer compatible result. Open it, download, pause/resume, and install. When Android opens Allow from this source, enable it yourself, return to boxlore, and tap Install update again. The screen and verified file remain; returning never auto-installs. Cancel Android installation and check that Install remains available. Process restart re-verifies the existing file; removing its cache file returns to Download without a new automatic transfer.

Restart the local server with `--state unavailable`, `--state incompatible`, or `--state corrupt` to test failures. Use Settings' manual check to bypass intervals. A manifest at the installed code should hide the icon. Integrity/signature/package/version checks must reject invalid candidates. The shipping app rejects testOnly/loopback manifests; Play bundles exclude the direct installer and install permission.

For a long release-note test, add `--notes-file /tmp/boxlore-test-notes.txt` with UTF-8 sample text. The fixture rejects notes beyond the app's 24,000 UTF-16 character or 64 KiB response bounds. Scroll the notes to the end and confirm the close control and download/install footer remain visible. The `/notes` link shows the same sample text. This does not publish release notes or send an alert.

For local visual review, export an announcement from the admin's Download preview, then run:

```sh
python3 .github/scripts/preview_announcement_on_device.py --preview /tmp/announcement-preview.json --serial <device>
```

This uses an ADB-only receiver present in updateTest and no FCM. The preview clears version eligibility locally so current devices can show its layout; delivery filtering is tested separately. Open the app and check Compact/Full screen, large text, light/dark, RTL, long titles, imagery and scrollable body. Release actions: Download update starts the native updater, View on GitHub keeps the message available on return, and Dismiss clears it. Permission/settings round-trips retain the updater.

For a real transport test after workflows/panel are deployed, keep Test mode on and audience Isolated test builds. First retain Validate without delivery, review and approve provider validation. Only then turn validation off, review the changed draft and approve one actual test_users send. Production builds neither subscribe to this audience nor accept test-only payloads. Test Ordinary alert versus Release, both presentations, installed-version suppression, explicit Play override and normal episode notification independence. Do not select all_users/prod_users for an isolated test.

Run `node --test .github/scripts/test_announcement_admin.cjs` and the Python suite for approval, payload bounds, audience restrictions, preview action semantics and font parity. Announcements render natively; verify preview style against phone screenshots. The optimized artifact gate rejects the isolated receiver/marker in shipping outputs. Report local/device/provider verification separately.

## Stack

- JUnit 5 (+ Vintage where leftovers remain)
- Turbine, MockWebServer, Robolectric
- Konsist (architecture guards in `:core:testing`)
- Shared fixtures / fakes: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`, `core.testing.fakes.*`)
- No MockK / Hilt
- Optional Roborazzi JVM screenshot goldens (not CI-gated)

## Coverage bars

| Layer | Bar | CI fail |
| :--- | :--- | :--- |
| JVM | Every public behavior-owning type has a suite (happy/empty/error/branches) or an irreducible exclusion | `testDebugUnitTest` |
| ViewModels | Behaviors via hermetic `logic/` + Settings Turbine; AndroidViewModels allowlisted when logic suites exist | unit job |
| Screenshots | Home settings goldens (local record/verify) | Manual / local only |
| Maestro | YAML present and well-formed | nightly validate |
| Kover merged | **≥ 80%** end state (ratchet **40 → 45 → 55 → 70 → 80**) | `:koverVerifyMerged` |
| Kover per-module | **≥ 70%** on logic-heavy modules (ratchet as suites land) | module verify |
| Architecture | ARCHITECTURE.md boundaries | scripts + `ArchitectureGuardTest` + dependencyGuard |
| New code | `*ViewModel` / `*Repository` need matching `*Test.kt` (allowlists for stubs / Media3 / hard AndroidViewModels) | `ArchitectureGuardTest` |

### Current Kover floor

| Target | Status |
| :--- | :--- |
| Merged floor ≥ **45%** on full gated set | Done (enforced by `:koverVerifyMerged`) |
| Measured merged line coverage | **≈ 47.9%** (13,358 / 27,869 lines) |
| Per-module ≥ 70% on logic modules | Yet to start |
| Merged floor ≥ 55% / 70% / **80%** | Yet to start (next ratchets) |

The CI floor is locked at **45** (never lower). Reaching **55+** requires more hermetic suites on remaining pure helpers. Media3-bound types (`PlaybackRepository`, queue/telemetry coordinators, `DownloadRepository`, `SmartDownloadManager`) stay on alternate layers (policy tests, `logic/` packages).

`:app` Compose nav / FCM / survey chrome is excluded from the line gate; see root [`build.gradle.kts`](../build.gradle.kts) `kover { }`.

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
| `PlaybackRepository` + `core.playback.service.*` / Auto | Policy unit tests |
| `@Composable` / `@Preview` | Manual (+ optional local Roborazzi) |
| `:app` `navigation.*` / `ui.*` / `fcm.*` / `surveys.*` | Manual / optional local Maestro |
| PostHog / Firebase SDK internals | Not our code; features must not import PostHog |
| Generated `R` / `BuildConfig` / databinding | Generated |

## Architecture CI (fail on deviate)

`unit-tests.yml` (PR / dispatch) fails when architecture drifts:

| Guard | What it enforces |
| :--- | :--- |
| `ArchitectureGuardTest` | No feature→feature Gradle deps or imports; catalog↛designsystem; catalog↛playback; catalog must not `api` analytics/ranking; module READMEs; `getInstance` allowlist; package=module (+ `core.data` stubs); no Hilt/Koin/Dagger/MockK; new `*ViewModel`/`*Repository` need matching `*Test.kt` |
| `GlossaryCoverageGuardTest` | `event_glossary.csv` ↔ `AnalyticsGlossary` allowlist; every event has `glossary_emission_coverage.csv` mode (`emission` / `sdk_backed` / `person_props_only`); no dual open/install volume |
| `GlossaryAllEventsEmissionTest` (`:core:analytics`) | Every `emission:` inventory row captures via façade; lifecycle helpers never emit `app_open` / `app_background` / `install_attributed` |
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
Use the root `detekt` task; individual modules do not all define one. These commands do not rewrite source files.

## Module × layer checklist

| Module | JVM | VM / logic | Notes |
| :--- | :--- | :--- | :--- |
| `:core:ranking` | Done | n/a | Repos, scorer, runtime controls |
| `:core:catalog` | WIP | n/a | Ports/consent/content Done; backup WIP |
| `:core:playback` | WIP | n/a | Queue/mixtape/policy Done; service excluded |
| `:core:downloads` | WIP | n/a | Candidate logic Done; Media3 manager Excluded |
| `:core:prefs` | Done | n/a | DataStore + migrator |
| `:core:database` | Done | n/a | In-memory DAOs |
| `:core:rss` | Done | n/a | Feed fixtures + helpers |
| `:core:analytics` | Done | n/a | Tracks + glossary + facade |
| `:core:network` | Done | n/a | MockWebServer contracts |
| `:core:domain` | Done | n/a | Port contracts |
| `:core:model` | Done | n/a | Behavior helpers |
| `:feature:home` | Done | Done | Settings Turbine + logic (+ optional Roborazzi goldens) |
| `:feature:info` | Done | Done | Port/logic suites |
| `:feature:explore` | Done | Done | Logic + Learn store |
| `:feature:library` | Done | Done | Sort/filter + download models |
| `:feature:onboarding` | Done | Done | Logic suites |
| `:feature:briefing` | Done | Done | Story text helpers |
| `:feature:player` | Done | n/a | v2 logic JVM |
| `:app` | WIP | n/a | Worker/push allowlists |

Application-backed Home/Info suites are **not** pursued; hermetic `logic/` + assembler/port suites replace them.

## Maestro

| Target | Status |
| :--- | :--- |
| Flow YAML under `maestro/` | Done |
| Nightly YAML validate | Done |
| Maestro Cloud device runs | Optional; requires configured service credentials |

See [`maestro/README.md`](../maestro/README.md).

## Screenshots

| Target | Status |
| :--- | :--- |
| Reserved `screenshots/baselines/` | Done |
| Checked-in PNG goldens (Add RSS, Reset analytics, Downloads) | Done |
| Roborazzi CI gate | Removed (local optional only) |

See [`docs/screenshots/README.md`](screenshots/README.md).

## CI

| Workflow | Runs | When | Status |
| :--- | :--- | :--- | :--- |
| `unit-tests.yml` | Architecture + detekt + unit + Kover + lint + Dependency Guard + Python release tests | PR / master push / dispatch | Done |
| `release-copy.yml` | Authored release regions and exactly one impact label | PR, including body / label edits | Configured |
| `release-rehearsal.yml` | Hermetic script tests and offline candidate previews | Manual | Configured |
| `coderabbit-threads-resolved.yml` | Fail unless all non-outdated CodeRabbit review threads are Resolved | PR / review | Done |
| `gitleaks.yml` | Secret scan | PR / push to master | Done |
| `maestro-nightly.yml` | Validate Maestro YAML; optional Cloud device job when configured | Nightly / manual | Done |

**Merge gate:** master uses a branch ruleset with no merge queue. Required checks are **`testDebugUnitTest`** and **`coderabbit-threads-resolved`**. SonarCloud, CodeRabbit, and Gitleaks also run on PRs. Fix all Sonar new-code issues and address every CodeRabbit finding, marking every review thread Resolved; the bare CodeRabbit status only confirms that the review completed.

The unit suite cancels prior in-progress runs on each PR push. `[skip unit]` in the PR title no-ops that job only for docs/chore changes with no logic risk; it still reports green. Actions → Run workflow always runs the full suite. If the review decision is `CHANGES_REQUESTED`, stop automated merging and have a maintainer handle the review or merge manually. Otherwise squash-merge after the required checks are green and review requirements are satisfied. A maintainer may explicitly authorize bypassing required status checks for a specific PR; report remaining checks and known risks first. General merge authorization does not authorize a bypass, and review requirements still apply. Repository-administration procedures belong in root `AGENTS.internal.md`.

Protected inputs: `app/google-services.json` is gitignored; CI writes a non-secret stub.

## Conventions

- Prefer constructor injection + fakes (`core.testing.fakes`) over `getInstance` in new tests.
- Hard ViewModels use assemblers + ports from `:core:domain` and Turbine + `MainDispatcherExtension` when constructible; otherwise exhaust `logic/` packages.
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners.
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures.
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` where required.
- Workers that need listen history use `HistoryRecommendationSource` / ports — not a second `PlaybackRepository`.

## Module README checklist

Every `app/`, `core/*/`, and `feature/*/` module keeps a folder README. Shape: [`MODULE_README_TEMPLATE.md`](MODULE_README_TEMPLATE.md). Konsist fails if an included module lacks `README.md`.

## Optimized release validation

Debug JVM tests do not run optimized DEX. `testReleaseUnitTest` also uses JVM classes before R8, so it cannot replace a release-artifact gate or device acceptance.

`release-contracts.yml` is an optional manual preflight: Actions → Manual optimized release validation → Run workflow, then select the branch to test. It builds unsigned optimized APK/AAB artifacts and runs release lint using the same release rules and resource shrinking, a build-only Firebase stub, no production signing key, and no Crashlytics mapping upload. It does not run automatically on PRs and does not publish a release or send notifications. Both signed build paths in `changelog-on-merge.yml` always run the contract gate before publication. Fast hermetic contract-regression tests remain in PR CI; existing unit/review merge requirements are unchanged.

The gate derives contracts from pre-R8 release class files and the merged manifest, then checks the actual APK **and** AAB DEX with the matching R8 mapping:

| Contract | Regression rejected |
| :--- | :--- |
| All concrete project workers and bundled input mergers | Renamed persisted classes; missing/private/wrong-argument constructors |
| Manifest components, startup initializers, Firebase registrars, Cast/credential providers | Missing runtime entry points or reflection constructors |
| Legacy service/provider aliases and both Room implementations | Broken upgraded-install or database initialization identities |
| Gson backup/cache/Room model graphs | Removed/renamed nested fields; erased generic field types; lost default-value constructors or enum constants |
| Concrete Gson TypeTokens | Lost generic superclass signatures |
| Every Retrofit API method | Erased generic response types or lost endpoint/parameter annotations |
| Dynamic API serializers and widget snapshot models | Lost `Companion` fields or public `serializer` methods |
| Dynamic onboarding covers, Android Auto label font and literal resource URIs | Required resource names removed from the APK resource table; named raw payloads absent or changed in APK/AAB |
| Debug-only App Check provider and test notification receiver | Development components accidentally included in release |

Gson roots are intentionally declared in `verify_release_contracts.py`; add a new reflective JSON entry point there when introducing one. Nested model/field additions, new workers, new manifest components, and new API methods are discovered from compiled release classes automatically. This compares optimization against current source; it does not detect an intentional source-level schema rename or prove SDK/network behavior. A successful build alone is never release acceptance.

```sh
./gradlew :app:assembleRelease :app:bundlePlayRelease :app:lintRelease :app:lintPlayRelease
python3 .github/scripts/verify_release_contracts.py --sdk <android-sdk> \
  --report app/build/reports/release-contracts.json \
  app/build/outputs/apk/release/app-release.apk
python3 .github/scripts/verify_release_contracts.py --sdk <android-sdk> --app-variant playRelease \
  --report app/build/reports/play-release-contracts.json \
  app/build/outputs/bundle/playRelease/app-playRelease.aab
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
```

Keep the release `mapping.txt`, effective `configuration.txt`, `seeds.txt`, `usage.txt`, lint output and gate report for diagnosis. The manual preflight preserves these as diagnostic artifacts; AABs also contain their mapping and signed release builds retain normal Crashlytics upload. Never upload signing keys, local properties or service configuration.

### Release-device acceptance

Install the signed release over the existing app without uninstalling or clearing data. Check:

1. Cold launch → Home: feed and artwork load; saved subscriptions remain; search/detail pages load.
2. Existing library → downloads: manual download runs. A genuine new release with auto-download enabled starts work; pending ledger entries can recover through existing foreground reconciliation. Avoid shared-topic test alerts unless explicitly authorized.
3. Settings → Account: sign-in, cloud refresh and a foreground sync complete; account reauthentication errors remain actionable.
4. Settings → backup: export/restore a controlled fixture containing subscriptions, history, folders, preferences and ranking state; old JSON keys/default values remain compatible.
5. Playback → Cast/Android Auto: providers initialize, playback controls and restored sessions work; warnings/errors remain available.
6. Installed widgets → force-stop/process restart: saved snapshots render; library/playback controls and deep links work. Onboarding covers and Auto collage labels retain their artwork/font.

Record device/runtime verification separately from optimized-artifact checks. Static checks cannot establish every vendor-ROM behavior, live SDK response, or background-delivery timing.
