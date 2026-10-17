# `:core:auth`

## Purpose

Provides authentication abstractions and implementations (Firebase).

## Public API

- `AuthRepository` (authentication operations, Google, Magic Link, Email/Password, `sendEmailVerification()`, `reloadUser()`)
- `AuthException`, `RecentLoginRequiredException`
- `FirebaseAuthRepository` (supports optional `onPreDeleteAccount` callback invoked before Firebase account deletion). Magic links are emailed on the `boxlore.in` link domain (`ActionCodeSettings.setLinkDomain`, Firebase's `/__/auth/links` page), so tapping one opens the app directly, and they return to `https://boxlore.in/auth`; `boxlore.in` must stay an authorized domain in Firebase Auth. A used or expired sign-in link fails with `SignInLinkExpiredException` (Firebase's `ERROR_INVALID_ACTION_CODE` / `ERROR_EXPIRED_ACTION_CODE`).
- `EmailLinkSignIn` and `EmailLinkSignInStatus` (Idle, SigningIn, SignedIn, Failed with `expired` and the address): signs in from an opened email link, ignores the same link arriving again while it signs in, retries the last link after a failure, and counts up `accountPageRequests` (time-based, so a request after process recreation is still newer than one the UI saved) so the app opens Account & sync once per link.

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
- `EmailLinkSignIn` is application-scoped too (`AppContainer.emailLinkSignIn`), so a sign-in in flight survives activity recreation, rotation, folding and window resizes.

## Persistence & identity

- PendingEmailStore (DataStore / SharedPreferences for magic link callbacks).

## Testing notes

- Unit tests in `src/test` covering `AuthRepository` contracts, verification state, user reload, and error propagation. `EmailLinkSignInTest` covers the sign-in states, duplicate links, retry, dismiss, rising Account page requests and the used-or-expired error codes.
- Local command:

```bash
./gradlew :core:auth:testDebugUnitTest
```

## CI relevance

- General CI pipeline.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
