# `:core:domain`

## Purpose

Owns thin domain ports and small result types used by ViewModels, repositories, workers, and tests without pulling in production repository graphs. It does not own Room entities, Retrofit DTOs beyond existing edge types, ranking engines, Compose UI, managers, or production implementations.

## Public API

- `LocalEpisodeCatalogPort` also serves source-aware subscription automation through `SubscribedEpisodeCatalog` and `RssEpisodeCatalog`. The existing `RefreshRequest.podcastIndexId` field carries the canonical show ID, including `rss:` for that adapter. Subscription creation and PI extras remain separate ports. A catalog refresh never reads a Podcast Index list: new episodes of Podcast Index shows take recipe IDs.

- `RssSubscriptionPort` and `RssSubscriptionResult`.
- `EpisodeSupplementPort` and `EpisodeSupplementOutcome` (PI show feed extras; not an RSS subscription). `NewestTipRequest` / `FeedItemMatch` bundle the lighter tip-refresh path used as an FCM payload match fallback. `RefreshFromFeedRequest` optionally loads the PI list the extras are compared with, in parallel with the feed GET, via `loadBaseline` (legacy launch sync, restore and FCM hydration paths without a local catalog). If that loader throws (strict PI HTTP failure), production `refreshFromFeed` returns `Failure` and does not replace stored supplement rows. `isPublisherFeedUnchanged` defaults to false (always refresh). `listDirectFeedOptIns` / `restoreDirectFeedOptIn` default to empty / no-op so fakes stay small; production restores Missing episodes? after library JSON import.
- `RankingResetPort`.
- `PodcastCatalogPort`.
- `HistoryRecommendationSource`.
- `LocalCatalogPort` for local podcast lookup and subscribed podcast upsert.
- `LocalEpisodeCatalogPort` for the first-class subscribed-PI episode catalog (ready gate, paged windows, sticky refresh). Creation is separate from RSS subscription catalog reads and Missing-episodes extras.
- `EpisodeOfflineLookupPort` and `OfflineEpisodeSnapshot` for episode-detail download/history hydration.
- `ConnectivityStatusPort` and connectivity status types.
- `DeviceIdentityPort` for cross-device sync attribution.
- `FeedTelemetryPort` reports feed reads (`feedFetched`) and saved-address swaps (`feedUrlCorrected`) without a dependency on `:core:analytics`; `AppContainer` wires the analytics-backed one and `NONE` reports nothing. Implementations must not throw.
- `ShowPageFeedPort` is the feed-first show page's stored feed copy of a Podcast Index show, followed or not (P4 PR 16): `cached` (episode count, whether it is the whole archive, when it was read, oldest-first), `read` (`NEWEST` or `ARCHIVE` of an HTTPS address; `Stored`, `NotModified` or `Failed` with the `/v2/feed-report` status, never a throw), `readTransport` (an http-only feed through boxlore's transport: the caller's fetch gets the copy's ETag and answers `Transported` `Document`, `NotModified`, `ReadDirect` or `Failed`; it runs under the read's lock and download gate), `search` (bounded in the query), `markOpened` and `cleanUp` (unfollowed copies not opened in 30 days, then a size cap; never history, downloads, the queue or aliases), and since P4 PR 20 `unfollowedCount` / `clearUnfollowed` for Settings › Clear saved shows (every unfollowed copy, whatever its age and including one still under its unsubscribe TTL; the same rows as `cleanUp`, and a show followed meanwhile keeps its copy; both default to 0). Pages of the copy go through `LocalEpisodeCatalogPort.getPage`. `:core:rss` implements it (`LocalEpisodeCatalogRepository.showPages`).

- `LocalEpisodeCatalogPort.RefreshRequest.reason` defaults to `NORMAL`; automatic refresh and `AUTO_DOWNLOAD` share the persisted six-hour freshness gate. `MANUAL` and `NEW_RELEASE` bypass quiet/HEAD shortcuts. `canProceed` rechecks authorization before network/persistence, `runPostPersistCallback` prevents background metadata recovery from invoking ungated ingest callbacks, and `isRefreshDue` supports bounded foreground batches. `RefreshOutcome.Failure.addressGone` says the saved address looks dead rather than temporarily failing, and `RefreshOutcome.Failure.reportStatus` says what the read saw in the words `/v2/feed-report` takes (`http_404`, `timeout`, `dns`, `tls`, `parse`, `network`, `other`; null when no read happened); with `RefreshRequest.detectMove` (and a `PodcastMeta.title` to compare), `RefreshOutcome.Success.movedTo` carries a confirmed new HTTPS address. `RefreshRequest.serverEpNs` passes the episode namespace the server reported for the show; it is only compared with the stored one and never changes an ID. Publisher RSS and Room remain the source of subscribed episodes.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/domain/
  RssSubscriptionResult.kt
  ports/
    ConnectivityStatusPort.kt
    EpisodeOfflineLookupPort.kt
    EpisodeSupplementPort.kt
    FeedTelemetryPort.kt
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
