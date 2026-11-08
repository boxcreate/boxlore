# `:core:model`

## Purpose

Owns the shared domain models, enums and pure value helpers used by network, data, playback and UI modules: podcasts, episodes, briefings, Podcasting 2.0 types, listening history, folders, share links, content regions and feed telemetry values.

It does not own network DTOs, Room entities, Compose UI, repositories or Android lifecycle behaviour.

## Public API

Models:

- `Podcast`, `Episode`, `Briefing`: core show, episode and Brief types shared across modules.
- `Person`, `Transcript`, `Chapter`, `PodrollItem` (`Podcast2Types.kt`): Podcasting 2.0 tag values.
- `ShowNotes`, `EpisodeLink`, `EpisodeLinkKind`: parsed plain text, publisher HTML, chapters and labelled links, with no UI or parser-library dependency.
- `SubscriptionFolder`, `FolderDisplaySize`: custom library folders and their tile sizes; `isPinnedToTop` and `placementLabel` separate grid-movable from top-pinned sizes.
- `BoxLoreUser`: the signed-in account (UID, email, display name, verification, anonymous flag, provider).
- `CrossPromotionResult`, `ResolvedCrossPromotion`, `CrossPromotionConfidence`, `CrossPromotionIndicator`: cross-promotion results; `DISTINCT_ARTWORK` marks an episode whose cover differs from its show's. Indicators live only in UI state.
- `AutoTranscriptState`, `ShareTarget`, `SleepTimerConstants`: small shared enums and constants.

Listening history:

- `ListeningHistoryItem`: a history row with `firstPlayedAt` and `finishedAt` (null: not yet; 0: before boxlore recorded them; else epoch ms).
- `ListeningDayEpisode`: one day's timed listening of an episode, with time by part of day, output, source and new releases, and `releaseKnownMs`.
- `ListeningPlayedShow`: a show from history with its first and last plays.
- `ListeningPeriod`, `ListeningInsightSummary`: the periods and summaries behind Listening history's insights.
- `ListeningHistoryRemoval`: everything removing an episode took away, so Undo restores it unchanged; built from `ListeningHistoryRowSnapshot`, `ListeningGroupStateSnapshot` and `ListeningRollupSnapshot`.

Rules and helpers:

- `Episode.isTrailer`, `Episode.effectiveEpisodeType`: an episode is a trailer only when tagged `trailer` (any case) and at most `TRAILER_MAX_DURATION_SECONDS` long; unknown duration (0) stays a trailer. Show pages, Showcase and Smart Queue share this rule.
- `Episode.recipeId`, `Episode.catalogKey`: optional identity fields, null until a source fills them and omitted from JSON when unset; `id` stays the local ID.
- `EpisodeRecipeId`: boxlore's own episode IDs for numeric shows (`forNamespace`, `catalogKey`, `showIdOrNull`). Rules: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- `Podcast.listenerShowIds` (`ListenerShows.idsOf`): the shows whose history, downloads and queue rows count as this show's own, covering RSS shows switched from Podcast Index ([rules](../../docs/EPISODE_IDS.md)).
- `Podcast.isLatestEpisodeNew`: the shared NEW badge rule.
- `Podcast.effectiveGenre`: the listener's `customGenre` when set, else `genre`; `recommendationGenre` uses it only when it canonicalizes to a standard genre.
- `Podcast.RSS_REFRESH_AUTOMATIC`: the refresh capability for publisher subscriptions; legacy capability strings stay readable.
- `PodcastGenres`: genre canonicalization, `tokenMatches` for genre folders, and `isPlaceholder` for "Podcast"/"Podcasts".
- `artworkUrls`, `isPlaybackArtworkLink`, `persistableArtworkUrl` (`ArtworkUrls.kt`): ordered, deduplicated, HTTPS artwork sources; the player's in-app collage links are never saved, synced or shown.
- `BriefingArtwork`: stable Brief artwork resource names and region aliases, shared by UI and the database artwork repair.
- `EpisodeMediaCacheKey`: Media3 `customCacheKey` helper; Brief keys include the audio URL's `v=` so regenerated Briefs are not served from cache.
- `ContentRegion`, `ContentRegions`: the 11 chart storefronts, language allowlist and normalization, and Brief market mapping.
- `ContentRegions.ENGLISH_OFF_LANGUAGES`, `canSwitchEnglishOff`: the languages that allow English off; the set stays aligned with the API.
- `ContentLanguageSelection`: language chip toggle rules, `englishSwitch`, and `normalizeKeepingEnglishChoice` for request builders so English off reaches the server.
- `ShareLinkBuilder`: share links on `https://boxlore.in/share`; episode links can name their show (`&show=`); `forTarget` builds from an `EpisodeShareTarget`.
- `EpisodeShareIds.choose`: picks the ID a share link carries, never a phone-only negative or reserved ID. `EpisodeShareTargetSource` is the seam the app fills with alias reads (`FROM_EPISODE` uses the episode's fields only).
- `PlaybackEntryPoint`: coarse play origins (`GENERIC`, `HOME_MIXTAPE`, `LEARN`, `BRIEFING`).
- `FeedFetchReport`, `FeedFetchSource`, `FeedFetchOutcome`, `FeedFetchCompleteness`, `FeedPassResult`, `FeedUrlCorrection` (`FeedTelemetry.kt`): feed analytics values whose `wireName` is the glossary value; none holds an address.
- `RankingAggregateTelemetry`: aggregate ranking analytics values.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/model/
  Podcast.kt                   # Podcast, ListenerShows
  Episode.kt
  Podcast2Types.kt             # Person, Transcript, Chapter, PodrollItem
  Briefing.kt, BriefingArtwork.kt
  ShowNotes.kt
  ListeningHistoryModels.kt
  SubscriptionFolder.kt, FolderDisplaySize.kt
  PodcastGenres.kt
  ArtworkUrls.kt
  EpisodeRecipeId.kt, EpisodeMediaCacheKey.kt
  ShareLinkBuilder.kt, ShareTarget.kt, EpisodeShareTarget.kt
  ContentRegion.kt, ContentLanguageSelection.kt
  CrossPromotion.kt
  FeedTelemetry.kt, RankingAggregateTelemetry.kt
  PlaybackEntryPoint.kt, AutoTranscriptState.kt, SleepTimerConstants.kt
  BoxLoreUser.kt
```

## Dependencies

- No project dependencies.
- `implementation`: kotlinx serialization JSON. `EpisodeRecipeId` uses the JDK's `MessageDigest` (SHA-256).
- Forbidden: Android framework types, Room, Retrofit, repositories and Compose UI.

## Threading / lifecycle

- Types are immutable value data or treated as such.
- No lifecycle owners, singletons, dispatchers or background work live here.

## Persistence & identity

- This module owns no files or storage keys.
- ID schemes carried by model fields (RSS podcast IDs, media IDs) are owned by their source modules; see [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage).
- `EpisodeRecipeId` is the exception: it defines the recipe scheme here and it is fixed. Never change the tag, trim set or hash; a new recipe needs a new tag and range ([`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)).
- Serialized field names of `Episode`, `Podcast` and other stored or synced models are stable; renaming one loses saved data.
- Share links use `https://boxlore.in/share`; the app still opens older `aswin.cx` links.

## Testing notes

Tests live in `core/model/src/test` (pure JVM).

| Area | Test classes |
| :--- | :--- |
| Episodes and identity | `EpisodeIdentityFieldsTest`, `EpisodeRecipeIdNamespaceTest`, `EpisodeTrailerTest`, `EpisodeMediaCacheKeyTest` |
| Sharing | `ShareLinkBuilderTest`, `EpisodeShareIdsTest` |
| Podcasts, genres, folders | `PodcastLatestEpisodeNewTest`, `PodcastGenresTest`, `SubscriptionFolderTest` |
| Artwork and Brief | `ArtworkUrlsTest`, `BriefingArtworkTest` |
| Regions and languages | `ContentRegionsTest`, `ContentLanguageSelectionTest` |
| Telemetry and account | `FeedTelemetryTest`, `BoxLoreUserTest` |

- The shared recipe fixture and `EpisodeRecipeIdTest` live in `core/rss/src/test`, where the scripts copy reads the same file; run `:core:rss:testDebugUnitTest` after any recipe change.
- `Podcast.listenerShowIds` is covered with its readers in `:core:database`'s `SwitchedShowListeningTest`.

```bash
./gradlew :core:model:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests and compiles this module for nearly every test target.
- Release keeps the JSON field names and default constructors of reflectively read models; `release-contracts.yml` checks the optimized build.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:network` README](../network/README.md)
- [`:core:database` README](../database/README.md)
