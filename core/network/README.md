# `:core:network`

## Purpose

Owns the Retrofit API boundary, OkHttp/Retrofit construction, request and response DTOs, and network serialization contracts for boxlore's HTTP API. It does not own repositories, RSS feed parsing, Room persistence, Compose UI, or feature workflows.

## Public API

- `BoxLoreApi` defines the Retrofit service surface, including additive `GET search/typeahead` (Meili show typeahead), cloud sync endpoints (`POST user/sync/push`, `POST user/sync/pull`, `DELETE user/sync/account`), and legacy routes. Legacy `GET search` and `POST sync` are unchanged.
- `FirebaseAuthAuthenticator` provides an OkHttp `Authenticator` handling HTTP 401 Unauthorized responses with Kotlin `Mutex` refresh serialization to prevent token refresh stampedes (consuming `AuthRepository` from `:core:auth`).
- `NetworkModule` creates OkHttp, Retrofit, and related network clients, with automatic header redaction for `Authorization`, `X-App-Key`, and `X-Firebase-AppCheck`.
- `StreamingJsonConverterFactory` streams response deserialization directly from OkHttp's `ResponseBody.byteStream()` via `Json.decodeFromStream` without monolithic string buffering.
- DTOs under `cx.aswin.boxlore.core.network.model`, including recommendation, bootstrap, content catalog/v3, history, sync, user cloud sync (`UserSyncModels.kt` with `feedUrl` support on `UserSubscriptionSyncDto`), and request payload models. Onboarding curriculum / genre-synth / similar-shows requests accept optional `languages` (chip codes; proxy expands and defaults from country).
- App Check, app version, public-key, and device-header hooks used by application wiring.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/network/
  FirebaseAuthAuthenticator.kt
  BoxLoreApi.kt
  NetworkModule.kt
  StreamingJsonConverterFactory.kt
  model/
    UserSyncModels.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:auth`.
- Libraries: Retrofit, Kotlinx serialization, OkHttp, OkHttp logging interceptor, Gson, AndroidX annotation, and coroutines.
- Reverse-edge rule: network must not depend on catalog, database, playback, downloads, designsystem, or feature modules.

## Threading / lifecycle

- OkHttp uses its own dispatcher threads.
- Retrofit suspend functions must be called from coroutines, normally from repository IO paths.
- Production clients are process-scoped when created by application wiring.

## Persistence & identity

- No user data is persisted by this module.
- Base URL, public key, app version, App Check tokens, and device identifiers are supplied at runtime by app or repository wiring.
- API path and payload shape changes should be tested as contract changes.

## Testing notes

- Unit tests live under `core/network/src/test`.
- `BoxLoreApiContractTest` uses MockWebServer fixtures for endpoint contracts.
- Prefer MockWebServer over live backend calls.

```bash
./gradlew :core:network:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs network JVM contract tests.
- Network DTO compile failures block catalog and feature modules that map API responses.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:rss` README](../rss/README.md)
