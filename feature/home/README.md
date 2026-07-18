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

- → `:core:model`, `:core:data`, `:core:designsystem`

## Testing notes

- Manual smoke: home feed, Settings → Add RSS entry, debug tools
- Assemblers: `HomeViewModelAssembler`, `settings.SettingsViewModelAssembler`
- Hard VM: `SettingsViewModelTest` (Turbine + fake RSS/ranking ports)

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
