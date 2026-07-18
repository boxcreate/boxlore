# `:feature:info`

## Purpose

Podcast and episode detail screens (subscribe, RSS refresh, related/similar, cross-promo). Dual episode routes are wired from `:app`.

## Public API

- `PodcastInfoScreen` / `PodcastInfoViewModel`
- `EpisodeInfoScreen` / `EpisodeInfoViewModel`
- Supporting cards/components in the feature package

ViewModels take a **shared** `PodcastRepository` (no per-method construction).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/info/
  components/
```

## Dependencies

- → `:core:model`, `:core:data`, `:core:designsystem`

## Testing notes

- JVM: episode duration formatter; `InfoViewModelAssemblerTest` + `InfoCatalogPortBehaviorTest` (catalog port fakes for Info seam)
- Assembler: `InfoViewModelAssembler` (podcast + episode factories) — full Info VMs still need Application
- Commands: `./gradlew :feature:info:testDebugUnitTest`
- Smoke: podcast/episode subscribe, RSS pull-to-refresh

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
