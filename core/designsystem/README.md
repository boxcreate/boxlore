# `:core:designsystem`

## Purpose

Owns shared Compose visual primitives: theme, typography, shapes, motion, loaders, image helpers, navigation chrome constants, and share-card UI. It does not own feature navigation, repositories, network clients, Room access, or business workflows.

## Public API

- `BoxLoreTheme` and theme helpers such as expressive shapes, motion, typography, and dynamic color utilities. `Modifier.expressiveClickable` has a long-press overload (`onLongClick`) used by Downloads multi-select. Pass `shape` so press-shrink stays rounded; `pressScaleEnabled = false` while a drag overlay owns scale.
- Shared components including `OptimizedImage` (optional `errorContent` for blank/failed art), loaders, `PillFilterChip` (onboarding/Explore genre pills), `BoxLoreLogo` (optional `height` for hero vs chrome sizes), player-control primitives used by UI modules, floating 3+1 navigation chrome, bottom-content clearance helpers, and sleep-timer chrome.
- `PredictiveBackWrapper` peeks the NavHost (scale 1.0 → 0.9) during system Back. Progress always returns to rest after commit or cancel so a Back that replaces the start destination (cold-start Subscriptions → Home) does not leave Home scaled down.
- Shared discovery poster cards: `FeedMediaCard`, `CuratedEpisodeCard`, `EqualHeightPosterGrid`, and `FeedPosterSpacing` (Home “Based on Your Taste” and Explore For You).
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
- UI typeface: bundled **Google Sans Flex** variable font (`res/font/google_sans_flex_variable.ttf`). Default **ROND = 100 (Round)**; Appearance → Lettering can switch Crisp (0) / Soft (50) / Round (100) via `FontRoundness`, `LocalFontRoundness`, and `BoxLoreTheme(fontRoundness=…)`. `GoogleSansFlexTypeface` caches native faces by weight and axes (`ROND`, `opsz`, optional `wdth`); `Typography.kt` wraps each resolved face in a Compose `FontFamily` so Android skins cannot collapse Material typography weights. `GoogleSansWeight` centrally maps app emphasis to the lighter 400/400/500/600 scale. `rememberGoogleSansFamily()` is the shared helper for explicit weighted UI. Roboto Flex paths (`RobotoFlexFamily`, `LogoFontFamily`, `robotoflex_variable`) are unchanged. Shared connected chips: `ConnectedOptionSelector` (Appearance, Library history period/status filters). Content region uses `RegionSegmentedSelector` (sheet list of 11 storefronts) and `ContentRegionLanguagePicker` / `ContentLanguageChipRow` (suggested-for-country + more languages; English locked). SIL OFL 1.1 text: [`licenses/GoogleSansFlex-OFL.txt`](licenses/GoogleSansFlex-OFL.txt).
- Lettering roundness is mirrored in `boxlore_theme_fast_cache` key `font_roundness` (owned by `:core:prefs`) so share cards / Auto collage can read it without a Compose tree.
- `BoxLoreNavigationBar` owns the selectable navigation presentation: a solid Material 3 floating 3+1 shell (Home / Explore / Library pill with a spring-animated active indicator plus Lore action) or the classic four-tab bar. The floating Lore action gives its existing Neurology icon a short, clipped Gemini-inspired multicolor gradient flow once the app reports initial content ready, then uses a slow continuous aurora while Lore is active. `NavigationChromeMetrics` and `appBottomChromeContentPadding()` keep app and feature overlays clear of the selected chrome and its matching mini-player geometry.

## Testing notes

- Unit tests live under `core/designsystem/src/test`.
- `ThemeBrandTokensTest` covers brand seed and contrast helper behavior.
- `PredictiveBackPeekTest` covers rest progress after a predictive-back gesture (scale must return to 1).
- Screenshot goldens (optional local Roborazzi) live in feature modules (see `:feature:home`).

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
