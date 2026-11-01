# `:core:support`

## Purpose

Listener feedback as support conversations with the boxlore API (`/support/...`): reading what the
server allows (switches, limits, the push topic), sending a new message, and uploading photos.
Wording, statuses and limits come from the server, so they change without an app update.

It does not own any UI (the screens live in `:feature:settings`) and does not talk to Telegram or
send pushes; that is the API's job.

## Public API

- `SupportRepository`: `meta` (`StateFlow<SupportMeta>`), `refresh()`, `uploadPhoto(bytes, mimeType)`,
  `send(OutgoingFeedback): SendResult`. Application-scoped: create it once in `AppContainer`.
- `SupportMeta`, `OutgoingFeedback`, `SendResult` (`Sent`, `SentByEmailForm`, `Refused`, `Failed`).
- `PhotoPrep`: shrinks a picked photo (long edge 1600 px, JPEG) before upload; `targetSize` is pure.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/support/
  SupportRepository.kt   API calls, old-form fallback, error mapping
  SupportModels.kt       SupportMeta, OutgoingFeedback, SendResult
  PhotoPrep.kt           photo downscaling
```

## Dependencies

- → `:core:network` (`SupportApi`, wire models; built on the shared cached client)
- OkHttp, Retrofit, kotlinx-serialization (error bodies), coroutines

Features use it; it never depends on a feature, the design system or the catalog.

## Threading / lifecycle

- All calls run on the injected IO dispatcher; callers use `viewModelScope`.
- Cancellation is rethrown; network and parsing errors become `SendResult.Failed` or `null`.

## Persistence & identity

- Nothing stored on the phone. The API knows the install from the API session the shared client
  attaches (`X-Boxlore-Session`); the app never sends an id of its own.
- Fallback: when the support API answers 404, 401 or 503 `support_unavailable`, the message goes
  through the old `POST /feedback` form (`legacySubmit`), so feedback sent before the server side is
  live (or without a session) is never lost. The old form has no `audio` category (sent as `bug`)
  and no photos.

## Testing notes

- `SupportRepositoryTest` runs the real `SupportApi` against MockWebServer: meta parsing, sending,
  the fallback cases, refusals with the server's message, no connection, photos off, `PhotoPrep.targetSize`.

```bash
./gradlew :core:support:testDebugUnitTest
```

## CI relevance

Runs in the unit-test workflow like every `:core:*` module; included in merged Kover coverage.

## See also

- The API's support routes live in the separate API repository (`/support/...`).
- [`feature/settings/README.md`](../../feature/settings/README.md) (Feedback screens)
