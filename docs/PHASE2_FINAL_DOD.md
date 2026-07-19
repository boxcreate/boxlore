# Phase 2 Final — Definition of Done

Signed off with PR10 (`:core:catalog` → `cx.aswin.boxlore.core.catalog`). After this document is green, **Phase 3 = testing only** — no architecture / modular mega-plan.

## A. Structure

| # | Criterion | Status |
|:--|:--|:--|
| 1 | 21-module map with `:core:catalog` | Done |
| 2 | Thin catalog (smart-queue in playback) | Done |
| 3 | Narrow catalog `api`; features declare ranking/analytics deps | Done |
| 4 | PostHog only via `:app` / `:core:analytics` | Done (`check-feature-no-posthog.sh`) |
| 5 | `AppContainer` sole composition root | Done |

## B. Package = module

| # | Criterion | Status |
|:--|:--|:--|
| 6 | Every extracted `:core:*` main sources use matching package root | Done (see migration map) |
| 7 | Failsafes live: `LegacyWorkerFactory` + old-FQCN stubs + dual ProGuard + map | Done (permanent) |
| 8 | Konsist package=module with stub allowlist | Done (`ArchitectureGuardTest`) |
| 9 | Pref files via `PrefsFileMigrator`; BuildConfig dual-read; deep-link dual schemes; Room filenames unchanged | Done |

## C. Fat-file caps

| # | Criterion | Status |
|:--|:--|:--|
| 10 | No main Kotlin file >1500 LOC (allowlist: `PodcastIndexModels` only) | Done (PR10 LOC extracts) |
| 11 | Listed orchestrators ≤1000 LOC | Verified in prior extract PRs + PR10 recheck |

## D. Automation

| # | Criterion | Status |
|:--|:--|:--|
| 12 | Kover merge includes playback; floor ≥20% | Done (`minBound(20)`) |
| 13 | Tests: HomeVM, Info/Episode, Onboarding, PlaybackRepository, RssFeedClient | Done |
| 14 | ≥1 androidTest beyond Settings-only; Maestro primary asserts strict | Done |
| 15 | Every Phase 2 PR used **`merge-ci`** and went green | Done |

## E. Docs / bugs

| # | Criterion | Status |
|:--|:--|:--|
| 16 | No `:core:catalogbase`; READMEs state merge-ci; ARCHITECTURE package=module | Done |
| 17 | Cold-start deep links, FCM `target_route`, onboarding bugs fixed | Done (PR1) |
| 18 | PLAN_MODULAR §11 = **Final**; Phase 3 = testing only | Done |

## F. Analytics

| # | Criterion | Status |
|:--|:--|:--|
| 19 | Insight + event map + glossary (MD+CSV) committed | Done |
| 20 | Client events replaced by glossary Phase A+B; allowlist test | Done |
| 21 | Phase C Auto (+ polish) live; glossary Phase C rows complete | Done (PR9) |
| 22 | Features no direct PostHog; Must dashboards rebuildable; AI + search raw text logged | Done |

### Must-dashboard rebuild checklist (glossary tags)

Documented in [`ANALYTICS_EVENT_GLOSSARY.md`](ANALYTICS_EVENT_GLOSSARY.md) — rebuild PostHog on **new** event names (no CI click-through required):

1. **company_health** — DAU/WAU/MAU (`app_open`); hours from playback pause/complete
2. **activation** — onboarding → `activated_at` / first `playback_started` ≤24h
3. **entry_point** — `playback_started.entry_point` (retained cohort) + normalizer aliases
4. **playback** — start → milestone → complete; require `entry_point` on all `playback_*`
5. **discovery** — `home_surface_tapped` / `search_performed` / `learn_card_action` → play
6. **growth** — `install_attributed` + `deep_link_opened` + `share_content`
7. **library** — queue/downloads/library destinations
8. **delight / feedback** — `feedback_submitted`
9. **auto** — connect/browse + `client_surface=android_auto`

## Permanent exceptions (not unfinished)

- `LegacyWorkerFactory` + old FQCN stubs forever
- Pref old XML may remain on disk after migrator copy
- No Hilt; no Maestro Cloud required
- Full Auto E2E / all Room migrations = non-blocking product debt
- Analytics Phase C “Nice” extras remain documented follow-ups inside the event map — not a Phase 3 architecture wave

## Phase 3 boundary

| In Phase 3 | Forbidden in Phase 3 |
|:--|:--|
| Coverage depth, E2E/Maestro, CI hardening | New Gradle modules / DI frameworks |
| Bugs found by tests | Package renames / analytics taxonomy redesign |
| Flaky CI fixes | “One more modular mega-plan” |
