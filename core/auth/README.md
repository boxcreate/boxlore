# `:core:auth`

## Purpose

Provides authentication abstractions and implementations (Firebase).

## Public API

- `AuthRepository` (authentication operations, Google, Magic Link, Email/Password, `sendEmailVerification()`, `reloadUser()`)
- `AuthException`, `RecentLoginRequiredException`
- `FirebaseAuthRepository` (supports optional `onPreDeleteAccount` callback invoked before Firebase account deletion)

## Internal structure

- `cx.aswin.boxlore.core.auth`
  - `AuthRepository.kt` — Core auth interface contracts
  - `FirebaseAuthRepository.kt` — Firebase Auth implementation
  - `PendingEmailStore.kt` — Magic link email persistence

## Dependencies

- `:core:model`
- Firebase Auth SDK
- Kotlinx Coroutines Android

## Threading / lifecycle

- IO / background tasks for network and authentication operations.
- Application-scoped repository instance exposed via `AppContainer`.

## Persistence & identity

- PendingEmailStore (DataStore / SharedPreferences for magic link callbacks).

## Testing notes

- Unit tests in `src/test` covering `AuthRepository` contracts, verification state, user reload, and error propagation.
- Local command:

```bash
./gradlew :core:auth:testDebugUnitTest
```

## CI relevance

- General CI pipeline.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
