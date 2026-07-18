# `:feature:explore`

## Purpose

Explore discovery and **Learn** bottom-nav tab (curiosity card stack + Learn history). Not a Gradle dependency of other features.

## Public API

- `ExploreScreen` / `ExploreViewModel`
- `LearnScreen` / `LearnViewModel`
- `LearnHistoryScreen` / `LearnHistoryViewModel`
- `LearnCuriosityHistoryStore`

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/explore/
  components/
```

## Dependencies

- → `:core:model`, `:core:data` (re-exports `:core:prefs`), `:core:designsystem`, `:core:network` as needed
- `LearnCuriosityHistoryStore` and Explore recommendation reads use `BoxcastPrefs` (no raw `boxcast_prefs`)

## Testing notes

- JVM: Learn pagination test
- UI/Maestro must include Learn tab later (P24–P25)

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
