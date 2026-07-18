# Testing

## Layers

| Layer | Command / location | Catches |
| :--- | :--- | :--- |
| JVM unit | `./gradlew testDebugUnitTest` | Logic / state bugs |
| Compose UI | `androidTest` (P24) | Dead controls, nav wiring |
| Maestro | device flows (P25) | Real-device glitches |
| Screenshots | optional (P26) | Visual regressions |

## Stack

- **JUnit 5** (+ Vintage during migration leftovers)
- **Turbine**, **MockWebServer**, **Robolectric**
- **Kover** plugin available (thresholds in harden phase)
- Shared fixtures: `:core:testing` (`TestFixtures`, `MainDispatcherExtension`)
- **No MockK / Hilt**
- Compose **androidTest** uses **JUnit4** + `AndroidJUnitRunner` (androidx.test)

## Compose UI tests (P24)

Hosted in `:feature:home` so dialogs can be composed without app DI.

Stable `testTag`s:

- `home_settings_button` — home top-bar Settings
- `settings_add_rss_url` / `settings_add_rss_confirm` / `settings_add_rss_cancel` — Add RSS dialog

Compile instrumentation sources (no emulator required):

```bash
./gradlew :feature:home:compileDebugAndroidTestKotlin
```

Run on a connected device/emulator (local only; not wired into CI):

```bash
./gradlew :feature:home:connectedDebugAndroidTest
```

## Maestro smoke (P25)

See `maestro/README.md`.

```bash
./gradlew :app:installDebug
maestro test maestro/
```

## Screenshot baselines (P26)

Optional, local-only. See `docs/screenshots/README.md` and `screenshots/baselines/`.
No Roborazzi/Papyrus plugin is required for the current scaffolding.

## CI

GitHub Actions workflow `unit-tests.yml` runs `testDebugUnitTest` on PRs and pushes to feature branches.
androidTest / Maestro / screenshots stay **local** unless explicitly added later.

Protected inputs:
- `app/google-services.json` is **gitignored** and must never be committed.
- The real file is a **release-environment** secret (`GOOGLE_SERVICES_JSON_BASE64` on GitHub Environment `release`). Ordinary PR/push jobs cannot read it.
- The unit-test workflow writes a non-secret CI stub only so the Google Services plugin can configure `:app`, then deletes it. Release workflows keep using the real secret.
- Locally, keep using your usual `.env` / local `app/google-services.json` — unchanged.

## Conventions

- Prefer constructor injection + fakes over `getInstance` in new tests
- Hard ViewModels (Home/Settings/Info) use assemblers + thin ports from `:core:domain` (`RssSubscriptionPort`, `RankingResetPort`, `PodcastCatalogPort`) and Turbine
- Do not rewrite `feature/player` `v2/logic` behavior when migrating runners
- Keep DataStore name `user_preferences`, DB filename, and `rss:` / negative IDs stable in fixtures
- Room/Robolectric DAO tests need `unitTests.isIncludeAndroidResources = true` and compileSdk ≥ 36 (blocked by current AAR metadata pins); RSS ID fixtures cover the RSS identity rules until then
- Workers that need listen history for recommendations use `HistoryRecommendationSource` / `DefaultSmartQueueSources`, not a second `PlaybackRepository`
