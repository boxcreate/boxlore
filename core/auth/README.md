# `:core:auth`

## Purpose

Owns account sign-in: the `AuthRepository` contract, its Firebase Auth implementation, and email-link sign-in state.

It does not own the account UI, cloud sync ([`:core:catalog`](../catalog/README.md#cloud-sync)), API sessions or request authentication ([`:core:network`](../network/README.md)), or where the pending sign-in address is stored (`:app` supplies that).

## Public API

- `AuthRepository`: the auth contract. `currentUser` (`StateFlow`) and `currentUserId`; Google, email and password, and email-link (magic link) sign-in; sign-up, password reset, `sendEmailVerification()`, `reloadUser()`, `signOut()`, `deleteAccount()` and `getIdToken(forceRefresh)`.
- `FirebaseAuthRepository`: the Firebase implementation. Its optional `onPreDeleteAccount` callback runs before the Firebase account is deleted; the app uses it to delete cloud sync data first.
- Magic links are sent on the `boxlore.in` link domain (`MAGIC_LINK_DOMAIN`) and return to `MAGIC_LINK_DEFAULT_URL`, so tapping one opens the app; `boxlore.in` must stay an authorized domain in Firebase Auth.
- `AuthException` is the base error. `SignInLinkExpiredException` means a used or expired sign-in link; `RecentLoginRequiredException` means the action needs a fresh sign-in.
- `EmailLinkSignIn`: signs in from an opened email link and reports `EmailLinkSignInStatus` (`Idle`, `SigningIn`, `SignedIn`, `Failed` with `expired` and the address). It ignores the same link arriving again while signing in, and `retry()` repeats the last link after a failure.
- `EmailLinkSignIn.accountPageRequests` rises with each link that arrives, so the app opens Account & sync once per link. Values are time-based, so a request after process recreation is still newer than one the UI saved.
- `PendingEmailStore`: the interface for the address a magic link was sent to, kept across the switch to the email app. `:app` implements it.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/auth/
  AuthRepository.kt          # contract and exceptions
  FirebaseAuthRepository.kt  # Firebase Auth implementation
  EmailLinkSignIn.kt         # email-link sign-in state
  PendingEmailStore.kt       # port for the pending sign-in address
```

## Dependencies

- `implementation`: `:core:model` (`BoxLoreUser`).
- Libraries: Firebase Auth (through the Firebase BoM), coroutines.
- Used by `:core:network` (`FirebaseAuthAuthenticator`), `:feature:settings` and `:app`; it depends on no project module other than `:core:model`.

## Threading / lifecycle

- Repository calls are `suspend` and await Firebase tasks; `currentUser` follows Firebase's auth state listener.
- `AuthRepository` and `EmailLinkSignIn` are application-scoped (`AppContainer.authRepository`, `AppContainer.emailLinkSignIn`), so a sign-in in flight survives activity recreation, rotation, folding and window resizes.
- `EmailLinkSignIn` runs sign-ins on the scope it is given.

## Persistence & identity

- This module stores nothing itself. `:app` implements `PendingEmailStore` in `AppContainer` on `BoxcastPrefs` (key `pending_auth_email`); keep that key stable so a link opened after an app update still finds its address.
- Firebase Auth keeps the signed-in account.

## Testing notes

Tests live in `core/auth/src/test` (JUnit 5, fake repositories, no Firebase).

| Area | Test classes |
| :--- | :--- |
| Contract, verification, reload, errors | `AuthRepositoryContractTest` |
| Email-link sign-in states, duplicates, retry, account page requests, expired links | `EmailLinkSignInTest` |

`AuthExceptionsTest` in `:core:network` covers the exception types and a fake `PendingEmailStore`.

```bash
./gradlew :core:auth:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests and merges their Kover coverage.
- `:core:testing` architecture guards check this README and the dependency rules.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:network` README](../network/README.md)
- [`:core:prefs` README](../prefs/README.md)
