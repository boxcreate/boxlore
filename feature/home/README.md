# `:feature:home`

## Purpose

Home feed, Settings hub (incl. Add RSS), and Debug tools. Owns presentation; dependencies are injected from `:app` `AppContainer`.

## Public API

- `HomeRoute` / `HomeViewModel`
- `settings.SettingsScreen` / `SettingsViewModel` (RSS add + recommendation reset)
- `DebugScreen` / `DebugViewModel`

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/home/
  components/ settings/ settings/pages/ settings/dialogs/
```

## Dependencies

- → `:core:model`, `:core:data` (re-exports `:core:prefs`), `:core:designsystem`
- Recommendation / genre caches and learner-log toggle go through `BoxcastPrefs` (no raw `boxcast_prefs`)

## Testing notes

- Manual smoke: home feed, Settings → Add RSS entry, debug tools
- Assemblers: `HomeViewModelAssembler`, `settings.SettingsViewModelAssembler`
- Hard VM: `SettingsViewModelTest` (Turbine + fake RSS/ranking ports via assembler)
- Home slice (B2): `DiscoveryGreetingTest` — pure daypart greeting helper extracted from `HomeViewModel` (full Home VM still needs Application + heavy fakes / Robolectric; deferred)
- Commands: `./gradlew :feature:home:testDebugUnitTest`
- Participates in Kover merged coverage (`:koverVerifyMerged` in unit-tests CI)

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
