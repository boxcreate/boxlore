# `:core:designsystem`

## Purpose

Owns shared Compose visual primitives: theme, typography, shapes, motion, loaders, image helpers, navigation chrome constants, and share-card UI. It does not own feature navigation, repositories, network clients, Room access, or business workflows.

## Public API

- `BoxLoreTheme` and theme helpers such as expressive shapes, motion, typography, and dynamic color utilities.
- Shared components including `OptimizedImage`, loaders, player-control primitives used by UI modules, bottom navigation chrome constants, and sleep-timer chrome.
- `share.ShareManager` for composite share cards and the system share sheet; emits glossary `share_content` via `:core:analytics`.
- `share.ShareCardRenderer` builds the share-card bitmaps used by `ShareManager` (stories / message formats).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/designsystem/
  component/
  components/
  share/
  theme/
src/main/res/
  drawable/
  font/
```

## Dependencies

- Project dependencies: `:core:model`, `:core:analytics`, `:core:prefs` (lettering axis + shared Google Sans Flex `Typeface` loader).
- Libraries: Compose Material, Material icons, graphics shapes, Coil, AndroidX activity/core, smooth corner rect, and coroutines.
- Reverse-edge rule: designsystem must not depend on catalog, network, database, playback, downloads, or feature modules.

## Threading / lifecycle

- Compose UI work runs on the main thread.
- Coil performs image loading on background dispatchers.
- The theme is applied by `:app` and feature composition roots; this module owns no application-scoped repositories.

## Persistence & identity

- No user data, database files, DataStore names, or SharedPreferences files are owned here.
- Resource names are app-internal UI contracts and should be changed with normal Android resource migration care.
- UI typeface: bundled **Google Sans Flex** variable font (`res/font/google_sans_flex_variable.ttf`). Default **ROND = 50 (Soft)**; Appearance → Lettering can switch Crisp (0) / Soft (50) / Round (100) via `FontRoundness`, `LocalFontRoundness`, and `BoxLoreTheme(fontRoundness=…)`. Factories live in `Typography.kt`; Android `Typeface` loads go through `GoogleSansFlexTypeface` / `GoogleSansTypefaces`. Shared connected chips: `ConnectedOptionSelector` (Appearance, `RegionSegmentedSelector`, Library history period/status filters). SIL OFL 1.1 text: [`licenses/GoogleSansFlex-OFL.txt`](licenses/GoogleSansFlex-OFL.txt). Roboto Flex paths (`RobotoFlexFamily`, `LogoFontFamily`, `robotoflex_variable`) are unchanged.
- Lettering roundness is mirrored in `boxlore_theme_fast_cache` key `font_roundness` (owned by `:core:prefs`) so share cards / Auto collage can read it without a Compose tree.

## Testing notes

- Unit tests live under `core/designsystem/src/test`.
- `ThemeBrandTokensTest` covers brand seed and contrast helper behavior.
- Screenshot goldens (Roborazzi) live in feature modules (see `:feature:home`).

```bash
./gradlew :core:designsystem:testDebugUnitTest
```

## CI relevance

- Compiled by app and feature test jobs whenever UI modules build.
- Module JVM tests run with `unit-tests.yml`.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:app` README](../../app/README.md)
