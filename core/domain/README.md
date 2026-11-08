# `:core:domain`

## Purpose

Owns thin domain ports and small result types that ViewModels, repositories, workers and tests depend on without pulling in production repository graphs.

It does not own Room entities, network DTOs, ranking engines, Compose UI, managers or production implementations; those live in the modules that implement the ports and are wired in `AppContainer`.

## Public API

Catalog and feeds:

- `PodcastCatalogPort`: show details, one episode, and a show's episodes from the catalog.
- `LocalCatalogPort`: local show lookup, the subscribed RSS show linked to a Podcast Index show, and subscribed show upsert.
- `LocalEpisodeCatalogPort`: the subscribed-show episode catalog (ready gate, pages and windows, search, newest, refresh, unsubscribe TTL). Production composes catalog and RSS adapters in `:core:catalog`'s `SubscribedEpisodeCatalog`.
- `LocalEpisodeCatalogPort.RefreshRequest`: `podcastIndexId` carries the canonical show ID, including `rss:`; `reason` is a `RefreshReason` (`NORMAL`, `AUTO_DOWNLOAD`, `NEW_RELEASE`, `MANUAL`).
- `RefreshRequest.canProceed` rechecks authorization before network and storage work; `runPostPersistCallback = false` keeps background recovery from running post-write callbacks.
- `RefreshRequest.detectMove` asks for a confirmed new HTTPS address in `RefreshOutcome.Success.movedTo`; `serverEpNs` is only compared with the stored namespace and never changes an ID.
- `LocalEpisodeCatalogPort.RefreshOutcome`: `Success`, `Unchanged` or `Failure`; `Failure.addressGone` marks a dead address and `reportStatus` uses the `/v2/feed-report` status words.
- `ShowPageFeedPort`: the stored feed copy behind a Podcast Index show's page, followed or not; implemented by `:core:rss` (`LocalEpisodeCatalogRepository.showPages`).
- `ShowPageFeedPort.read` and `readTransport`: read the `NEWEST` or `ARCHIVE` scope and return `Stored`, `NotModified` or `Failed`, never throwing; `readTransport` routes an http-only feed through boxlore's transport.
- `ShowPageFeedPort.cleanUp`, `unfollowedCount`, `clearUnfollowed`: remove unfollowed copies (stale ones, then over a size cap; or all for Settings); history, downloads, the queue and aliases always stay.
- `EpisodeSupplementPort`, `EpisodeSupplementOutcome`: feed-only extras of a Podcast Index show (not an RSS subscription), including the newest-tip lookup (`NewestTipRequest`, `FeedItemMatch`) and direct-feed opt-ins.
- `RefreshFromFeedRequest.loadBaseline`: loads the Podcast Index list in parallel with the feed; if it throws, the refresh returns `Failure` and keeps stored extras.
- `RssSubscriptionPort`, `RssSubscriptionResult`: subscribing by feed address, with any likely Podcast Index match.

Other ports:

- `ListeningHistoryPort`: history timelines, listening by day and episode, played shows, period insights, and removing, restoring and clearing history.
- `EpisodeOfflineLookupPort`, `OfflineEpisodeSnapshot`: episode details from a download or history row when offline.
- `HistoryRecommendationSource`: recent history for recommendations.
- `RankingResetPort`: resets the ranking model.
- `ConnectivityStatusPort`, `AlwaysOnlineConnectivity`: online checks.
- `DeviceIdentityPort`: device ID for sync attribution.
- `FeedTelemetryPort`: reports feed reads, saved-address swaps and upgrade repair results without depending on `:core:analytics`; `NONE` reports nothing and implementations must not throw.

Default methods (`isRefreshDue`, `isPublisherFeedUnchanged`, `listDirectFeedOptIns`, `restoreDirectFeedOptIn`, `unfollowedCount`, `clearUnfollowed`) keep fakes small.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/domain/
  RssSubscriptionResult.kt
  ports/
    ConnectivityStatusPort.kt
    DeviceIdentityPort.kt
    EpisodeOfflineLookupPort.kt
    EpisodeSupplementPort.kt
    FeedTelemetryPort.kt
    HistoryRecommendationSource.kt
    ListeningHistoryPort.kt
    LocalCatalogPort.kt
    LocalEpisodeCatalogPort.kt
    PodcastCatalogPort.kt
    RankingResetPort.kt
    RssSubscriptionPort.kt
    ShowPageFeedPort.kt
```

## Dependencies

- `api`: `:core:model`, `:core:network`.
- `implementation`: coroutines.
- Forbidden: catalog, database, rss, playback, downloads, designsystem or feature modules.

## Threading / lifecycle

- Ports expose `suspend` functions or Flows; implementations choose dispatchers.
- This module creates no application-scoped objects.
- Production implementations are wired from `AppContainer` in the owning data modules.

## Persistence & identity

- This module owns no storage.
- Ports carry stable IDs (`rss:` show IDs, episode and recipe IDs) whose schemes are owned elsewhere: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md) and [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage).
- A catalog refresh never reads a Podcast Index episode list; new episodes of Podcast Index shows take recipe IDs.
- A show page read never makes an unfollowed show's copy the followed live list (`isReady` stays false) and never runs followed-show post-write work.

## Testing notes

Tests live in `core/domain/src/test` (JUnit 5). Callers should use constructor-injected fakes or helpers from [`:core:testing`](../testing/README.md).

| Area | Test classes |
| :--- | :--- |
| Subscription results | `RssSubscriptionResultTest` |
| Port contracts and defaults | `ConnectivityStatusPortTest`, `EpisodeOfflineLookupPortTest`, `FeedTelemetryPortTest`, `HistoryRecommendationSourceTest`, `LocalCatalogPortTest`, `RankingResetPortTest` |

```bash
./gradlew :core:domain:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests and includes the module in the coverage gate.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
