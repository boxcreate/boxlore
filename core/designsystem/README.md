# `:core:designsystem`

## Purpose

Owns shared Compose visual primitives: theme, typography, shapes, motion, loaders, image helpers, navigation chrome constants, and share-card UI. It does not own feature navigation, repositories, network clients, Room access, or business workflows.

## Public API

- `LocalUpdateAvailableAction` carries an optional app-shell UI action. `TopBarUtilityActions` connects icon-only Update, Feedback and Settings action segments using Material 3 connected spacing and leading/middle/trailing corners. The optional Update segment uses primary-container/on-primary-container roles and a shimmering system-update symbol; it has no nested circle and appears only when the action is supplied. Feedback/Settings click and long-press callbacks, toolbar height and shortcut placement remain unchanged. Its accessible label is **Update available**; the design system owns no update checking or installation.

- `ArtworkNavigationColors.appThemePreviewEntryId` tracks a temporary regular-page preview separately from committed entry ownership. Preview changes do not mutate saved artwork palettes; navigation commits and cancellation clear the preview.

- `BoxLoreTheme` and theme helpers such as expressive shapes, motion, typography, and dynamic color utilities. `resolveBoxLoreColorScheme` / `resolveBoxLoreChromeColors` / `darkThemeFromConfig` expose the same Appearance ColorScheme as ARGB for non-Compose chrome (home-screen widgets). Custom `theme_brand` may be a named seed, `#RRGGBB` (Material 3 palette from that seed), or `exact:#RRGGBB` (pins primary to that RGB; not recommended for readability). `Modifier.expressiveClickable` has a long-press overload (`onLongClick`) used by Downloads multi-select. Pass `shape` so press-shrink stays rounded; `pressScaleEnabled = false` while a drag overlay owns scale.
- `CustomThemeSeeds` encodes primary and optional secondary/tertiary seeds as versioned `custom:1:...` values in the existing theme-brand key. `generatePersonalColorScheme` uses Material Color Utilities HCT/tonal palettes and coordinated color roles for both modes; existing authored/hex/preset resolution remains unchanged. `artworkSurfaceStyle` retains Pure surfaces while allowing artwork-tinted detail pages; `selectArtworkSeed` ignores monochrome margins and negligible details. `LocalArtworkColorsEnabled` carries the saved opt-out. `ArtworkNavigationColors` captures the visible source palette synchronously at navigation, retains up to 80 entry snapshots and starts forward and Back handoffs with the departing colors, and invalidates them when the saved theme or artwork opt-out changes; `rememberAnimatedArtworkColors` eases all roles on one 750ms clock and retargets from the visible palette. `LocalArtworkLoaderColors` lets shared loaders inherit the source without recoloring regular-page content; detail pages override it with their synchronized page palette. `ArtworkNavigationTheme` inherits the page’s exact animated accents and restrained neutral surfaces, including Pure overrides; playback chrome remains independent.
- Shared components including `OptimizedImage` (optional `errorContent` for blank/failed art), loaders, `PillFilterChip` (onboarding/Explore genre pills), `BoxLoreLogo` (optional `height` for hero vs chrome sizes), `RemoveDownloadConfirmationDialog` (Material 3 confirmation dialog before removing downloaded media), player-control primitives used by UI modules, floating 3+1 navigation chrome, bottom-content clearance helpers, sleep-timer chrome, and the non-dismissible `RepairProgressPopup` for app-shell foreground repair status.
- `TopBarUtilityActions` groups Feedback and Settings as 33dp-tall solid Material 3 connected action segments, adding Update when available. Settings uses the sliders (`Tune`) icon. Feedback/Settings use 22dp icons in 48dp-wide segments with press feedback; the update symbol is 24dp. These remain action buttons rather than mutually exclusive selection controls. Foundation expands touch bounds vertically beyond the compact visual height. Screen owners retain placement, click/long-press callbacks and shortcut visibility; this component owns no scroll behavior.
- `LocalAdaptivePlayerCompactProgress` carries the app-owned player/nav morph clock. `Modifier.adaptivePlayerOverlayOffset` reads it only during placement, moving floating feature controls down by the released player row during the shared `adaptivePlayerDockingProgress` phase. Classic and absent playback retain their placement; list/content clearance remains stable.
- Regular navigation tabs in both presentations use the current theme's `primary` accent and `onPrimary` icon color through `NavigationAccentColors`, including authored presets, wallpaper palettes and exact custom accents. Classic selected labels use the same accent. Floating Lore retains its multicolour aurora and container base, blended into the single moving indicator during draw rather than adding another selection surface. Its selected icon uses `onPrimary` before the aurora arrives, `onPrimaryContainer` at rest, and the more readable of those foregrounds during the solid-base blend; foreground selection follows the same indicator position without a second delayed color animation. Unselected icons retain `onSurfaceVariant`.
- `icon.GenreIcons`: Rich icon palette and resolution helpers for custom podcast genre tags, subscription folders, and filter chips. Maps icon keys to Material Rounded icons (`all`, `findIcon`, `defaultGenreIcon`, `iconOrFallback`, `defaultFolderIcon`, `folderIconOrFallback`).
- `icon.GenreSuggestions`: Real-time keyword-driven suggestions and fuzzy ranking helpers (`GenreSuggestion`, `ALL_GENRE_SUGGESTIONS`, `filterGenreSuggestions`, `findSuggestedIcons`, `buildFolderSuggestionsWithLibrary`, `buildGenreSuggestionsWithFolders`) for genre tagging and folder creation with library prioritization.
- `icon.GenreExactMatchResolver`: Exact case-insensitive matching (`findExactGenreIconKey`) that auto-switches folder icons in real time as the user types matching genre names or topic keywords without waiting for manual chip or grid taps.
- `PredictiveBackWrapper` peeks the NavHost (scale 1.0 → 0.9) during system Back. Its optional `backgroundColor` lets the shell keep the visible artwork palette during the peek without changing the saved app theme. Progress always returns to rest after commit or cancel so a Back that replaces the start destination (cold-start Subscriptions → Home) does not leave Home scaled down.
- Shared discovery cards: `FeedMediaCard`, `CuratedEpisodeCard`, `EqualHeightPosterGrid`, and `FeedPosterSpacing`. `FeedMediaCardPresentation.Default` retains the existing outlined presentation. Explicit `ExpressivePoster` and `ExpressiveFeatured` variants use native clickable cards with a three-line title-only foot that vertically centers shorter titles and grows with font scale. Posters use solid surface-container cards with 24dp corners, 14sp/20sp text, and restored 16dp lower artwork corners. Featured cards use a title-first split composition on solid tertiary-container: a 16sp/24sp three-line title and standalone 24dp forward arrow at the start, with artwork occupying 44% of the width (capped at 176dp) and running flush to the end edge. A sweeping 72dp artwork corner and asymmetric 28dp/12dp outer corners define the silhouette; the composition mirrors in RTL. The arrow belongs to the same native card action, 12dp below the reserved title area. Featured artwork omits promotional overlays. Their normal-text height is 152dp; enlarged text grows the layout. `FeedMediaCardSkeleton` shares the loaded layout and a measured three-line paragraph, covering nonlinear font scaling and keeping placeholders equal to loaded cards. `DiscoveryExpressiveTheme` scopes expressive Material motion while inheriting the existing color scheme, typography, shapes and appearance preferences; it does not change the application theme.
- `ProgressiveSearchScrollLogic` decides when a progressive Find-a-show list should pin to the top (query change, new top hit, or Matches header). Used by onboarding search and Explore Find-a-show; Ask anything does not use it.
- `LazyListKeyPolicy` provides safe deduplication and collision-free key generation for LazyColumn, LazyRow, and LazyGrid lists, preventing `IllegalArgumentException: Key was already used` crashes from duplicate backend feeds or search hits.
- `share.ShareManager` for composite share cards and the system share sheet; emits glossary `share_content` via `:core:analytics`. `ShareBottomSheet` presents a clear content preview, a switch-style timestamp option, a primary artwork share action, and separate link/story actions.
- `share.ShareCardRenderer` builds the share-card bitmaps used by `ShareManager`. Stories and square artwork separate episode/show details from a compact “Listen on” label and upright boxlore wordmark.

## Brand identity

- boxlore classic uses the authored iris, slate and seafoam light/dark palette. A rich iris primary in light mode and luminous iris in dark mode lead the quieter slate and seafoam supporting accents. Wallpaper colours and custom accents retain their selection, with the updated classic neutral surfaces.
- The top-bar wordmark uses the full-size plain B vector for readability at chrome sizes. LORE page/navigation marks and the boxlore brief serif masthead are tintable vectors from the new brand kit; UI typography remains Google Sans Flex. Share artwork follows the wordmark’s updated aspect ratio.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/designsystem/
  component/
  components/
  list/
  share/
  theme/
src/main/res/
  drawable/
  font/
```

## Dependencies

- Project dependencies: `:core:model`, `:core:analytics`, `:core:prefs` (lettering axis + shared Google Sans Flex `Typeface` loader).
- Libraries: Compose Material, MaterialKolor’s public, platform-independent Material Color Utilities JVM port (keeps the current Android SDK requirement), Material icons, graphics shapes, Coil, AndroidX activity/core, smooth corner rect, and coroutines.
- Reverse-edge rule: designsystem must not depend on catalog, network, database, playback, downloads, or feature modules.

## Threading / lifecycle

- Compose UI work runs on the main thread.
- Coil performs image loading on background dispatchers. `OptimizedImage.proxyWidth` and `optimizedImageUrl(width)` are pixels (clamped to 10–2048); proxy/CDN dimensions and Coil decode dimensions use the same value without an extra density/quality multiplier. `OptimizedImage` keeps its solid fallback underneath the painter through the entire success crossfade; Coil success does not remove that backing before artwork becomes opaque. Error content still draws above it.
- `ShimmerScope` shares one linear, draw-only loading clock across a feed and stops it when loading finishes. Visible skeletons animate even while their navigation entry is still CREATED; the Compose window lifecycle pauses frames in the background. The published phase keeps a stable State reference across loading transitions so cached lazy-item draw modifiers resume the sweep instead of retaining the stopped value. The horizontal shader moves over a stationary full-block rectangle, with alpha-correct highlights and matching transparent edge colors. Its 1600ms two-width travel starts a quarter-cycle into the sweep so the first loading frame already has a highlight, and keeps the sweep visible through the cycle without a trailing idle interval or a hard rectangle edge through tall placeholders.
- ColorScheme resolution is remembered for theme inputs and resource configuration changes.
- `ThemePresets` defines sixteen complete themes: the original ten Colorful palettes, Minimal Paper/Graphite, and Bold Voltage/Arcade/Solar/Cobalt, each with its own light/dark background, tonal surface hierarchy and authored primary/secondary/tertiary accents. Collection metadata is presentation-only; every palette is checked for readable text across accent and surface roles in both modes. Stable `preset:*` keys use existing `surface_style` and `theme_brand` preferences. `resolveFixedThemeColorScheme` shares resolution with Appearance previews and app/widgets. Theme mode remains independent. Personalising an accent or enabling wallpaper colors retains the chosen preset background; choosing another background retains the existing background-selection behavior. Existing seed/custom/exact colors and legacy mode locks remain supported.
- The theme is applied by `:app` and feature composition roots; this module owns no application-scoped repositories.

## Persistence & identity

- No user data, database files, DataStore names, or SharedPreferences files are owned here.
- Resource names are app-internal UI contracts and should be changed with normal Android resource migration care.
- UI typeface: bundled **Google Sans Flex** variable font (`res/font/google_sans_flex_variable.ttf`). Default **ROND = 100 (Round)**; Appearance → Lettering can switch Crisp (0) / Soft (50) / Round (100) via `FontRoundness`, `LocalFontRoundness`, and `BoxLoreTheme(fontRoundness=…)`. `GoogleSansFlexTypeface` caches native faces by weight and axes (`ROND`, `opsz`, optional `wdth`); `Typography.kt` wraps each resolved face in a Compose `FontFamily` so Android skins cannot collapse Material typography weights. `GoogleSansWeight` centrally maps app emphasis to the lighter 400/400/500/600 scale. `rememberGoogleSansFamily()` is the shared helper for explicit weighted UI. Roboto Flex paths (`RobotoFlexFamily`, `LogoFontFamily`, `robotoflex_variable`) are unchanged. Shared connected chips: `ConnectedOptionSelector` (Appearance, Library history period/status filters; optional `contentPadding` / `labelStyle` for dense 3-up labels). Content region uses `RegionSegmentedSelector` (sheet list of 11 storefronts) and `ContentRegionLanguagePicker` / `ContentLanguageChipRow` (suggested-for-country + more languages; English locked). SIL OFL 1.1 text: [`licenses/GoogleSansFlex-OFL.txt`](licenses/GoogleSansFlex-OFL.txt).
- Lettering roundness is mirrored in `boxlore_theme_fast_cache` key `font_roundness` (owned by `:core:prefs`) so share cards / Auto collage can read it without a Compose tree.
- `BoxLoreNavigationBar` owns the selectable navigation presentation: a solid Material 3 floating 3+1 shell (Home / Explore / Library pill plus Lore action, with one persistent active indicator tracking all four tabs) or the classic four-tab bar. Its optional `compactProgress: State<Float>?` is supplied by the app alongside the mini-player's shared animation: Floating eases labels into icons and moves the same Lore action into a fourth pill slot during contraction, reserving its former 52dp trailing slot before playback docks. A single selection surface sits above the neutral pill/Lore backdrops and below their actions. It travels directly between all four targets in 240ms and morphs from the external Lore circle into the compact fourth-slot capsule. Retargeting starts at its current position and heads toward the latest tab without retaining travel in the previous direction; hiding it never retargets it to Home. Floating actions use ripple-free selectable semantics and subtle icon/label press scaling, so touch feedback cannot paint a second highlight. Solid high-container surfaces, a fine outline, restrained elevation, crossfading filled/outlined icons and press scaling keep both states consistent. Pill width, 16dp edge insets, and `AppFloatingNavigationActionGap` (14dp) stay fixed; the compact Lore target is 48dp. Placement follows layout direction, and selection semantics / spoken labels follow each persistent action. Progress is clamped and read during measurement, placement and graphics layers; neutral Lore elevation follows the shared contraction without per-frame composition. Classic ignores compact progress. Lore uses a subtle icon settling animation after initial content is ready. Lore restores the original blue/pale-blue/violet/red/gold/green multicolour aurora inside that same selection surface, with its original restrained 0.44 colour opacity and a softer broad highlight. Colour strength follows the actual indicator position with an eased blend, including interrupted tab changes, so no separate Lore highlight is painted. Cached diagonal/radial brushes and draw-only phase reads keep its 20-second round trip out of per-frame composition; the original sweep eases smoothly through both turnarounds, pauses while Lore is unselected and resumes from its retained position, and the parent selection shape clips both the external circle and compact capsule. `NavigationChromeMetrics` and `appBottomChromeContentPadding()` keep app and feature overlays clear of the selected chrome and its matching mini-player geometry.

## Testing notes

`NavigationChromeMetrics` gives the Floating mini player a full capsule: 32dp corners at 64dp height. Classic retains 26dp top / 14dp bottom corners and 72dp height. Player surfaces use the same selected corner metrics; bottom-content clearances do not change.

Classic navigation applies its system insets outside the minimum 80dp content height and disables the nested Material bar's inset padding, so its body and Android button area are counted once each. `appMiniPlayerTopOffset` includes the bottom system inset for both presentations even when Classic owns inset padding internally. Edge-to-edge feature overlays can pass `systemNavigationInset` to `appBottomChromeContentPadding`; callers inside already-inset Scaffold content retain the default zero additional inset.

- Unit tests live under `core/designsystem/src/test`.
- Lore foreground regression tests cover the primary/container endpoints, invalid indicator positions, and at least 3:1 contrast against the blended solid base at 101 transition positions for every authored preset in both modes.
- `ImageRequestSizingTest` covers pixel targets, bounds, CDN/proxy agreement and local URL bypass. `ShimmerColorLogicTest` covers translucent highlight compositing and seam-free sweep edges. Home JVM native-graphics tests cover the shared artwork backing through a transparent success painter and visible shimmer movement during short loading sessions.
- `ArtworkNavigationColorsTest` covers source capture before disposal, Back inheritance and handoff generations, late outgoing updates, rapid navigation, cold start, opt-out/theme changes, bounded snapshots and Pure/mode compatibility.
- `ArtworkColorMotionTest` covers interrupted handoffs, bounded progress, intermediate light/dark text contrast, navbar surface agreement and neutral Pure backgrounds throughout the transition.
- `ThemeBrandTokensTest` covers brand seed, contrast helper, and scheme-resolution behavior.
- `ThemePresetsTest` checks distinct/stable preset keys, shared seed resolution, both-mode accent/container text contrast (at least 4.5:1), inverse/tint roles and preservation of user-selected surfaces and error colors. Settings JVM integration tests exercise actual app/widget resolution across all background styles.
- `ProgressiveSearchScrollLogicTest` covers pin-to-top when catalog hits prepend over local matches.
- `LazyListKeyPolicyTest` covers safe deduplication, key prefixing, blank ID fallbacks, and duplicate collision disambiguation.
- `GenreIconsTest` and `GenreSuggestionsTest` cover icon lookup, keyword scoring, library priority ranking, and suggestion composition.
- `PredictiveBackPeekTest` covers rest progress after a predictive-back gesture (scale must return to 1).
- `FloatingNavigationChromeTest` covers presentation clearance, root/query selection, fixed floating pill / player-slot geometry, Lore's fourth-slot placement, non-overlapping 48dp targets throughout the morph at supported widths, and safe progress clamping.
- `NavigationAccentColorsTest` covers every preset's authored primary in both modes, exact accent overrides with different container colors, and wallpaper-style palettes with unrelated secondary roles. `NavigationChromePlacementTest` covers Classic's 48dp button area, gesture/zero/larger insets, both styles at multiple densities, player/selector gaps, unchanged already-inset content padding, and constrained windows.
- `AdaptivePlayerOverlayOffsetTest` covers selector/Play All clearance, eased contraction-before-descent timing with zero slope at phase boundaries, reverse motion, Classic/no-player behavior, and invalid progress. `NavigationIndicatorMotionTest` covers the production indicator route/target logic and motion spec: direct Lore-to-Library movement, retained positions while hidden, immediate direction changes from the current position when interrupted, and position-linked Lore aurora blending with bounded/invalid inputs. `FloatingNavigationChromeTest` checks the single indicator path, external/compact Lore geometry, and matching mirrored bounds throughout the player morph. These are JVM logic checks without screenshots or device automation.
- Home JVM `HomeDiscoveryCardTest` checks the expressive card/skeleton geometry at phone widths, enlarged fonts and RTL, centered short titles, three-line ellipsis, curved artwork, compact/regular rail sizing, legacy default sizing, and actual light/dark/dynamic/pure-black surface rendering. `HomeDiscoveryInteractionTest` checks title-only episode semantics and independent native click targets. No device automation or saved screenshots are required.
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

- The shared top-bar wordmark uses the plain B without a microphone, retaining its dimensions and theme tint.


### Upright brand wordmarks

- Extended brand wordmarks and the LORE navigation brain are upright. Share cards use the plain upright wordmark at 280px wide. Stories stack “Listen on” above it in a centred signature below the content, clear of bottom story controls. Square message cards place the label and wordmark in one centred footer row with a 96px bottom inset. Labels retain a 12px size when the full export is displayed at 360px wide; artwork and title remain the main focus.

The toolbar action segments use a compact 43 × 33 dp visual size. The available-update segment has a restrained shimmer across its surface.
