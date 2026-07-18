# `:core:prefs`

## Purpose

Owns user preference persistence: DataStore `user_preferences` via `UserPreferencesRepository`, theme fast-cache SharedPreferences (`boxcast_theme_fast_cache`), and the typed façade `BoxcastPrefs` over the legacy SharedPreferences file `boxcast_prefs`.

Does **not** own analytics prefs (`boxcast_analytics_prefs`), player prefs (`boxcast_player`), privacy consent DataStore, ranking runtime SharedPreferences, or catalog cache files.

## Public API

Stable types other modules may depend on:

- `UserPreferencesRepository` — DataStore-backed settings (theme, region, smart downloads, skip, etc.)
- `Context.userPreferencesDataStore` — named DataStore delegate (`user_preferences`)
- `BoxcastPrefs` — typed API for `boxcast_prefs` keys (onboarding, genres, recommendation caches, learn curiosity, learner-log gate)
- `PlaybackSkipBounds` / `EngagementPromptConstants` — shared sanitize/threshold constants used by prefs + playback/engagement

Kotlin packages remain `cx.aswin.boxlore.core.data` (no import renames). Do **not** recreate a parallel SharedPreferences client for `boxcast_prefs` in features — call `BoxcastPrefs`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/catalog/
  UserPreferencesRepository.kt
  BoxcastPrefs.kt
```

## Dependencies

- → `:core:model` (api)
- AndroidX DataStore Preferences, core-ktx, coroutines

Forbidden reverse edges: prefs ↛ `:core:catalog`, features, playback, designsystem.

## Threading / lifecycle

- `UserPreferencesRepository` / `BoxcastPrefs` are cheap to construct (Context-backed) but Application should hold a single `UserPreferencesRepository` via `AppContainer` / `SharedAppDependencies`
- DataStore flows are cold; SharedPreferences reads/writes on the calling thread (use IO for large JSON cache decode)

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| DataStore name `user_preferences` | Existing installs |
| SharedPreferences `boxcast_prefs` (+ keys in `BoxcastPrefs`) | Existing installs |
| SharedPreferences `boxcast_theme_fast_cache` | Theme cold-start cache |

## Testing notes

- JVM + Robolectric: `src/test/.../BoxcastPrefsTest.kt` (defaults, round-trip, clear)

```bash
./gradlew :core:prefs:testDebugUnitTest
```

## CI relevance

Exercised by the unit-test CI job (`testDebugUnitTest`). Coverage floors remain on `:core:catalog` / `:core:domain` / `:feature:home` (Kover merged).

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A2)
- [`:core:catalog` README](../catalog/README.md)
