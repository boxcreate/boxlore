# `:core:support`

## Purpose

Owns listener feedback as support conversations with the boxlore API: reading what the server allows (switches, limits, categories, push topics), starting and replying to conversations, reading them, and uploading photos. Wording, statuses and limits come from the server, so they change without an app update.

It does not own UI (the Feedback screens live in [`:feature:settings`](../../feature/settings/README.md)), and it does not answer messages or send pushes; that is the API's job.

## Public API

- `SupportRepository`: the one application-scoped repository, created in `AppContainer`. It takes the `SupportApi`, the old feedback form (`legacySubmit`) and a `SupportLocalStore`.
- Sending: `send(OutgoingFeedback): SendResult` (`Sent`, `SentByEmailForm`, `Refused` with the server's message, `Failed`) and `uploadPhoto(bytes, mimeType): PhotoUpload` (`Uploaded`, `Unavailable`, `Failed`).
- `meta` (`StateFlow<SupportMeta>`): the last known switches and limits; `SupportMeta.Offline` until a refresh succeeds or when support is not there.
- `threads` (`StateFlow<List<SupportThread>?>`, null until a refresh succeeds), `replyWaiting` and `hasConversations` drive the Feedback entry point and list.
- `refresh()` reads `meta` and the conversation list; `refreshIfDue(minIntervalMs)` runs one check at a time and counts a failed attempt, so an outage is not asked about on every start.
- `refreshed` announces every successful refresh's `meta`, even when nothing else changed, so push topics follow it.
- `conversation(id, before)` returns a `ConversationResult` (`Loaded`, `Gone`, `Failed`): a page of messages oldest first, with `earlier` to page back and the conversation's `ending`.
- `reply(id, body, requestId, photoIds)` returns a `ReplyResult` (`Sent`, `Closed` with the server's ending, `Refused`, `Failed`).
- `markRead(id, upTo)` sends the newest shown message's time, so a later reply stays unread. `markRead`, `reply` and `solve` refresh the list afterwards so its labels follow the server.
- `photo(id)` returns a conversation photo's bytes for its owner only.
- `onReplyPushed(threadId)` marks a reply waiting and announces it on `pushedReplies`. A list requested before the push arrived cannot clear that flag.
- Conversation models (`SupportConversations.kt`): `SupportThread`, `SupportStatus`, `SupportMessage`, `SupportEnding`, `SupportConversation`.
- `SupportLocalStore`: what the phone remembers between launches (has conversations, a reply waiting, joined push topics, last check, last push). The app backs it with its own preferences file; `SupportLocalStore.InMemory` is for tests.
- `SupportPushTopics` plans which topics to join and leave (`PushTopicPlan`) from a successful refresh's `meta` only.
- `PhotoPrep`: shrinks a picked photo to `PhotoPrep.LONG_EDGE` and re-encodes it as JPEG before upload; `targetSize` is pure.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/support/
  SupportRepository.kt      # API calls, old-form fallback, error and wire mapping
  SupportModels.kt          # SupportMeta, SupportCategory, OutgoingFeedback, SendResult, PhotoUpload
  SupportConversations.kt   # conversation models, results, SupportLocalStore, push topic plans
  PhotoPrep.kt              # photo downscaling
```

## Dependencies

- `implementation`: [`:core:network`](../network/README.md) (`SupportApi` and its wire models, built on the shared cached client).
- Libraries: OkHttp, Retrofit, kotlinx-serialization-json (error bodies), coroutines.
- Forbidden: feature modules, the design system and the catalog.

## Threading / lifecycle

- Every call runs on the injected IO dispatcher; callers use `viewModelScope`, so leaving the screen cancels them.
- Cancellation is rethrown; network and parsing errors become a `Failed` result or `null`.
- `SupportRepository` is application-scoped, so `meta`, the list and the waiting flag survive configuration changes, folding and window resizes.

## Persistence & identity

- Messages live on the server; the phone keeps only what `SupportLocalStore` remembers.
- The API knows the install from its API session and the support client's request proof (see [`:core:network`](../network/README.md)); the app never sends an ID of its own.
- `OutgoingFeedback.requestId` is the retry key: the screen sends the same key when the same message is sent again, so a send whose answer was lost is not saved twice.
- When support is not there but the old feedback form can still deliver (not found, support switched off, or a refused proof), the message goes through `legacySubmit` and the result is `SentByEmailForm`.
- The old form needs the API session too, so a missing or refused session is a failure and the screen keeps the draft. The old form knows only feature, bug and other categories and takes no photos.
- A refresh that finds support gone resets `meta` to `SupportMeta.Offline`, so a pause the server set earlier cannot keep Send off.
- `meta.categories` are the server's choices, filtered to valid IDs and bounded; empty means the app's own list.
- Support requests ask the HTTP cache not to store anything.

## Testing notes

Tests live in `core/support/src/test` (JUnit 5). They run the real `SupportApi` against MockWebServer.

| Area | Test classes |
| :--- | :--- |
| Meta, sending, fallback, photos | `SupportRepositoryTest` |
| Conversations, replies, read state, push topics, start-up check | `SupportConversationsTest` |
| Request proofs | `RequestProofTest` in `:core:network` |

```bash
./gradlew :core:support:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests and merges their Kover coverage.
- `:core:testing` architecture guards check this README and the dependency rules.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:network` README](../network/README.md)
- [`:feature:settings` README](../../feature/settings/README.md)
