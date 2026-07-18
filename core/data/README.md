# `:core:data`

## Purpose

Monolithic data layer: Room, repositories, playback service, downloads/workers, ranking, RSS, analytics helpers, prefs. Intended to split into `database` / `playback` / `downloads` / `library` / `prefs` / `analytics` in later phases.

## Public API

- Repositories: `PodcastRepository`, `PlaybackRepository`, `QueueRepository`, `SubscriptionRepository`, `DownloadRepository`, `RssPodcastRepository`, `UserPreferencesRepository`
- Ports: `ports.RssSubscriptionPort`, `ports.RankingResetPort`, `ports.PodcastCatalogPort`, `ports.HistoryRecommendationSource`
- Managers: `QueueManager`, `SmartDownloadManager` (uses `HistoryRecommendationSource`, not full `PlaybackRepository`)
- `BoxLoreDatabase`, playback `BoxLorePlaybackService`
- Ranking: `AdaptiveCandidateScorer`, `RankingFeedbackRepository`, `AdaptiveRankingRepository` (prefer container façades over ad-hoc `getInstance` in UI)
- Workers: `SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker` (FQCN stability / aliases matter; smart downloads avoid constructing `PlaybackRepository`)

**Must not** depend on `:core:designsystem`. Share UI lives in designsystem; notification seek icons live in this module’s `res/`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  database/ ranking/ playback/ service/ content/
  analytics/ privacy/ backup/ crosspromo/
```

## Dependencies

- → `:core:model`, `:core:network`
- Room, Media3, WorkManager, DataStore, Coil (artwork in service), Firebase Messaging pieces as needed

## Testing notes

- Existing JVM tests under `src/test` (queue math, RSS, ranking, content, etc.)
- Migrate to JUnit 5 / shared fixtures in `:core:testing` in later phases

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
