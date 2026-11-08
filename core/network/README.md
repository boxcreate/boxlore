# `:core:network`

## Purpose

Owns boxlore's HTTP API boundary: the Retrofit services, OkHttp and Retrofit construction, request and response DTOs, JSON serialization, and the client side of API sessions and request proofs.

It does not own repositories ([`:core:catalog`](../catalog/README.md)), RSS parsing ([`:core:rss`](../rss/README.md)), Room, Compose UI or feature workflows. Firebase is not a dependency: App Check and other attestation reach it through ports.

## Public API

### Services and clients

- `BoxLoreApi`: the v1 service (search and typeahead, trending, recommendations, bootstrap, onboarding, content catalog, `/sync`, cloud sync push, pull and delete, feedback). `GET episodes/match` serves only the comparison list for a Podcast Index show's feed-only extras.
- `BoxLoreApiV2`: the `/v2` service for boxlore's own episode IDs (shows and feed addresses, episodes, registration, search, discovery, briefings). Paths are relative and carry no app key; discovery routes answer in the v1 shapes.
- `SupportApi`: the support conversation routes used by [`:core:support`](../support/README.md); nothing it returns enters the HTTP cache.
- `NetworkModule.createCachedClient(context)`: the one shared OkHttp client with the HTTP cache. Build every service on it (`createBoxLoreApi`, `createBoxLoreApiV2`, `createSupportApi`); two caches must never share a directory, so there is no context-based shortcut.
- `NetworkModule.createSessionExchangeClient()`: the plain client for session exchanges (no session interceptor, no authenticator, no cache).
- `NetworkModule.appVersion` / `appChannel` are sent as `X-App-Version` and `X-App-Channel`; `sessionManager`, `authenticator` and `accountToken` are set by the app at startup.
- Debug logging redacts every credential, App Check, session and proof header; release builds log nothing.
- `StreamingJsonConverterFactory`: decodes responses straight from the body stream, without buffering a whole string.
- `EnglishOffInterceptor` (first on the API client): adds `englishOff` to requests whose `languages` leave English out; every other request passes through unchanged.
- `FirebaseAuthAuthenticator`: refreshes the Firebase ID token on a 401, one refresh at a time (`Mutex`), using `AuthRepository` from [`:core:auth`](../auth/README.md). It ignores 401s carrying `ApiErrorCodes.SHIELD_CODES`, which belong to the session layer.
- `AccountTokenInterceptor`: adds the signed-in account's ID token to support requests.

### API sessions and request proofs (`session/`)

- `ApiSessionManager`: owns this install's API session (obtaining it, attaching it to requests and keeping it valid). The bundled app key is never sent.
- `ApiSessionInterceptor`: the OkHttp side of the session; it removes the bundled app key from every request.
- `ApiSessionPorts.kt`: the ports for attestation tokens, the device key (`DeviceKeySigner`), storage (`ApiSessionStore`), the exchange (`SessionExchanger`) and outcomes (`ApiSessionListener`, which the app forwards to [`:core:analytics`](../analytics/README.md)).
- `OkHttpSessionExchanger`, `AndroidKeystoreDeviceKey` (non-exportable Keystore key) and `DataStoreApiSessionStore`: the production adapters for those ports.
- `ApiErrorCodes`: reads the API's error codes; callers branch on its constants instead of string literals.
- `RequestProofSigner` and `RequestProofInterceptor`: prove that support requests for an install's private data come from that install.

### `/v2` models (`model/V2Models.kt`)

- `V2EpisodeIds`: the range checks for recipe IDs, show IDs and lookup IDs, and strict `parseShowId` (`rss:` shows return null). Check before calling: the routes refuse IDs outside these ranges permanently, so never retry them.
- `V2EpisodesPaging.isValid` mirrors the `/v2/episodes` bounds.
- `EpisodeItem.v2Identity()`: recipe ID, show ID, catalog key and served ID from a `/v2` episode, or null; use it only on `/v2` answers.
- `V2FeedAddress` (on `V2PodcastResponse`, `V2ShowAddress`, `V2FeedReportResponse` and `V2EpisodeReply.NeedsFeed`): `readTarget(showId)` returns `V2FeedReadTarget.Direct(url)` or `ViaApi(showId)`. A proxied show is read only with `getFeed`; its transport URL is never fetched by the RSS client or saved as an address.
- `V2EpisodeReply`: `getEpisode` decodes a 200 into `Found` or `NeedsFeed` (read the feed and match by recipe ID); a 404 is an error response.
- `getFeed` streams the document with `Cache-Control: no-store` and an optional `If-None-Match`; a 304 is not a successful Retrofit response, so callers check `code()`.
- Request models validate on construction: `V2ShowsFeedsRequest`, `V2FeedReportRequest` (a `V2FeedReportStatus` value) and `V2EpisodeRegisterRequest`.
- `V2BriefingRoute.url(...)` builds briefing resource links.
- `V2BootstrapRequest` carries the current recommendation body (`RecommendationsV2Request`), because `/v2` reads seeds and exclusions as recipe IDs.

### Other DTOs (`model/`)

- `RecommendationsV2Request.contractVersion` has no default; pass `RecommendationsV2Request.CONTRACT_VERSION` on every recommendations request, v1 and `/v2`.
- `BootstrapRequest.include` lists the reply parts the app uses (`INCLUDE_*`); null means every part.
- Cloud sync (`UserSyncModels.kt`): `SyncPushRequest`, `SyncPullRequest` and their responses, with optional episode identity (`recipeId`, `catalogKey`, rich queue removals) and listening days. `SyncEpisodeIdentityFields` treats out-of-range values as unknown. Semantics: [listening and cloud sync](../catalog/README.md#cloud-sync).
- Onboarding (`OnboardingModels.kt`): opt-in fields (`responseFormat` for `GuidedOnboardingTurn` answers, `features`, `languages`, `recommendationFormat`, `probeReactions`) are omitted when null, so older request shapes are unchanged. `OnboardingProbeCard.toPodcast()` maps a show-deck card.
- `SupportModels.kt`, `SyncModels.kt`, `PodcastIndexModels.kt`, `CuratedCuriosityModels.kt` and `ITunesResponse.kt` hold the remaining wire models.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/network/
  BoxLoreApi.kt                  # v1 routes
  BoxLoreApiV2.kt                # /v2 routes, V2BriefingRoute
  SupportApi.kt                  # support conversation routes
  NetworkModule.kt               # clients, cache, service factories
  EnglishOffInterceptor.kt
  AccountTokenInterceptor.kt
  FirebaseAuthAuthenticator.kt
  StreamingJsonConverterFactory.kt
  model/
    V2Models.kt                  # /v2 models and range checks
    UserSyncModels.kt            # cloud sync push and pull
    SyncModels.kt                # recommendations, bootstrap, /sync
    OnboardingModels.kt
    SupportModels.kt
    PodcastIndexModels.kt
    CuratedCuriosityModels.kt
    ITunesResponse.kt
  session/
    ApiSessionManager.kt         # owns the install's session
    ApiSessionBook.kt            # the manager's in-memory state
    ApiSessionScheduler.kt       # background session upkeep
    ApiSessionStepUp.kt          # extra verification for AI routes
    ApiSessionPolicy.kt          # pure session rules
    ApiSessionInterceptor.kt
    ApiSessionToken.kt           # reads session claims
    ApiSessionPorts.kt
    ApiErrorCodes.kt
    OkHttpSessionExchanger.kt
    AndroidKeystoreDeviceKey.kt
    DataStoreApiSessionStore.kt
    RequestProofSigner.kt
    RequestProofInterceptor.kt
```

## Dependencies

- `implementation`: `:core:model`, `:core:auth`.
- Libraries: Retrofit, OkHttp and its logging interceptor, kotlinx-serialization-json, Gson, coroutines, AndroidX annotation and DataStore Preferences (the session store).
- Forbidden: catalog, database, playback, downloads, designsystem or feature modules; no Firebase SDK.

## Threading / lifecycle

- Retrofit services are `suspend` and called from repository IO paths; OkHttp runs on its own dispatcher threads.
- Clients are process-scoped. `PodcastRepository` builds both API versions and the support service on the one cached client.
- `ApiSessionManager` runs on the application scope it is given. The interceptor calls it on OkHttp threads and may block a request briefly while a session is obtained.
- Session persistence is asynchronous and serialized; `ApiSessionListener` callbacks arrive off the main thread.

## Persistence & identity

Never rename these:

- DataStore file `boxlore_api_session` (`DataStoreApiSessionStore.FILE_NAME`) and its keys; the app's backup rules exclude it because it is bound to this phone's Keystore key.
- Keystore alias `boxlore_api_session_device_key` (`AndroidKeystoreDeviceKey.DEFAULT_ALIAS`). Renaming either signs every install out of its session.
- The install ID inside the session store is random and separate from the analytics and sync device IDs.
- Recipe IDs, show IDs and legacy episode IDs on the wire follow [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md); negative `rss:` IDs are never sent.
- No other user data is stored here. Base URL, app version, attestation tokens and device identifiers come from app wiring at runtime.

## Testing notes

Tests live in `core/network/src/test` (JUnit 5, MockWebServer, virtual time); recorded answers are in `src/test/resources/fixtures`. Treat a path or payload change as a contract change and cover it here.

| Area | Test classes |
| :--- | :--- |
| v1 routes | `BoxLoreApiContractTest` |
| `/v2` routes and models | `BoxLoreApiV2ContractTest`, `model/V2ModelsTest` |
| Cloud sync payloads | `BoxLoreApiSyncContractTest` |
| Onboarding payloads | `OnboardingGuidedContractTest` |
| Interceptors, converter | `EnglishOffInterceptorTest`, `StreamingJsonConverterFactoryTest` |
| Token refresh, auth types | `FirebaseAuthAuthenticatorTest`, `AuthExceptionsTest` |
| Sessions and proofs | `session/ApiSessionInterceptorTest`, `ApiSessionSchedulerTest`, `ApiSessionPolicyTest`, `OkHttpSessionExchangerTest`, `RequestProofTest` |

```bash
./gradlew :core:network:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests and merges their Kover coverage.
- DTO compile failures block catalog, support and the feature modules that map API responses.
- Release keeps both Retrofit services and their JSON DTOs; `release-contracts.yml` verifies every API method and serializer in the optimized build, which JVM tests cannot prove.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:support` README](../support/README.md)
- [`:core:auth` README](../auth/README.md)
