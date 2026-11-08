# `:core:ranking`

## Purpose

Owns adaptive recommendation and candidate scoring: Bayesian facet preferences, online linear models, diversity re-ranking, exposure tracking, rewards, runtime controls, diagnostics and ranking's own Room database.

It does not own the main database, Podcast Index networking, content retrieval, playback services or feature UI. Listening and freshness signals (`PodcastScoring`, `ScorablePodcast`) come from [`:core:database`](../database/README.md).

## Public API

- `AdaptiveCandidateScorer`: scores shows and episodes for Home, Explore, the queue and downloads (`scorePodcasts`, `rankPodcasts`, `scoreEpisodes`); built with `AdaptiveCandidateScorer.create`.
- `YourShowsScorer`: the one deterministic `YOUR_SHOWS` path on every surface; log-normalized listening, freshness and preference signals plus a single subscribe-recency floor, with no exposure training.
- `YourShowsSubscriptionRecency`: the bounded floor for newly followed shows, high for a short window and then decaying, so listening habits take over without a cliff.
- `RankingHistory`: listening history with each episode counted once across its IDs; `forShowEpisode` finds a show's latest-episode history within its `listenerShowIds`.
- `AdaptiveRankingRepository`: ranking state, exposure recording, facet affinities, `exportBackup` / `restoreBackup`, and `migrateShowFacet` for show ID repairs.
- `RankingFeedbackRepository`: records listener actions and implements `RankingResetPort`.
- `RankingRuntimeControls`: runtime switches for ranking surfaces.
- `RankingObjective`, `RankingSurface`, `CandidateSource`: scoring context.
- `RankingAction`, `RankingOutcome`, `RankingReward`: feedback and reward semantics.
- `DiversityPolicy`, `DiversityReranker`: reshape scored lists for variety.
- `AdaptiveRankingBackup`: the backup model of models, facets and exposures.
- `LearningEventLog`, `RankingShadowDiagnostics`: in-process learning log and diagnostics.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/ranking/
  AdaptiveCandidateScorer.kt
  AdaptiveLinearModel.kt
  AdaptiveRankingRepository.kt       # repository and AdaptiveRankingBackup
  BayesianPreferenceFacet.kt
  LearningEventLog.kt
  RankingFeedbackRepository.kt
  RankingModels.kt                   # objectives, surfaces, sources, DiversityPolicy, DiversityReranker
  RankingHistory.kt
  RankingReward.kt
  RankingRuntimeControls.kt
  RankingSerialization.kt
  ShowFacetMigrationLogic.kt
  YourShowsScorer.kt
  YourShowsSubscriptionRecency.kt
  database/
    AdaptiveRankingDao.kt
    AdaptiveRankingDatabase.kt
    AdaptiveRankingEntities.kt
```

## Dependencies

- `implementation`: `:core:model`, `:core:database`, `:core:domain`, `:core:prefs`, Room runtime and KTX (compiler through KSP), AndroidX core, coroutines.
- Forbidden: catalog, playback, downloads, designsystem, analytics or feature modules.

## Threading / lifecycle

- Production instances are application-scoped and created in `AppContainer`.
- Room and model updates run through `suspend` APIs on background dispatchers.
- Scorers are called from UI, service and worker paths; APIs that touch stored state stay `suspend`.
- `LearningEventLog` is process state, not a durable log. `BoxLoreApplication` gates it with `BoxcastPrefs.resolveLearnerLogEnabled` (off in release unless explicitly turned on).

## Persistence & identity

- Room file `adaptive_ranking_database` stores models, preference facets and exposures. It exports no schema and rebuilds empty on a version downgrade (`fallbackToDestructiveMigrationOnDowngrade`); ranking relearns or restores from a backup.
- SharedPreferences file `adaptive_ranking_runtime` stores runtime control values.
- The database is included in Android backup with the other databases, and `AdaptiveRankingBackup` travels in `:core:catalog`'s library backup.
- Show facet keys follow the active show ID; `migrateShowFacet` moves or merges evidence to the new key in one transaction.
- An episode stored under several IDs counts once: `AdaptiveCandidateScorer.create` takes `episodeTokens` (`EpisodeListenerStore.groupTokens`, wired in `AppContainer`). Rules: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone).
- A show switched to its feed keeps listening under its old Podcast Index show; show scores count those rows through `ScorablePodcast.listenerShowIds`, and a row of an unrelated show sharing an ID never counts.
- Package root `cx.aswin.boxlore.core.ranking` is stable. Storage identities: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage).

## Testing notes

Tests live in `core/ranking/src/test` (JUnit 5, Robolectric Room). Feature tests exercise reset through `RankingResetPort` fakes.

| Area | Test classes |
| :--- | :--- |
| Adaptive learning | `AdaptiveRankingTest`, `RankingFeedbackRepositoryTest`, `RankingRuntimeControlsTest` |
| Candidate scoring and episode IDs | `AdaptiveCandidateScorerTest`, `RankingHistoryTest` |
| Your Shows | `YourShowsScorerTest`, `YourShowsSubscriptionRecencyTest` |
| Storage, backup and facet repair | `AdaptiveRankingRepositoryTest`, `ShowFacetMigrationLogicTest`, `AdaptiveRankingDatabaseDowngradeTest` |

```bash
./gradlew :core:ranking:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests; app and feature tests compile against ranking wherever scoring is wired.
- Release keeps the `AdaptiveRankingBackup` JSON graph and the generated Room database constructor; `release-contracts.yml` checks the optimized build.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/recommendation-system.md`](../../docs/recommendation-system.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:database` README](../database/README.md)
- [`:core:domain` README](../domain/README.md)
