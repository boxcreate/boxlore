# `:core:domain`

## Purpose

Owns thin domain ports (interfaces) and small result types that feature ViewModels and workers depend on for fakes/tests — without pulling in full repositories from `:core:data`. Deliberately does **not** own Room entities, Retrofit clients, ranking engines, or Compose.

## Public API

Stable types/entry points other modules may depend on:

- `ports.RssSubscriptionPort` / `RssSubscriptionResult`
- `ports.RankingResetPort`
- `ports.PodcastCatalogPort`
- `ports.HistoryRecommendationSource` (uses network `HistoryItem`)

`ListeningHistoryBackupPort` stays in `:core:data` (uses Room `ListeningHistoryEntity` to avoid a domain → database edge).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/domain/
  RssSubscriptionResult.kt
  ports/
```

## Dependencies

Gradle edges (project + notable libs):

- → `:core:model` (api)
- → `:core:network` (api; `HistoryItem` only)
- kotlinx-coroutines

Forbidden reverse edges: domain ↛ `:core:data`, `:core:database`, `:core:playback`, features, or designsystem.

`:core:data` depends on this module (`api`) and implements the ports.

## Testing notes

- JVM unit tests under `src/test` for port fakes / result shaping
- Prefer constructor injection + fakes over full repository graphs

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:data` README](../data/README.md) — production implementations
