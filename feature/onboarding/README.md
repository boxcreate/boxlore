# `:feature:onboarding`

## Purpose

First-run onboarding flows (genre, search, import, AI suggestions). Completes via `:core:prefs` (`BoxcastPrefs` / `UserPreferencesRepository`); do not read `boxcast_prefs` raw.

## Public API

- `OnboardingScreen` / `OnboardingViewModel`
- Supporting screens: genre / search / import / AI flows

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/onboarding/
```

## Dependencies

- → `:core:model`, `:core:data`, `:core:designsystem`

## Testing notes

- Smoke: cold start with incomplete onboarding; deep-link skip path owned by `:app`

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
