# Boxlore — Cloud Agent Playbook (Next Wave)

**Audience:** Cloud / autonomous agents continuing work after modular hardening (A0–A8, B0–B10 scaffold).  
**Repo:** `ashwkun/boxlore` · default branch `master`  
**Baseline commit (verify before starting):** run `git rev-parse --short HEAD` and `git status` — expect a clean `master` tracking `origin/master`.  
**Product status:** App is shipping and working. Prefer **deepening shallow automation** and **safe refactors** over new mega-architecture. Do **not** reopen finished module extracts unless there is a concrete bug.

---

## 0. Why this playbook exists

The first hardening wave left the app healthy but left several items **looking done while still shallow**:

| Area | Looks done | Reality |
| :--- | :--- | :--- |
| Maestro | Nightly workflow + 3 YAML flows | Almost every assert is `optional: true`; Cloud job never runs without secrets (owner will **not** add secrets) |
| Screenshots | `screenshots/baselines/` + stub test | **Zero** PNG goldens; stub uses `onRoot()` and fails on device (dual Compose roots) |
| Instrumented | Required CI check | **Only** `:feature:home` · 3 files · hermetic dialogs only |
| Kover | Gate in CI | **12%** floor on **3 modules only** (`data`+`domain`+`home`); ~25.6% actual — analytics/rss/downloads have plugin but are **not** in the merge gate |
| Home/Info VMs | “B2 advanced” | Logic/port tests exist; **full ViewModel construction** still deferred |
| Nav / UI fat | MainActivity thin | Complexity moved to `BoxLoreNavHost`, huge screens/VMs |

**This playbook’s rule:** a phase is **not done** until its **Definition of Done** checklist is 100% checked, verified on device when UI-touched, and documented. Scaffold-only PRs that claim “B6 done” / “screenshots done” are **rejected**.

---

## 1. Absolute constraints (never violate)

### 1.1 Product / identity (breaking these ships broken installs)

| Keep stable | Why |
| :--- | :--- |
| `applicationId = cx.aswin.boxlore` | Play / sideload identity |
| DataStore name `user_preferences` | User prefs |
| Room DB filename(s) | User library |
| `rss:` / negative IDs, mediaId / cache key schemes | Playback + library integrity |
| SharedPreferences keys `boxcast_*` (via façades) | Existing installs |
| WorkManager worker FQCNs + `LegacyWorkerFactory` aliases | Scheduled work survives upgrades |
| `BoxLorePlaybackService` / download service Manifest names | System bindings |

### 1.2 Architecture (already enforced — don’t regress)

- **Manual DI only** — `AppContainer` + holders. **No Hilt / Koin / MockK** unless this doc is amended by a human.
- **One composition graph** — never construct a second ranking/RSS/playback/prefs graph in features/workers.
- **No feature → feature** Gradle edges (Konsist).
- **`:core:data` ↛ designsystem** (Konsist).
- Features must not touch `BoxLoreDatabase` directly (`scripts/ci/check-feature-no-boxlore-database.sh`).
- **One UI `PlaybackRepository`**; Smart Queue refill remains service-owned.
- Gradle module id ≠ Java package is **intentional** (A8). Do not mass-rename packages to match Gradle ids.

### 1.3 Process

- Conventional Commit PR titles; **exactly one** `user-impact-*` label; optional `backend-change`.
- Expensive CI runs only with **`merge-ci`** label (see `.github/PULL_REQUEST_TEMPLATE.md`).
- **Do not** add Maestro Cloud secrets. Owner uses **local Maestro only**.
- **Do not** enable Dependabot version-update PRs / autosubmit spam.
- **Do not** open mega-PRs (>~150 files or mixed themes). One phase = one PR (or a tightly stacked pair).
- After UI/app-behavior changes: `./gradlew installDebug` on a connected device when available (Gradle with real `GRADLE_USER_HOME=/Users/aswinc/.gradle`, no sandbox).
- **Hard stop** after each phase: paste verification output + wait for human **OK** before the next phase. No silent chaining of 5 phases.

### 1.4 Tooling commands (copy-paste)

```bash
unset GRADLE_USER_HOME
export GRADLE_USER_HOME="/Users/aswinc/.gradle"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/Users/aswinc/Library/Android/sdk}"

./gradlew compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :koverVerifyMerged
./gradlew detekt ktlintCheck
./gradlew :feature:home:connectedDebugAndroidTest   # device/emulator
./gradlew installDebug                               # when UI changed
```

CI stub for google-services is workflow-only; locally use real `app/google-services.json` (gitignored).

---

## 2. Current baseline (agent must re-verify on day 1)

Run and record:

```bash
git fetch origin && git checkout master && git pull --ff-only
git rev-parse --short HEAD
git status -sb
./gradlew :koverXmlReportMerged --quiet
# parse LINE covered% from build/reports/kover/reportMerged.xml
find . -path '*/src/androidTest/**/*Test.kt' -not -path '*/build/*' | wc -l
find maestro -name '*.yaml' | wc -l
ls screenshots/baselines/
```

Expected shape (update numbers in your kickoff note if drifted):

- **21** Gradle modules; packages under `cx.aswin.boxlore.*`
- **~62** JVM `*Test.kt` files; androidTest **only** in `:feature:home` (3 files)
- Maestro: 3 YAML; Screenshots: empty baselines
- Kover merge: `data`+`domain`+`home`, floor **12%**, measured ~**25%** lines
- Workflows: `unit-tests.yml` + `android-instrumented-tests.yml` gated by `merge-ci`

If baseline differs wildly, **stop** and ask the human — do not invent a new architecture.

---

## 3. Phase machine (execute in order)

Each phase below has: **Goal · Scope · Forbidden · Steps · Verification · DoD · PR · Hard stop**.

---

### Phase C0 — Stabilize instrumented CI (unbreak the required check)

**Goal:** Required instrumented job is trustworthy on emulator **and** physical device.

**Scope (only):**
- `feature/home/src/androidTest/**`
- Possibly tiny `testTag` additions in Home settings Compose (if missing)
- `docs/TESTING.md` + `feature/home/README.md` Testing notes

**Forbidden:**
- New features, module moves, Maestro Cloud, Roborazzi plugin, Kover floor changes

**Steps:**
1. Reproduce: `./gradlew :feature:home:connectedDebugAndroidTest` on emulator and/or device.
2. Fix `AddRssFeedDialogScreenshotStubTest` dual-root failure (`onRoot()` / `assertIsDisplayed` on multi-window `AlertDialog`). Prefer **tags-only** assertions like `AddRssFeedDialogUiTest`. Rename or document as “composition smoke,” not “screenshot.”
3. Ensure all **6** existing tests are green, or delete/replace the stub with an equivalent green smoke that doesn’t flake.
4. Confirm `android-actions/setup-android` (or equivalent) remains in `android-instrumented-tests.yml` so CI `sdkmanager` works.

**Verification:**
- [ ] `./gradlew :feature:home:connectedDebugAndroidTest` exit 0
- [ ] HTML report shows 0 failures
- [ ] `compileDebugKotlin` green

**DoD:** Required instrumented check is honest; no known failing test left “for later.”

**PR:** `fix(test): stabilize home instrumented suite` · label `no-user-impact` · add `merge-ci` only when ready · wait for Unit + Instrumented green.

**Hard stop:** Human OK.

---

### Phase C1 — Kover ratchet 12 → 15 (same three modules)

**Goal:** Raise the floor without widening scope yet.

**Scope:**
- `build.gradle.kts` (`minBound(15)`)
- `docs/TESTING.md`, plan status line if present
- Module READMEs that mention the 12% floor

**Forbidden:**
- Adding modules to the merge gate in this PR
- Mass new tests “to chase 15%” unless measured coverage is somehow below 15% (today ~25% — should be a one-line bump)

**Steps:**
1. Confirm current `:koverVerifyMerged` passes at 12% and report actual %.
2. Change `minBound(12)` → `minBound(15)`.
3. Update docs ratchet text: current floor **15**, next **25**.
4. Run `:koverVerifyMerged`.

**Verification:**
- [ ] `:koverVerifyMerged` passes
- [ ] Docs match Gradle

**DoD:** CI will fail if coverage on the gated trio drops under 15%.

**PR:** `chore(test): raise Kover merged floor to 15%` · `no-user-impact` · `merge-ci` when ready.

**Hard stop:** Human OK.

---

### Phase C2 — Widen Kover merge (optional soft) — analytics + rss + downloads

**Goal:** Coverage gate reflects modules that already have the Kover plugin and real unit tests.

**Scope:**
- Root `build.gradle.kts` `kover(projects…)` list
- Measure **before** raising floors further
- Docs + READMEs for newly gated modules

**Forbidden:**
- Adding player/playback/ranking to the gate until each has Kover plugin + measured coverage
- Setting floor so high that CI becomes flaky (if merged % drops, **lower or keep 15**, don’t silently weaken tests)

**Steps:**
1. Record line % for current 3-module merge.
2. Add `kover(projects.core.analytics)`, `kover(projects.core.rss)`, `kover(projects.core.downloads)`.
3. Generate XML report; compute new merged %.
4. Keep `minBound(15)` if new % ≥ 15; if new % < 15, **stop** and either add targeted tests in those modules or abort widen — do not ship a failing gate.
5. Update rule name string and `docs/TESTING.md`.

**Verification:**
- [ ] `:koverXmlReportMerged` + `:koverVerifyMerged` green
- [ ] Documented module list matches Gradle

**DoD:** Gate modules listed in docs == Gradle `kover(...)` deps; floor still meaningful.

**PR:** `chore(test): include analytics/rss/downloads in Kover merge` · `no-user-impact`.

**Hard stop:** Human OK. If human prefers skip, mark cancelled and jump to C3.

---

### Phase C3 — B2 deepen: Home / Info without Application bootstrap

**Goal:** More real behavior tests without dragging full `Application` / Media3.

**Scope:**
- Extract **one** pure helper or port-facing slice from Home **or** Info per PR (max two small PRs)
- Turbine / JUnit5 tests with fakes from `:core:testing`
- Touch only the feature under test + its README Testing notes

**Forbidden:**
- Rewriting `feature/player` `v2/logic` behavior
- Constructing `HomeViewModel` / `PodcastInfoViewModel` with real `PlaybackRepository` / Room in unit tests
- “While I’m here” NavHost refactors

**Good targets (pick from evidence, don’t invent):**
- Additional Home feed / affinity / greeting edge cases
- Info catalog error / offline merge paths (extend existing port tests)
- Settings assembler behaviors not yet covered

**Verification:**
- [ ] `./gradlew :<module>:testDebugUnitTest` green
- [ ] `./gradlew testDebugUnitTest` green (or at least affected modules + `:core:testing`)
- [ ] No new detekt/ktlint baseline spam without justification

**DoD:** New tests fail if the production helper is broken (prove by temporarily breaking once locally, then revert). README Testing notes list the new class.

**PR:** `test(home|info): …` · usually `no-user-impact` unless behavior changed.

**Hard stop:** Human OK. May repeat C3 once more if human requests — still one concern per PR.

---

### Phase C4 — B5 expand instrumented (still `:feature:home` only)

**Goal:** One more **hermetic** Compose UI suite with stable `testTag`s.

**Scope:**
- One additional Settings page or dialog already structured for tags (pattern: `DownloadsSettingsPageUiTest`)
- Tag constants documented in `docs/TESTING.md` + `feature/home/README.md`

**Forbidden:**
- Full-app nav instrumentation
- Flaky `Thread.sleep` / `waitForIdle` abuse without idling resources
- Starting Maestro Cloud
- Touching player/explore/onboarding androidTest (create modules later only with human OK)

**Steps:**
1. Identify a settings surface with clear controls.
2. Add `testTag`s if missing (production Compose change is OK if tiny and documented).
3. Write hermetic `createComposeRule` tests (fakes / no network).
4. Run on device: `connectedDebugAndroidTest`.

**Verification:**
- [ ] New tests green on device/emulator
- [ ] Entire `:feature:home:connectedDebugAndroidTest` green
- [ ] `installDebug` if production UI tags changed

**DoD:** ≥1 new meaningful UI behavior covered (not a duplicate of Add RSS). Tags listed in docs.

**PR:** `test(home): instrumented coverage for …` · `no-user-impact` or `user-impact-low` if tags only.

**Hard stop:** Human OK.

---

### Phase C5 — Maestro: make **one** free local flow real

**Goal:** Local-only Maestro becomes a useful smoke, not theater.

**Scope:**
- Exactly **one** flow file (prefer `maestro/smoke_launch.yaml`)
- `maestro/README.md` + `docs/TESTING.md`
- Optional: debug “skip onboarding / land on home” hook **only if** required and gated by `BuildConfig.DEBUG` / debug preference — no release behavior change

**Forbidden:**
- Adding Cloud secrets or requiring Cloud in CI
- Making all three flows strict in one PR
- Nightly cron changes that require paid minutes (cron may stay; Cloud job must remain skipped without secrets)

**Steps:**
1. Run locally: `./gradlew :app:installDebug && maestro test maestro/smoke_launch.yaml`.
2. Remove `optional: true` from assertions that are stable on a fresh debug install **or** document a single prep step (adb clear + debug flag).
3. Keep other flows as optional scaffolds until a later phase.
4. Ensure nightly still passes with **validation-only** when secrets absent.

**Verification:**
- [ ] `maestro test maestro/smoke_launch.yaml` exit 0 on a connected device (agent or human)
- [ ] README states: local-only, no Cloud keys, exact prep steps
- [ ] Nightly validate job still green without secrets

**DoD:** One flow is **strict** and reproducible. No “optional everything.”

**PR:** `test(maestro): harden smoke_launch for local runs` · `no-user-impact`.

**Hard stop:** Human OK. Default **skip Cloud**. Do not schedule more nightlies.

---

### Phase C6 — Screenshots: honest status (no fake “done”)

**Goal:** Either a **real** minimal golden path **or** remove misleading “screenshot test” naming — no half states.

**Pick one track (human chooses at hard-stop of C5):**

**Track A — Defer Roborazzi (recommended if AGP 9 still blocks):**
1. Delete or rewrite stub so it is clearly “composition smoke,” not screenshot.
2. Update `docs/screenshots/README.md`: “baselines reserved; Roborazzi not enabled; do not claim P26 complete.”
3. Keep empty `screenshots/baselines/.gitkeep`.

**Track B — Enable Roborazzi (only if agent verifies AGP 9 compatibility with a spike PR):**
1. Spike in a throwaway branch: one composable golden.
2. If spike fails AGP tooling — abort Track B, fall back to A (do not merge broken plugin).
3. If spike works — one golden under `screenshots/baselines/`, CI job optional/manual first (not required check).

**Forbidden:** Claiming screenshot automation complete without PNG baselines checked in.

**DoD:** Docs and code agree; no failing stub in required instrumented suite.

**Hard stop:** Human OK.

---

### Phase C7 — Fat-file surgical extract (optional, high risk)

**Goal:** Reduce cognitive load **without** behavior change.

**Allowed targets (one per PR):**
- Extract pure helpers from `BoxLoreNavHost` **or**
- Extract a composable section from a huge screen **or**
- Split a ViewModel helper already covered by tests

**Forbidden:**
- Behavior changes “while extracting”
- Cross-cutting redesigns
- Touching playback service FQCNs / worker classes

**Verification:**
- [ ] Unit tests for extracted helpers
- [ ] `installDebug` + human smoke of affected flow
- [ ] detekt complexity improved or neutral

**DoD:** Diff is reviewable; product behavior identical; README/ARCHITECTURE updated if ownership moved.

**Hard stop:** Human OK. Skip entirely unless human asks.

---

### Phase C8 — A8 polish (cosmetic only, evidence-gated)

**Goal:** Optional renames that do **not** break identity.

**Allowed in separate tiny PRs:**
- `BoxCastTheme` → `BoxLoreTheme` (with typealiases if needed for one release)
- Log tag string cleanups that are not pref keys

**Forbidden in this wave:**
- Renaming `BOXCAST_*` BuildConfig without dual-read + docs for local.properties
- Removing `boxcast://` deep links without redirect aliases
- Deleting `LegacyWorkerFactory` without proving zero persisted `boxcast` worker names in the wild

**DoD:** App installs; deep links still open; workers still schedule.

**Hard stop:** Human OK. Default **skip** unless requested.

---

## 4. Explicitly out of scope (do not start)

- Hilt / Koin / MockK introduction
- `:core:data` → `:core:catalog` Gradle rename (unless human orders it as its own playbook)
- Org transfer / merge-queue migration
- Re-enabling Dependabot PR spam
- Maestro Cloud paid usage
- Full Home/Info VM tests that boot real Application + Media3 (needs a dedicated design spike first)
- Rewriting player `v2/logic` “for cleanliness”
- Parallel service-locator revival

---

## 5. PR & merge ritual (every phase)

1. Branch from latest `master`: `fix/…` or `test/…` or `chore/…`
2. Implement **only** that phase’s scope.
3. Local gates: compile + relevant tests + (if UI) `installDebug`.
4. `gh pr create` with Conventional title, **one** user-impact label, body using `.github/PULL_REQUEST_TEMPLATE.md` (includes Merge CI section).
5. Iterate without `merge-ci`.
6. When ready: `gh pr edit <n> --add-label merge-ci`
7. Wait for required checks: `testDebugUnitTest` + `feature:home connectedDebugAndroidTest`
8. Squash-merge after green (human may admin-bypass only for infra flakes they accept).
9. Paste phase report → **HARD STOP**.

### Phase report template (paste to human)

```text
Phase: C#
Commit/PR: …
Files touched: …
Verification commands + exit codes:
Device install: yes/no (model …)
DoD checklist: all [x] or list gaps
Risks / follow-ups:
STOP — waiting for OK to start C#(next)
```

---

## 6. Risk register (read before every phase)

| Risk | Mitigation |
| :--- | :--- |
| Break playback after “cleanup” | Don’t touch `PlaybackRepository` / service unless phase explicitly includes tests + install smoke |
| Worker class rename | Never without `LegacyWorkerFactory` + release note |
| Pref key drift | Only `BoxcastPrefs` / prefs façades |
| CI green but app broken | `installDebug` + human smoke for UI phases |
| Half-baked Maestro/screenshots | Phase DoD forbids optional-everything and empty goldens billed as done |
| Coverage gate flake | Measure before widen; don’t set floor above measured % − 3pp headroom |
| Huge NavHost edits | Prefer C7 single extract; otherwise leave alone |

---

## 7. Suggested first message to the cloud agent

```text
Execute docs/PLAN_CLOUD_AGENT_NEXT.md starting at Phase C0 only.

Rules:
- Hard stop after C0 DoD + PR merged (or ready).
- Do not chain phases without my OK.
- No Hilt/MockK, no Dependabot, no Maestro Cloud secrets.
- Preserve all identity/FQCN/prefs invariants in §1.1.
- Follow merge-ci labeling in the PR template.
- App must still install and play podcasts after any UI-touching change.

Kickoff: verify §2 baseline, then C0.
```

---

## 8. Success criteria for this wave

This wave is successful when:

1. Instrumented required check is **reliably green** (C0).
2. Kover floor is **≥15%** on an honest module set (C1, optionally C2).
3. Home/Info have **deeper** unit coverage without Application hacks (C3).
4. At least **one** new hermetic instrumented surface beyond Add RSS (C4).
5. **One** Maestro flow is locally strict and documented (C5).
6. Screenshot story is **honest** (C6).
7. Optional extracts/polish only if human requested (C7–C8).
8. App still works end-to-end; no identity breaks.

Anything that re-labels scaffold as “complete” without these bars fails the wave.
