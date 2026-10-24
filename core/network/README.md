# `:core:network`

## Purpose

Owns the Retrofit API boundary, OkHttp/Retrofit construction, request and response DTOs, and network serialization contracts for boxlore's HTTP API. It does not own repositories, RSS feed parsing, Room persistence, Compose UI, or feature workflows.

## Public API

- `BoxLoreApi` defines the Retrofit service surface, including additive `GET search/typeahead` (Meili show typeahead), cloud sync endpoints (`POST user/sync/push`, `POST user/sync/pull`, `DELETE user/sync/account`), `GET episodes/match` (`EpisodeMatchResponse`: up to 1,000 newest episodes with only `id`, `guid`, `title`, `enclosureUrl`, `datePublished`, for baselines), and legacy routes. Legacy `GET search` and `POST sync` are unchanged.
- `BoxLoreApiV2` is the `/v2` surface for builds that use boxlore's own episode IDs. Paths are relative (`v2/...`) and there is no app-key parameter. It covers show details and addresses (`getPodcast`, `getShowFeeds` for the daily check of up to 500 shows, `getFeed` for an http-only show's feed document, `reportFeed`), episodes (`getEpisode`, `getEpisodes` as the last-resort list, `registerEpisode` before sharing, `getSimilarEpisodes`), search (`search`, `searchSemantic`) and discovery (trending, home bootstrap POST and GET, recommendations, because-you-like, curated vibe and curiosity, briefing metadata and `getBriefingResource`). Discovery routes answer in the v1 response shapes.
  - On `/v2`, an episode's `id` is its recipe ID. `EpisodeItem` carries the optional `servedId` (the ID older builds were given) and `catalogKey`, both absent on v1. `EpisodeItem.v2Identity()` returns recipe ID, show ID, catalog key and served ID (as a decimal string), or null when `id` is outside the recipe range or the show is missing. Use it only on `/v2` answers.
  - `V2EpisodeIds` holds the range checks: recipe IDs in `[2^52, 2^53 − 1]`, show IDs and namespaces in `1..2^53 − 1`, `isEpisodeShowId` for the episode routes (`/v2/episode`, `/v2/episodes` and registration take only shows below 2^52), a strict `parseShowId` for stored show IDs (canonical digits only; `rss:` shows return null) and `isLookupEpisodeId` (never a negative local ID). Legacy episode IDs stay strings. `V2EpisodesPaging.isValid` mirrors the `/v2/episodes` bounds (show below 2^52, `limit` 1–1000, `offset` 0–10000). The routes answer 400 outside these rules, which is permanent, so check before calling and never retry it.
  - Show answers (`V2PodcastResponse`, `V2ShowAddress`, `V2FeedReportResponse`, `V2EpisodeReply.NeedsFeed`) share `V2FeedAddress`: `fetchUrl` (null means keep the saved address), `fetchVia` (`direct` or `proxy`), `transportUrl` and `feedSource`. `readTarget(showId)` says how to read the document: `V2FeedReadTarget.Direct(url)` on `fetchUrl`, or `V2FeedReadTarget.ViaApi(showId)` for a proxied show, which is read only with `getFeed(showId)`. `transportUrl` is an API URL that needs the session: the RSS client never fetches it, and it is never saved as the show's address. `validEpNs` and `V2EpisodeRegisterResponse.validRecipeId` return null outside their ranges.
  - `getEpisode` decodes its 200 answer into the sealed `V2EpisodeReply`: `Found` (the episode) or `NeedsFeed` (read the show's feed and match by recipe ID). A 404 is an error response.
  - `getFeed` streams the document. It sends `Cache-Control: no-store`, so the shared HTTP cache never stores feeds, and takes an optional `If-None-Match`. A 304 is not a successful Retrofit response, so callers check `code()`.
  - Request models validate on construction: `V2ShowsFeedsRequest` holds 1–500 show IDs, `V2FeedReportRequest` takes a `V2FeedReportStatus` value (`http_NNN`, `timeout`, `dns`, `tls`, `parse`, `network`, `other`), and `V2EpisodeRegisterRequest` needs a show below 2^52 and a catalog key (the trimmed GUID, else the trimmed enclosure URL) of at most 2,048 characters, as the route does. A null field is left out of the JSON.
  - `V2BriefingRoute.url(baseUrl, region, date, version)` builds `/v2/briefings/<route>/<region>` links with `d` and `v`.
- `FirebaseAuthAuthenticator` provides an OkHttp `Authenticator` handling HTTP 401 Unauthorized responses with Kotlin `Mutex` refresh serialization to prevent token refresh stampedes (consuming `AuthRepository` from `:core:auth`).
- `NetworkModule` creates OkHttp, Retrofit, and related network clients. `createCachedClient(context)` builds the shared client with the 50 MiB HTTP cache; build `BoxLoreApi` and `BoxLoreApiV2` on that one client (`createBoxLoreApi(baseUrl, client)`, `createBoxLoreApiV2(baseUrl, client)`), because two caches must never share a directory. There is no context-based `createBoxLoreApi` shortcut, so nothing can open a second cache on the same directory; tests pass their own client. The clients come with automatic header redaction for `Authorization`, `X-App-Key`, `X-Firebase-AppCheck`, `X-Boxlore-Session`, `X-Session-Renew` and `X-Session-Proof`. Every request carries `X-App-Version` and `X-App-Channel` (`play` or `github`) when the app sets `appVersion` / `appChannel`. `createSessionExchangeClient()` is the plain client for `POST /auth/session` (no session interceptor, no Firebase authenticator, no cache).
- **API sessions (`session/`, 2026-10-17).** This build authenticates with API session tickets only and **never sends the bundled app key**: `ApiSessionInterceptor` strips `X-App-Key` from every request. `NetworkModule.sessionManager` is set by the app at startup.
  - `ApiSessionManager` owns the install's session. One shared `POST /auth/session` exchange runs at a time; `start()` begins it at launch when no usable session is stored, and requests wait for it (at most 15 s) only then: a fresh install, or after the 30-day attestation window. With a stored session, requests attach it without waiting. Failed exchanges back off (2 s, 5 s, 15 s, 30 s, 1 min, then every 5 min, or the server's `Retry-After`); inside a backoff, requests go out without a session at once. There is no app-key fallback.
  - Exchanges send `{installId, platform: "android", channel, devicePublicKey}` (plus `turnstileToken` for a step-up) with `X-Firebase-AppCheck`. App Check is fetched only here, bounded to 6 s; a phone that has failed App Check gets its next session without asking App Check on the critical path.
  - Renewal: once a token is past `exp` (30 s early, for requests in flight), requests add `X-Session-Proof: <unix seconds>.<signature>`, a SHA256withECDSA signature of `bls-renew.<sid>.<unix seconds>` by the device key (`AndroidKeystoreDeviceKey`: non-exportable EC P-256 in StrongBox when available, else the TEE). One proof is reused for up to 2 minutes. `X-Session-Renew` from a network response (never a cached one) replaces the token when it extends the same session. The API clock is learned from each token's `iat`, so a wrong phone clock does not break renewals.
  - `session_expired` / `session_invalid` / `session_revoked`: the interceptor gets one new session and retries once. A session refused within a minute of being issued is not replaced again (the request returns its 401 and the exchange backs off), so a misconfigured API can't cause an exchange storm.
  - Limited sessions (`attestation: none`): `ApiSessionScheduler` retries App Check in the background only while the app is in the foreground with a network, about a minute after the first failure, then hourly (three times), then daily, and swaps in the full session silently. Weaker sessions are also refreshed (without App Check) an hour before they run out, and App Check sessions re-attest a day before their window ends. Background exchanges are at least a minute apart.
  - `verification_required` on an AI route: `ApiSessionStepUp` asks the app's `TurnstileTokenSource` for a token, exchanges it for a `challenge` session and retries once. It runs only when `stepUpEnabled` returns true (the app keeps it off until the API can verify Turnstile). Concurrent AI requests share one step-up; a failure starts a 30-minute cooldown in which AI requests return their 401 at once, so onboarding uses its non-AI path. `daily_ai_limit` (429) is passed straight to the caller.
  - `ApiErrorCodes` reads the shield's `{"error": "<code>"}` bodies (`of(okhttp3.Response)` peeks without consuming; `of(retrofit2.Response)` reads the error body). `AI_UNAVAILABLE` lists the codes after which AI routes won't work for this session.
  - `ApiSessionListener` reports each exchange (trigger, success, duration, attestation, limited, App Check attempt and failure category, server reason), the first renewal of each token or a renewal problem, and step-up outcomes; the app forwards them to `:core:analytics`.
- `FirebaseAuthAuthenticator` ignores 401s carrying shield codes (`ApiErrorCodes.SHIELD_CODES`): those belong to the session interceptor, and a Firebase token can't fix them.
- `StreamingJsonConverterFactory` streams response deserialization directly from OkHttp's `ResponseBody.byteStream()` via `Json.decodeFromStream` without monolithic string buffering.
- DTOs under `cx.aswin.boxlore.core.network.model`, including recommendation, bootstrap, content catalog/v3, history, sync, user cloud sync (`UserSyncModels.kt` with `feedUrl` support on `UserSubscriptionSyncDto`), and request payload models. Onboarding curriculum / genre-synth / similar-shows requests accept optional `languages` (chip codes; proxy expands and defaults from country).
- Next-turn onboarding requests may opt into `guided_v1`. `GuidedOnboardingTurn` adds separate response/question text, choices, action IDs and an explicit kind/intent alongside the legacy message/options/search fields. `OnboardingNextTurnRequest` also carries optional `features` (the app sends `finish_hint`), `country` and `languages`, all omitted when null; `GuidedOnboardingTurn.finish` (`hidden` / `available` / `prominent`) is decoded when the API sends it. Its absence remains compatible with older responses; synthesis requests do not implicitly opt in. Response interpretation belongs to onboarding rather than the HTTP layer.
- `getOnboardingProbeDeck` (`POST onboarding/probe-deck`) returns `OnboardingProbeDeckResponse` (2-4 `OnboardingProbeCard`s or none; preview fields optional). `OnboardingNextTurnRequest.probeReactions` (`OnboardingProbeReaction`: id, title, `listen` / `like` / `dislike` / `unknown`) is omitted when null and is sent with next-turn, synthesize and probe-deck requests. `OnboardingProbeCard.toPodcast()` maps a card to the app's podcast model.
- Onboarding synthesis, curriculum, genre and similar-show DTOs accept optional `recommendationFormat`. The updated flow explicitly sends `discovery_v2` to permit up to six recommendation rows; defaults omit the field, preserving older request behavior. Curriculum decoding preserves the API's row and show order and forwards opaque search queries unchanged. No response/storage migration is required.
- App version, channel, session manager and device-header hooks used by application wiring. The bundled public key is still passed to `BoxLoreApi` methods for source compatibility, but the interceptor removes it before any request leaves.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/network/
  FirebaseAuthAuthenticator.kt
  BoxLoreApi.kt
  BoxLoreApiV2.kt              # /v2 routes, V2BriefingRoute
  NetworkModule.kt
  StreamingJsonConverterFactory.kt
  model/
    UserSyncModels.kt
    V2Models.kt                # /v2 request and response models, V2EpisodeIds range checks
  session/
    ApiSessionManager.kt       # shared exchange, request headers, renewals, rejections
    ApiSessionBook.kt          # the manager's state (token, clock offset, backoffs, proof cache)
    ApiSessionScheduler.kt     # foreground upkeep: App Check retries, refreshes
    ApiSessionStepUp.kt        # Turnstile step-up with cooldown
    ApiSessionPolicy.kt        # pure timing rules
    ApiSessionInterceptor.kt   # OkHttp: attach, renew, retry once; strips X-App-Key
    ApiSessionToken.kt         # bls1 token claims
    ApiSessionPorts.kt         # App Check, Turnstile, device key, store, listener ports
    ApiErrorCodes.kt
    OkHttpSessionExchanger.kt  # POST /auth/session
    AndroidKeystoreDeviceKey.kt
    DataStoreApiSessionStore.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:auth`.
- Libraries: Retrofit, Kotlinx serialization, OkHttp, OkHttp logging interceptor, Gson, AndroidX annotation, AndroidX DataStore Preferences (the session store), and coroutines. No Firebase: App Check and Turnstile come in through ports.
- Reverse-edge rule: network must not depend on catalog, database, playback, downloads, designsystem, or feature modules.

## Threading / lifecycle

- OkHttp uses its own dispatcher threads.
- Retrofit suspend functions must be called from coroutines, normally from repository IO paths.
- Production clients are process-scoped when created by application wiring. `PodcastRepository` owns both API versions and builds them on one cached client.
- `ApiSessionManager` runs on the application scope (IO). The interceptor calls it on OkHttp threads and blocks only while waiting for a missing session, a one-time recovery, or a step-up on an AI route. Persistence is asynchronous and serialized. Listener callbacks arrive off the main thread.

## Persistence & identity

- `BootstrapRequest.include` lists the reply parts the app uses (`INCLUDE_BRIEFING`, `INCLUDE_TRENDING`, `INCLUDE_CURATED_VIBES`, `INCLUDE_RECOMMENDATIONS`). It is omitted when null, which older Workers and app versions treat as every part.
- No user data is persisted by this module.
- API session state lives in the DataStore file `boxlore_api_session` (keys `session_token`, `install_id`, `clock_offset_seconds`, `app_check_failures`, `next_app_check_at_ms`). The install id is random (16 bytes, base64url) and separate from the analytics and sync device ids. The device key is the Keystore alias `boxlore_api_session_device_key`. Keep both names stable: renaming signs every install out of its session. The app's backup rules exclude the file, because a restored token could never renew without this phone's Keystore key.
- Base URL, public key, app version, App Check tokens, and device identifiers are supplied at runtime by app or repository wiring.
- API path and payload shape changes should be tested as contract changes.
- `/v2` identity rules: recipe IDs are safe integers in `[2^52, 2^53 − 1]`; show IDs never change and are the same numbers as on v1; legacy episode IDs (including negative `rss:` IDs) stay opaque decimal strings and negative ones are never sent.

## Testing notes

- Unit tests live under `core/network/src/test`.
- `BoxLoreApiContractTest` uses MockWebServer fixtures for endpoint contracts.
- Prefer MockWebServer over live backend calls.
- `session/ApiSessionInterceptorTest` (MockWebServer): a stored session goes out at once while an exchange hangs and no request carries `X-App-Key`; one exchange serves concurrent first requests; `X-Session-Renew` is stored and foreign or older renewals are ignored; the proof appears only from 30 s before `exp`; a missing device key sends no proof and reports it once; a refused session is replaced once; a brand-new session refused again backs off; failed exchanges back off without an app-key fallback; App Check failure gives a limited session and the next launch skips App Check; the step-up retries with a `challenge` session, a failed step-up returns the 401 and cools down, and it stays off until enabled; `daily_ai_limit` passes through; other hosts get no session.
- `session/ApiSessionSchedulerTest` (virtual time): limited → full upgrade on the 1 min / hourly / daily schedule, nothing in the background or offline, an overdue retry when the network returns, and the pure next-step plan. `ApiSessionPolicyTest` covers token parsing, proof timing, usability, replacement and backoffs. `OkHttpSessionExchangerTest` covers the `/auth/session` body, headers and failure reasons. `FirebaseAuthAuthenticatorTest` checks shield 401s are left alone.
- `BoxLoreApiV2ContractTest` (MockWebServer) checks every `/v2` path (also under a base URL with a path prefix) and that no request carries `X-App-Key`; the exact request JSON of shows/feeds, feed reports, registration, recommendations, bootstrap, similar episodes (a recipe ID `id`, `limit` left at its default is not sent) and because-you-like (with and without `excludePodcastId` and `country`) (defaults are not encoded, so only explicitly set fields go out); decoding of show addresses (including `fetchUrl: null` and proxied shows), that a proxied show is read with `getFeed` on the base URL and never at the server's `transportUrl`, `needsFeed` with and without an address, a known episode with its identity, the fallback list, a 404 and a 304; that feed documents never enter the HTTP cache; and briefing URLs. `model/V2ModelsTest` covers the range checks (including the episode-route show limit and `/v2/episodes` paging bounds), strict show ID parsing, request validation (including the 2,048-character catalog key) and read targets.
- `OnboardingGuidedContractTest` checks legacy decoding, request-specific opt-in and structured recovery action decoding, including unknown additive fields, and the finish-hint opt-in (features, country, languages omitted by default; `finish` decoded), plus show deck cards with and without a preview and opt-in reactions.

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

### Release optimization

Release keeps both Retrofit APIs (`BoxLoreApi`, `BoxLoreApiV2`), generic `Call`/`Response` types and JSON DTO contracts, while optimizing HTTP/runtime implementations using library consumer rules. The artifact gate verifies every API method and nested dynamic serializer after R8; JVM tests alone do not establish release reflection safety.
