# `:core:rss`

## Purpose

Owns reading podcast feeds: fetching and parsing RSS and Atom, `rss:` show IDs and negative episode IDs, the true RSS show repository, the local episode catalog that followed Podcast Index shows read from their publisher feed, the stored feed copies behind feed-first show pages, and feed-only supplements ("Missing episodes").

It does not own Podcast Index or boxlore API calls ([`:core:network`](../network/README.md)), the Room schema ([`:core:database`](../database/README.md)), which source a show page or episode list uses ([`:core:catalog`](../catalog/README.md)), downloads, playback, ranking or feature UI.

## Public API

### Feed client and parsing

- `RssFeedClient`: `fetch`, `fetchConditional` (304 → null), `fetchNewest` (the first items only), `confirmUnchanged`, `checkFreshness` and `parse`.
- `ParsedRssFeed`, `RssFetchResult`, `RssPrefixFetch`, `RssFreshnessResult`: fetch and parse results; `ParsedRssFeed.items` carries each item's document position as a `ParsedFeedItem`.
- `FeedPrefix`: what a streaming read of a feed's start returned.
- `RssIdGenerator`: HTTPS validation, `rss:` show IDs and negative episode IDs (minted once, never on refresh).
- `RssUnchangedLogic`: a HEAD 405 or 501, or a failed HEAD, falls through to a conditional GET instead of counting as changed.
- `FeedHttpException`, `NotAFeedException`, `FeedTooLargeException`: typed read failures, all `IllegalArgumentException`s.
- `FeedOrderLogic`: classifies a feed as newest-first, oldest-first or mixed.

### RSS-only shows

- `RssPodcastRepository`: implements `RssSubscriptionPort` for `rss:` shows (subscribe, `restoreSubscription`, `refreshCatalog`, `refreshCatalogIfNeeded`, episode reads, `confirmPodcastIndexLink`, `relinkSwitchedShow`). Application-scoped; built by `AppContainer`.
- `RssPodcastRepository.episodeCatalog` (`RssEpisodeCatalog`): the same `LocalEpisodeCatalogPort` contract as Podcast Index catalogs, over `rss_episodes`.
- `RssPodcastRepository.legacySubscriptionRepair` (`LegacyRssSubscriptionRepair`): `inspect` returns a `LegacyRssFeedSnapshot` without writing; `upgrade` copies a verified RSS catalog under a Podcast Index ID and returns a `LegacyRssUpgradeOutcome`.
- `StickyRssEpisodeRemap`: maps parsed rows to the stored episode IDs before a write, so stored IDs never change.
- `escapeForSqlLike`: escapes a search term for a SQL `LIKE`.

### Local episode catalog for followed shows

- `LocalEpisodeCatalogRepository`: implements `LocalEpisodeCatalogPort` for followed Podcast Index shows, keyed by the show's Podcast Index ID, over `local_episode_feeds` and `local_episodes`. It never creates `rss:` rows.
- `LocalEpisodeCatalogRepository.linkRenumberedShow`: links a renumbered show's listener rows at once.
- `LocalCatalogReadyLogic`: lists come from Room only when the HTTPS feed was marked ready after a complete read, no backfill is pending and no unsubscribe TTL is set.
- `LocalCatalogOrphanRematch`, `StickyEpisodeIdentity`, `LocalEpisodeCatalogPersist` (`PlannedEpisodeRow`, `PlannedIdSource`), `NewEpisodeIds`: identity rules for stored keys, own mints, reserved IDs and new-row plans.
- `RecipeIdHash`: the hash new IDs are minted with; production uses `RecipeIdHash.RECIPE`, tests may force clashes.
- `RenumberedShows` (`MemoryRenumberedShows`, `SharedPreferencesRenumberedShows`) and `RenumberedShowIds`: the record of renumbered Podcast Index shows and its pure rules.
- `ports.DownloadCacheRelinker`: injected by app wiring so the orphan rematch can move a download cache without a `:core:downloads` dependency.

### Show-page feed copies

- `LocalEpisodeCatalogRepository.showPages`: the `ShowPageFeedPort` behind feed-first show pages, followed or not (`read`, `readTransport`, `search`, `markOpened`, `cleanUp`, `unfollowedCount`, `clearUnfollowed`).
- `ShowPageFeedRules`: newest-read size, recheck age, validator and 304 rules, and cleanup limits.

### Feed supplements (Missing episodes)

- `EpisodeSupplementRepository`: implements `EpisodeSupplementPort`. It reads a Podcast Index show's public feed and caches feed-only episodes under the Podcast Index ID. It is not a subscription and never creates `rss:` rows.
- `EpisodeSupplementListMerge`: dedupes Podcast Index pages with feed-only episodes (audio address, then title within a date window); only `PodcastRepository` merges lists.
- `EpisodeSupplementDisconnectLogic`: decides automatic opt-in on subscribe.
- `EpisodeSupplementArtworkLogic`: fills blank item artwork from the channel or the show image.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/rss/
  RssFeedClient.kt                 # fetch, parse, RssIdGenerator, result types
  RssHttpExecution.kt              # OkHttp call tied to coroutine cancellation
  RssParsePolicy.kt                # which parsers run for a feed size
  RssUnchangedLogic.kt
  FeedPrefixReader.kt              # streaming read of a feed's first items
  FeedReadFailure.kt               # typed failures, dead-address and report status
  FeedOrderLogic.kt
  ParsedFeedItem.kt                # document order, item keys
  RssPodcastRepository.kt          # also RssSourceMatcher
  RssEpisodeCatalog.kt
  StickyRssEpisodeRemap.kt
  LegacyRssSubscriptionUpgrade.kt
  LocalEpisodeCatalogRepository.kt
  LocalEpisodeCatalogReads.kt
  LocalEpisodeCatalogRefresh.kt
  LocalEpisodeCatalogPersist.kt
  LocalCatalogReadyLogic.kt
  PublisherFeedRefreshGate.kt      # shared whole-feed read limit
  StickyEpisodeIdentity.kt
  NewEpisodeIds.kt                 # recipe hash, namespace choice
  CatalogAliasRecords.kt           # alias writes of a read
  LocalEpisodeCollisionGuard.kt
  ListenerIdAdoption.kt
  LocalCatalogOrphanRematch.kt
  LocalCatalogListenerStateReconciler.kt
  ListenerStateMoves.kt
  SwitchedShowLinker.kt
  RenumberedShows.kt
  RenumberedShowLinker.kt
  ShowPageFeedCatalog.kt
  ShowPageFeedRules.kt
  EpisodeSupplementRepository.kt
  EpisodeSupplementMatcher.kt
  EpisodeSupplementListMerge.kt
  EpisodeSupplementTipLogic.kt
  EpisodeSupplementDisconnectLogic.kt
  EpisodeSupplementArtworkLogic.kt
  ports/
    DownloadCacheRelinker.kt
```

## Dependencies

- → `:core:model`; `api` → `:core:database` (entity types in results) and `:core:domain` (the ports it implements).
- Libraries: OkHttp, rssparser, Firebase Messaging (retires a Podcast Index topic when a show switches to its feed) and coroutines.
- `:core:catalog` exposes this module to callers through its `api` edge.
- Forbidden: downloads, playback, designsystem, analytics or feature modules.

## Threading / lifecycle

- Repositories are application-scoped, built by `AppContainer`; do not create a second `RssPodcastRepository`.
- Public APIs are `suspend` and do network and database work on IO.
- Writes to one show are serialised by a per-show lock: show-page reads share the followed catalog's lock, and the RSS follow upgrade shares `refreshCatalog`'s.
- At most two whole-feed reads run at once across followed catalogs, RSS shows and show pages (`PublisherFeedRefreshGate`).
- `RefreshRequest.canProceed` is checked again after waiting and after fetching, so a revoked refresh writes nothing.
- Cancelling a refresh cancels the HTTP call, including while the body is read (`RssHttpExecution`); every response, redirects included, must be HTTPS.
- Automatic refreshes of ready catalogs and RSS shows wait out a six-hour cooldown (for RSS shows, also after a failed read); `MANUAL` and `NEW_RELEASE` refreshes skip it.
- The post-persist callback runs outside the transaction and lock, and never for `AUTO_DOWNLOAD` or `runPostPersistCallback = false`.

## Persistence & identity

- **Feed size:** every read stops at 25 MB (`FeedTooLargeException`), transport documents included. Feeds over 4 MB use only the streaming pull parser; the library parser runs only at or below 4 MB (`RssParsePolicy`).
- **Whole feed or its start:** a show page's first read takes the newest 100 items (`fetchNewest`); archive reads, followed-show refreshes and oldest-first feeds read the whole feed.
- **Show-page copies:** a copy older than an hour is read again; a 304 to an archive read of a partial copy is never taken as the archive.
- **Copy cleanup:** unfollowed copies not opened in 30 days go first, then the least recently opened beyond 40 shows or 20,000 episodes. History, downloads, the queue and aliases stay.
- **Show-page writes** keep the stored row's `ready` and unsubscribe TTL, so a page read never makes an unfollowed copy the followed list.
- **IDs:** `rss:<sha256 of the normalized feed URL>` for RSS shows; negative `Long` strings for RSS and supplement episodes; recipe IDs for new episodes of Podcast Index shows ([§2](../../docs/EPISODE_IDS.md#2-recipe-id-fixed)).
- **Keys and duplicates:** an item's key is its GUID, else its first playable enclosure, else its audio address; the first item in the document wins a shared key ([§1](../../docs/EPISODE_IDS.md#1-what-an-episode-is), [§3](../../docs/EPISODE_IDS.md#3-keys-from-stored-data)).
- **Stored rows keep their IDs** across refreshes, parser changes and address moves; nothing is re-keyed except by the orphan rematch.
- **Collision guard:** no write replaces another episode; a clash gets a reserved ID and an alias in the same transaction ([§4](../../docs/EPISODE_IDS.md#4-collisions)).
- **Orphan rematch:** only phone-minted IDs with no alias or an unresolved one move after a read ([§5](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone)).
- **Switching a show to its RSS feed** moves nothing; RSS episodes get `rss_source_link` aliases under the Podcast Index show ([§5](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone)).
- **Renumbered shows:** catalog reads link the old show's listener rows ([renumbered shows](../../docs/EPISODE_IDS.md#renumbered-shows)).
- **Room tables written:** `podcasts`, `rss_episodes`, `local_episode_feeds`, `local_episodes`, `episode_aliases`, `episode_supplements`, `episode_supplement_items`, and listener tables during rematch and links. Schema: [`:core:database`](../database/README.md).
- **Never rename:** the `boxlore_renumbered_shows` SharedPreferences file and the `rss:` and negative ID schemes ([`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage)).

## Testing notes

Tests live in `core/rss/src/test`. Room tests use Robolectric with an in-memory `BoxLoreDatabase` (`RoomCatalogTestBase`); `android.util.Xml` is backed by kxml2. Feed fixtures are in `src/test/resources`; `fixtures/episode-recipe-vectors.json` is shared with `scripts/sync`.

| Area | Test classes |
| :--- | :--- |
| Fetch and parse | `RssFeedClientParseTest`, `RssFeedClientHelpersTest`, `RssConditionalFetchTest`, `RssHttpExecutionTest`, `RssParsePolicyTest`, `RssUnchangedLogicTest`, `FeedReadFailureTest`, `FeedMoveTest` |
| Keys and document order | `FeedDocumentOrderTest`, `FeedOrderLogicTest`, `StickyEpisodeIdentityTest`, `RssIdGeneratorTest`, `EpisodeRecipeIdTest` |
| RSS shows | `RssPodcastRepositoryTest`, `RssRepositoryHelpersTest`, `RssSourceMatcherTest`, `StickyRssEpisodeRemapTest`, `LegacyRssSubscriptionUpgradeLogicTest` |
| Local catalog | `LocalEpisodeCatalogRepositoryTest`, `LocalEpisodeCatalogPersistTest`, `LocalCatalogReadyLogicTest`, `CatalogAliasRecordsTest`, `ListenerIdAdoptionTest` |
| IDs and collisions through Room | `LocalEpisodeCollisionGuardTest`, `RecipeIdRoomTest`, `LegacyIdentityRoomTest` |
| Listener state | `ListenerStateMovesTest`, `ListenerStateMovesRoomTest`, `LocalCatalogListenerStateReconcilerTest`, `LocalCatalogOrphanRematchTest`, `OrphanRematchRoomTest`, `ShowSwitchRoomTest` |
| Renumbered shows | `RenumberedShowsTest`, `RenumberedShowLinksTest`, `RenumberedShowRoomTest` |
| Show pages | `FeedPrefixReaderTest`, `ShowPageFeedRulesTest`, `ShowPageFeedCatalogTest` |
| Supplements | `EpisodeSupplement*Test` |

```bash
./gradlew :core:rss:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests in the core shard.
- The module is in the merged Kover coverage gate (`:koverVerifyMerged`).
- `:core:testing` architecture guards check this README and the dependency rules.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:database` README](../database/README.md)
- [`:core:domain` README](../domain/README.md)
- [`:core:downloads` README](../downloads/README.md)
