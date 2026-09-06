# `:core:model`

## Purpose

Owns shared domain models, enums, and pure value helpers used across network, data, playback, and UI modules. It does not own network DTOs, Room entities, Compose UI, repositories, or Android lifecycle behavior.

## Public API

- Podcast, episode, briefing, chapter, person, transcript, and playback-adjacent model types.
- `EpisodeMediaCacheKey`: Media3 `customCacheKey` helper — briefing keys append audio URL `v=` so same-day regenerations bust the local audio cache.
- `ContentRegion` / `ContentRegions`: 11 chart storefronts, language allowlist/normalize/expand (`id`→`id,in`), off-market soft-warn helpers, and briefing market mapping that preserves the explicit `global` briefing tab.
- `ContentLanguageSelection`: pure language-chip toggle rules (English lock, max languages, BCP47 normalization) for settings/onboarding pickers.
- `PlaybackEntryPoint`, `ShareTarget`, and `ShareLinkBuilder`.
- `PlaybackEntryPoint` coarse values: `GENERIC`, `HOME_MIXTAPE`, `LEARN`, `BRIEFING`
  (Brief audio play synthesizes glossary `entry_point=briefing` for `playback_*`).
- `AutoTranscriptState`.
- `PodcastGenres` and `RankingAggregateTelemetry`.
- Cross-promotion model types.
- `Podcast.isLatestEpisodeNew`: shared NEW badge. Room `rssHasNewEpisodes` is true for true-RSS freshness **and** for Podcast Index direct-feed tip promotions (`updateLatestEpisode(..., markAsNew = true)`). Opening the show clears the flag. Otherwise the 48h window / last-seen id rules apply.
- `Podcast.effectiveGenre`: resolves user `customGenre` override when non-blank, falling back to default catalog `genre`. Companion `customGenreIcon` stores the icon identifier for custom tags.
- `Podcast.recommendationGenre`: resolves canonicalized `customGenre` via `PodcastGenres.canonicalize` so valid standard reclassifications adapt personalized recommendations and Smart Queue, falling back to `genre` when the tag is arbitrary.
- `FolderDisplaySize`: display sizing enum (`COMPACT`, `FEATURED`, `SHELF`) for library subscription folders.
- `SubscriptionFolder`: domain model representing a custom folder organizing subscribed podcasts with optional icon, display size, and linked genre auto-syncing.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/model/
  Briefing.kt
  Chapter.kt
  CrossPromotion.kt
  Episode.kt
  EpisodeMediaCacheKey.kt
  Podcast.kt
  PodcastGenres.kt
  RankingAggregateTelemetry.kt
  ShareLinkBuilder.kt
  SleepTimerConstants.kt
  Transcript.kt
  ContentRegion.kt
  ContentLanguageSelection.kt
  ...
```

## Dependencies

- Project dependencies: none.
- Libraries: Kotlinx serialization.
- Reverse-edge rule: Android framework, Room, Retrofit implementation details, repositories, and Compose UI must stay out of this module.

## Threading / lifecycle

- Types are immutable or treated as immutable value data.
- No lifecycle owners, singletons, dispatchers, or background work are created here.

## Persistence & identity

- This module owns no persistence files or storage keys.
- ID schemes carried by model fields are owned by their source modules, such as RSS podcast IDs and playback media IDs.

## Testing notes

- Unit tests live under `core/model/src/test`.
- `ShareLinkBuilderTest` covers share URL invariants.
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
