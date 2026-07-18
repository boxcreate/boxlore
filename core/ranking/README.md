# `:core:ranking`

Adaptive recommendation and candidate-scoring engine for Boxlore.
Extracted from `:core:data` in Phase A5 of the modular-hardening plan.

## Purpose

Provides personalised ranking of podcasts and episodes through a contextual-bandit approach:

- **Bayesian facet preferences** (show, genre, source) that decay over time (`BayesianPreferenceFacet`)
- **LinUCB linear model** that learns from resolved exposures (`AdaptiveLinearModel`, `AdaptiveRankingRepository`)
- **Diversity re-ranking** with per-show caps and novelty reservation (`DiversityReranker`, `DiversityPolicy`)
- **Action/exposure feedback loop** (`RankingFeedbackRepository`) — implements `RankingResetPort` from `:core:domain`
- **Shadow diagnostics** and learner audit log for debugging (`RankingShadowDiagnostics`, `LearningEventLog`)

## Own Room database

`AdaptiveRankingDatabase` (filename **`adaptive_ranking_database`**) is private to this module.
It holds three tables: `adaptive_models`, `preference_facets`, `ranking_exposures`.

**Backup exclusion:** the ranking DB is excluded from cloud-backup (`android:allowBackup` is not set
for this module's manifest; the app-level backup rules exclude `adaptive_ranking_database` to avoid
restoring stale model parameters that no longer match the installed feature-schema version).

## Debug surfaces

| Surface | Entry point |
|---|---|
| Learner inspector | `AdaptiveRankingRepository.learnerInspector()` |
| Shadow diagnostics | `RankingShadowDiagnostics.snapshots()` |
| Learning event log | `LearningEventLog.events` (StateFlow, session-only) |
| Runtime controls | `RankingRuntimeControls.getInstance(context)` |
| Rollout policy | `RankingRolloutPolicy.isEnabledByDefault(surface)` |

## Public API

| Type | Role |
|---|---|
| `AdaptiveCandidateScorer` | Score podcasts / episodes lists for home, explore, queue, downloads |
| `AdaptiveRankingRepository` | Model state, exposure recording, facet affinities, backup/restore |
| `RankingFeedbackRepository` | Record actions (play, like, skip, subscribe) and resolve exposures |
| `RankingObjective` | `YOUR_SHOWS`, `DISCOVERY`, `CONTINUATION`, `OFFLINE`, `SLATE` |
| `RankingSurface` | `HOME`, `EXPLORE`, `LIBRARY`, `QUEUE`, `DOWNLOADS`, `ANDROID_AUTO` |
| `CandidateSource` | `SUBSCRIPTION`, `LOCAL_HISTORY`, `SERVER_RECOMMENDATION`, … |
| `RankingAction` / `RankingOutcome` / `RankingReward` | Reward calculation |
| `DiversityPolicy` / `DiversityReranker` | Post-scoring diversity pass |
| `AdaptiveRankingBackup` | Portable model state for backup/restore |

## `getInstance` rule (production)

`AdaptiveCandidateScorer.getInstance`, `AdaptiveRankingRepository.getInstance`, and
`RankingFeedbackRepository.getInstance` **must only be called from `AppContainer`**.
Workers and services must consume ranking via `SharedAppDependenciesHolder.require()`.

## Dependencies

```
:core:ranking
  → :core:model      (Episode, Podcast, PodcastGenres, RankingAggregateTelemetry)
  → :core:database   (PodcastScoring, ScorablePodcast, ListeningHistoryEntity)
  → :core:domain     (RankingResetPort)
  → :core:prefs      (BoxcastPrefs — learner log gate key)
  + Room 2.8 / ksp   (AdaptiveRankingDatabase codegen)
```

Ranking does **not** depend on `:core:data`. `:core:data` depends on `:core:ranking` (api-exported)
to avoid a cycle and to re-export ranking types to features/downloads/playback transitively.

## Testing

```bash
./gradlew :core:ranking:testDebugUnitTest
```

All tests are pure JVM unit tests (no Robolectric / instrumented). The Room DAO layer is covered
indirectly through `AdaptiveRankingRepository` integration-style tests.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/recommendation-system.md`](../../docs/recommendation-system.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A5)
- [`:core:data` README](../data/README.md)
- [`:core:domain` README](../domain/README.md)
