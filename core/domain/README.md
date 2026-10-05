# `:core:domain`

## Purpose

Owns thin domain ports and small result types used by ViewModels, repositories, workers, and tests without pulling in production repository graphs. It does not own Room entities, Retrofit DTOs beyond existing edge types, ranking engines, Compose UI, managers, or production implementations.

## Public API

- `LocalEpisodeCatalogPort` also serves source-aware subscription automation through `SubscribedEpisodeCatalog` and `RssEpisodeCatalog`. The existing `RefreshRequest.podcastIndexId` field carries the canonical show ID, including `rss:` for that adapter. Subscription creation and PI extras remain separate ports; no PI baseline is requested for pure RSS.

- `RssSubscriptionPort` and `RssSubscriptionResult`.
- `EpisodeSupplementPort` and `EpisodeSupplementOutcome` (PI show feed extras; not an RSS subscription). `NewestTipRequest` / `FeedItemMatch` bundle the lighter tip-refresh path used as an FCM payload match fallback. `RefreshFromFeedRequest` optionally loads the PI baseline in parallel with the feed GET via `loadBaseline` (launch sync and FCM hydration). If that loader throws (strict PI HTTP failure), production `refreshFromFeed` returns `Failure` and does not replace stored supplement rows. `isPublisherFeedUnchanged` defaults to false (always refresh). `listDirectFeedOptIns` / `restoreDirectFeedOptIn` default to empty / no-op so fakes stay small; production restores Missing episodes? after library JSON import.
- `RankingResetPort`.
- `PodcastCatalogPort`.
- `HistoryRecommendationSource`.
- `LocalCatalogPort` for local podcast lookup and subscribed podcast upsert.
- `LocalEpisodeCatalogPort` for the first-class subscribed-PI episode catalog (ready gate, paged windows, sticky refresh). Creation is separate from RSS subscription catalog reads and Missing-episodes extras.
- `EpisodeOfflineLookupPort` and `OfflineEpisodeSnapshot` for episode-detail download/history hydration.
- `ConnectivityStatusPort` and connectivity status types.
- `DeviceIdentityPort` for cross-device sync attribution.

- `LocalEpisodeCatalogPort.RefreshRequest.reason` defaults to `NORMAL`; automatic refresh and `AUTO_DOWNLOAD` share the persisted six-hour freshness gate. `MANUAL` and `NEW_RELEASE` bypass quiet/HEAD shortcuts. `canProceed` rechecks authorization before network/persistence, `runPostPersistCallback` prevents background metadata recovery from invoking ungated ingest callbacks, and `isRefreshDue` supports bounded foreground batches. Publisher RSS and Room remain the source of subscribed episodes.

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
