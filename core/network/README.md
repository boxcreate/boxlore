# `:core:network`

## Purpose

Extracted network module for the Boxlore HTTP/API layer. Owns the Retrofit API client (`BoxLoreApi`), `NetworkModule` wiring, and network DTOs (Podcast Index proxy payloads, content sections, sync models such as `HistoryItem`, etc.). Does **not** own Room, RSS feed parsing (`RssFeedClient` stays in `:core:data`), Compose, or repositories.

## Public API

Stable types/entry points other modules may depend on:

- `NetworkModule` / `BoxLoreApi` — OkHttp + Retrofit client factory and API surface
- Network model types under `cx.aswin.boxlore.core.network.model` (including `HistoryItem`, content-section DTOs, sync models)
- App Check / version header hooks used by the app

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/network/
  BoxLoreApi.kt
  NetworkModule.kt
  model/          # request/response DTOs
```

## Dependencies

Gradle edges (project + notable libs):

- → `:core:model`
- OkHttp, Retrofit, Gson / kotlinx.serialization as configured in Gradle

Forbidden reverse edges: network ↛ `:core:data`, `:core:database`, features, or designsystem.

## Testing notes

- JVM tests for request serialization (e.g. content sections) under `src/test`
- Prefer MockWebServer over hitting the live backend

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:data` README](../data/README.md) — repositories that call this API; RSS client remains there
