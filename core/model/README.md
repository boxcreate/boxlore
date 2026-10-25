# `:core:model`

## Purpose

Owns shared domain models, enums, and pure value helpers used across network, data, playback, and UI modules. It does not own network DTOs, Room entities, Compose UI, repositories, or Android lifecycle behavior.

## Public API

- `ArtworkUrls.kt` provides ordered, deduplicated episode-first and show-first sources. It ignores blank, malformed and unsupported values, retains local artwork URIs and signed query parameters, and upgrades HTTP to HTTPS. `ArtworkUrlsTest` covers these contracts. The player's Android Auto / media controls links (`content://<app>.collage/...`) are excluded: `isPlaybackArtworkLink` detects them and `persistableArtworkUrl` turns them into null, so they are never saved, synced or shown as a card's image (they point into an in-app registry that is cleared with downloads and app data).
- `BriefingArtwork` owns stable square-player resource names and supported region aliases. UI and legacy database-artwork repair use the same named URI contract, independent of build-specific Android resource numbers.

- `Podcast.RSS_REFRESH_AUTOMATIC` describes publisher subscriptions refreshed via conditional GET or cooldown-gated GET, including feeds without HEAD validators. Legacy capability strings remain readable for in-place upgrade repair.

- Podcast, episode, briefing, chapter, person, transcript, and playback-adjacent model types.
- `EpisodeRecipeId`: boxlore's own episode IDs for numeric shows. A SHA-256 handle over (show ID, catalog key) in `[2^52, 2^53 - 1]`, so it never overlaps Podcast Index IDs or negative RSS IDs. `catalogKey` trims an explicit character set equal to Kotlin's `trim()` on Android (which, unlike the desktop JVM, also trims U+180E). `forNamespace(epNs, key)` mints with a show's stored episode namespace, which today equals its show ID. `showIdOrNull` parses a stored podcast ID exactly as the scripts copy does: ASCII digits only, no sign, leading zero or whitespace, and at most `2^53 - 1`; `rss:` IDs return null. It is a pure function with no storage of its own, kept here so `:core:database` and `:core:rss` share one copy. The full rules are in [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- `EpisodeMediaCacheKey`: Media3 `customCacheKey` helper — briefing keys append audio URL `v=` so same-day regenerations bust the local audio cache.
- `ContentRegion` / `ContentRegions`: 11 chart storefronts, language allowlist/normalize/expand (`id`→`id,in`), off-market soft-warn helpers, and briefing market mapping that preserves the explicit `global` briefing tab.
- `ContentLanguageSelection`: pure language-chip toggle rules (English lock, max languages, BCP47 normalization) for settings/onboarding pickers.
- `PlaybackEntryPoint`, `ShareTarget`, and `ShareLinkBuilder`. Episode links can name their show (`episode(…, showId)` adds `&show=` before `t` or the clip range); `forTarget` builds the link for an `EpisodeShareTarget` (`Episode(id, showId)`, or `Show(showId)`, which is the show's link).
- `EpisodeShareIds.choose(localId, podcastId, recipeId)` picks the ID a share link carries: for a Podcast Index show its recipe ID (or its own recipe-range or Podcast Index ID) with the show; never a phone-only negative or reserved ID (the show's link instead); a show added by its feed address (`rss:`) keeps the episode's own ID with no show. `EpisodeShareTargetSource` is the seam the app fills with alias reads and registration (`FROM_EPISODE` uses the episode's fields only).
- `PlaybackEntryPoint` coarse values: `GENERIC`, `HOME_MIXTAPE`, `LEARN`, `BRIEFING`
  (Brief audio play synthesizes glossary `entry_point=briefing` for `playback_*`).
- `AutoTranscriptState`.
- `ListeningHistoryRemoval` holds what removing an episode from history took away, for Undo: the chosen row, its sessions and daily totals, and for an episode stored under several IDs every removed row exactly as stored (`ListeningHistoryRowSnapshot`, with like and completion times and how it was completed) and its shared settings (`ListeningGroupStateSnapshot`), so Undo puts the episode back unchanged.
- `PodcastGenres` and `RankingAggregateTelemetry`.
- `Episode.recipeId` (boxlore's own episode ID) and `Episode.catalogKey` (trimmed GUID, else enclosure URL) are optional and null until a source fills them; `id` stays the local ID. Unset values are not written to JSON, and JSON without them still decodes (`EpisodeIdentityFieldsTest`).
- `FeedTelemetry.kt`: values for the feed analytics events. `FeedFetchReport.ShowPage` (one show-page read) and `FeedFetchReport.BackgroundPass` (one summary per pass and source, with `showsCount` and `passResult`), `FeedFetchSource`, `FeedFetchOutcome`, `FeedFetchCompleteness`, `FeedPassResult`, and `FeedUrlCorrection` with its `FeedUrlCorrectionReason` and `FeedUrlCorrectionStep`. Each enum's `wireName` is its glossary value. None of them holds an address (`FeedTelemetryTest`).
- Cross-promotion model types: `CrossPromotionResult` / `ResolvedCrossPromotion`, `CrossPromotionConfidence` and `CrossPromotionIndicator`, whose `DISTINCT_ARTWORK` marks an episode whose own cover differs from its show's (evidence `:core:catalog`'s detector uses for trailers of another show). Indicators live only in UI state.
- `ShowNotes`, `EpisodeLink`, and `EpisodeLinkKind` carry locally parsed plain text, publisher HTML, chapters, resource purpose, destination, and publisher labels without UI or parser-library dependencies.
- `Podcast.isLatestEpisodeNew`: shared NEW badge. Room `rssHasNewEpisodes` is true for true-RSS freshness **and** for Podcast Index direct-feed tip promotions (`updateLatestEpisode(..., markAsNew = true)`). Opening the show clears the flag. Otherwise the 48h window / last-seen id rules apply.
- `Podcast.listenerShowIds` (`ListenerShows.idsOf` for plain fields): the shows whose history, downloads and queue rows count as a show's own. For an RSS show the listener switched to from a Podcast Index show (`isRss` with `linkedPodcastIndexId`) that is the RSS show and the Podcast Index show, because the switch keeps every row under its stored show ([`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md) §5); for any other show, the show alone. Per-show reads (show scores, Most listened, serial next-up, auto-download limits) match rows by it.
- `Podcast.effectiveGenre`: resolves user `customGenre` override when non-blank, falling back to default catalog `genre`. Companion `customGenreIcon` stores the icon identifier for custom tags.
- `Podcast.recommendationGenre`: resolves canonicalized `customGenre` via `PodcastGenres.canonicalize` so valid standard reclassifications adapt personalized recommendations and Smart Queue, falling back to `genre` when the tag is arbitrary.
- `FolderDisplaySize`: display sizing enum (`COMPACT 1×1`, `WIDE 2×1`, `FEATURED 2×2`, `LARGE 2×3`, `SHELF 3×1`, `PANEL 3×2`, `SHOWCASE 3×3` max) for library subscription folders, with placement properties (`isPinnedToTop`, `placementLabel`) distinguishing grid-movable (`1×1 COMPACT`) from top-pinned display sizes.
- `SubscriptionFolder`: domain model representing a custom folder organizing subscribed podcasts with optional icon, display size, `showPodcastGrid` toggle for 1×1 covers, and linked genre auto-syncing.
- `BoxLoreUser`: domain model representing an authenticated user account (UID, email, display name, email verification flag, anonymous status, provider ID).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/model/
  Briefing.kt
  Chapter.kt
  CrossPromotion.kt
  Episode.kt
  EpisodeMediaCacheKey.kt
  EpisodeRecipeId.kt
  FeedTelemetry.kt
  Podcast.kt
  PodcastGenres.kt
  RankingAggregateTelemetry.kt
  ShareLinkBuilder.kt
  EpisodeShareTarget.kt
  ShowNotes.kt
  SleepTimerConstants.kt
  Transcript.kt
  ContentRegion.kt
  ContentLanguageSelection.kt
  ...
```

## Dependencies

- Project dependencies: none.
- Libraries: Kotlinx serialization. `EpisodeRecipeId` uses the JDK's `java.security.MessageDigest` (SHA-256), not an Android API.
- Reverse-edge rule: Android framework, Room, Retrofit implementation details, repositories, and Compose UI must stay out of this module.

## Threading / lifecycle

- Types are immutable or treated as immutable value data.
- No lifecycle owners, singletons, dispatchers, or background work are created here.

## Persistence & identity

- This module owns no persistence files or storage keys.
- ID schemes carried by model fields are owned by their source modules, such as RSS podcast IDs and playback media IDs.
- The recipe ID scheme is the exception: `EpisodeRecipeId` defines it here, and it is fixed. Never change the recipe tag, trim set or hash; a new recipe needs a new tag and range ([`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)).

## Testing notes

- Unit tests live under `core/model/src/test`.
- `ShareLinkBuilderTest` covers `&show=` with and without a timestamp or clip and `forTarget`; `EpisodeShareIdsTest` covers recipe, Podcast Index, phone-only, reserved and `rss:` IDs and non-canonical shows.
- `EpisodeRecipeIdNamespaceTest` covers the show ID parse (it rejects `0123`, `+5`, ` 5`, `9007199254740992`, non-ASCII digits and the empty string) and the namespace entry point. The shared recipe fixture and its test stay in `core/rss/src/test` (`EpisodeRecipeIdTest`), where the scripts copy reads the same file; run `:core:rss:testDebugUnitTest` after any change to the recipe.
- `Podcast.listenerShowIds` is covered with the readers that use it, in `:core:database` `SwitchedShowListeningTest`.
- `ShareLinkBuilderTest` covers share URL invariants. Share links use `https://boxlore.in/share`; the app still opens the older `aswin.cx` share links.
- Prefer pure JVM tests for additional formatters or value helpers.

```bash
./gradlew :core:model:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` compiles this module for nearly every test target.
- No module-specific CI job is required beyond JVM tests.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:network` README](../network/README.md)
- [`:core:database` README](../database/README.md)

### Release optimization

Gson-backed models retain JSON field names and default constructors in release. The optimized-artifact gate checks their nested field graph; compiler-generated serializers use the library consumer rules.
