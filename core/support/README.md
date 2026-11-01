# `:core:support`

## Purpose

Listener feedback as support conversations with the boxlore API (`/support/...`): reading what the
server allows (switches, limits, the push topics), sending a new message, and uploading photos.
Wording, statuses and limits come from the server, so they change without an app update.

It does not own any UI (the screens live in `:feature:settings`) and does not talk to Telegram or
send pushes; that is the API's job.

## Public API

- `SupportRepository`: `meta` (`StateFlow<SupportMeta>`), `refresh()`, `uploadPhoto(bytes, mimeType)`,
  `send(OutgoingFeedback): SendResult`. Application-scoped: create it once in `AppContainer`.
- `SupportMeta`, `OutgoingFeedback`, `SendResult` (`Sent`, `SentByEmailForm`, `Refused`, `Failed`).
- `PhotoPrep`: shrinks a picked photo (long edge 1600 px, JPEG) before upload; `targetSize` is pure.
- Conversations (`SupportConversations.kt`): `threads` (`StateFlow<List<SupportThread>?>`, null until
  a refresh succeeds), `replyWaiting`, `hasConversations`, `conversation(id, before)` (a page of
  messages oldest first, `earlier` to page back, `ending`), `reply(id, body, requestId)`
  (`ReplyResult.Sent`, `Closed` with the server's ending, `Refused`, `Failed`), `markRead`, `solve`,
  `photo(id)` (bytes, owner only), `onReplyPushed(threadId)` and `refreshIfDue(minIntervalMs)`.
  `markRead(id, upTo)` sends the newest shown message's time, so a later reply stays unread; it,
  `reply` and `solve` refresh the list afterwards so its labels follow the server. `refreshed`
  announces every successful refresh's `meta` (push topics follow it even when the list didn't
  change) and `pushedReplies` the conversation a push was for. A list asked for before a push
  arrived (`lastPushAt`) can't clear the push's waiting flag. `refreshIfDue` runs one check at a
  time and counts a failed attempt, so an outage isn't asked about on every start.
- `SupportLocalStore`: what the phone remembers between launches (has conversations, a reply
  waiting, joined push topics, last check); the app backs it with its own preferences file.
  `SupportPushTopics` plans which topics to join and leave (`PushTopicPlan`) from a successful
  refresh's `meta`.

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
  attaches (`X-Boxlore-Session`) together with the device key that session registered: every
  support request carries `X-Request-Proof`, the device key's signature over the session, method
  and path (`RequestProofInterceptor` in `:core:network`, added by `NetworkModule.createSupportApi`).
  The app never sends an id of its own. Without a device key or proof the API answers 401 and the
  message goes through the old form.
- `OutgoingFeedback.requestId` is the retry key: the screen sends the same key when the same message
  is sent again, so a send whose answer was lost isn't saved twice.
- Fallback: when support isn't there but the old `POST /feedback` form can still deliver (404, 503
  `support_unavailable`, or 401 `device_key_required` / `proof_required`), the message goes through it
  (`legacySubmit`). The old form needs the API session too, so a 401 for a missing or refused session
  is a failure: the screen keeps the draft to send later. The old form knows only feature, bug and
  other (audio goes as bug, anything newer as other) and has no photos. A refresh that finds support
  gone resets `meta` to `SupportMeta.Offline`, so a pause the server set earlier can't keep Send off.
- `uploadPhoto` answers `PhotoUpload.Uploaded`, `Unavailable` (photos off, or support not there: the
  message can still go without them) or `Failed`.
- `meta.categories` are the server's choices (ids `[a-z0-9_]`, labels trimmed, at most 12); empty means
  the app's own list. Support calls are Retrofit `suspend` functions, so leaving the screen cancels
  them, and they ask the HTTP cache not to store anything (`Cache-Control: no-store`).

## Testing notes

- `SupportRepositoryTest` runs the real `SupportApi` against MockWebServer: meta parsing (push topics
  and categories filtered to valid ones, a limit above 2,000 kept), sending with the retry key, the
  fallback cases (401 for a missing proof included, a new category sent as other), no fallback without
  a session, a pause forgotten once support is gone (but kept over a plain server error), refusals with
  the server's message, no connection, photo upload results and the no-store header, photos off,
  `PhotoPrep.targetSize`.
  `RequestProofTest` in `:core:network` covers the signed message and the interceptor.
- `SupportConversationsTest` covers the list and what the phone remembers, a conversation page
  (messages, photos, ending, the `before` cursor, gone and failed), replies (retry key, closed with
  its ending, refused, failed), reading the last unread reply, a push marking a reply waiting until a
  refresh says otherwise, the start-up check (only with conversations, not more often than asked), a
  first conversation remembered at once, photo bytes, push-topic plans, the read marker and the
  refresh after it, a list from before a push keeping its waiting flag, every refresh announced
  and a push naming its conversation, and a failed start-up check still counting.

```bash
./gradlew :core:support:testDebugUnitTest
```

## CI relevance

Runs in the unit-test workflow like every `:core:*` module; included in merged Kover coverage.

## See also

- The API's support routes live in the separate API repository (`/support/...`).
- [`feature/settings/README.md`](../../feature/settings/README.md) (Feedback screens)
