# `:core:domain`

## Purpose

Owns thin domain ports and small result types used by ViewModels, repositories, workers, and tests without pulling in production repository graphs. It does not own Room entities, Retrofit DTOs beyond existing edge types, ranking engines, Compose UI, managers, or production implementations.

## Public API

- `RssSubscriptionPort` and `RssSubscriptionResult`.
- `EpisodeSupplementPort` and `EpisodeSupplementOutcome` (PI show feed extras; not an RSS subscription). `NewestTipRequest` / `FeedItemMatch` bundle the lighter tip-refresh path used as an FCM payload match fallback. `RefreshFromFeedRequest` optionally loads the PI baseline in parallel with the feed GET via `loadBaseline` (launch sync and FCM hydration). If that loader throws (strict PI HTTP failure), production `refreshFromFeed` returns `Failure` and does not replace stored supplement rows. `isPublisherFeedUnchanged` defaults to false (always refresh). `listDirectFeedOptIns` / `restoreDirectFeedOptIn` default to empty / no-op so fakes stay small; production restores Missing episodes? after library JSON import.
- `RankingResetPort`.
- `PodcastCatalogPort`.
- `HistoryRecommendationSource`.
- `LocalCatalogPort` for local podcast lookup and subscribed podcast upsert.
- `LocalEpisodeCatalogPort` for the first-class subscribed-PI episode catalog (ready gate, paged windows, sticky refresh). Not an RSS subscription and not Missing-episodes extras.
- `EpisodeOfflineLookupPort` and `OfflineEpisodeSnapshot` for episode-detail download/history hydration.
- `ConnectivityStatusPort` and connectivity status types.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/domain/
  RssSubscriptionResult.kt
  ports/
    ConnectivityStatusPort.kt
    EpisodeOfflineLookupPort.kt
    EpisodeSupplementPort.kt
    HistoryRecommendationSource.kt
    LocalCatalogPort.kt
    LocalEpisodeCatalogPort.kt
    PodcastCatalogPort.kt
    RankingResetPort.kt
    RssSubscriptionPort.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:network`.
- Libraries: coroutines.
- Reverse-edge rule: domain must not depend on catalog, database, playback, downloads, designsystem, or feature modules.

## Threading / lifecycle

- Ports expose suspend functions or flows; implementations choose dispatchers.
- No application-scoped objects are created in this module.
- Production implementations are wired from `AppContainer` in owning data modules.

## Persistence & identity

- No persistence is owned here.
- Port contracts may carry stable IDs such as `rss:` podcast IDs or episode IDs, but those schemes are owned by RSS, database, and playback modules.

## Testing notes

- Unit tests live under `core/domain/src/test`.
- Existing tests cover subscription results and port contract behavior.
- Use constructor-injected fakes or helpers from `:core:testing` when testing callers.

```bash
./gradlew :core:domain:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs domain JVM tests.
- The root Kover merged verification includes this module.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
