# `:core:designsystem`

## Purpose

Owns boxlore's shared Compose visuals: theme and colour, typography, shapes, motion and press feedback, window and pane layout, shared components, navigation chrome, artwork loading, the Echo mascot, share cards and the page-view helper. It does not own navigation graphs, repositories, network clients, Room access or business workflows; callers pass data and callbacks in.

## Public API

### Theme and colour

- `BoxLoreTheme`: the app theme, applied by `:app` and feature roots from the saved Appearance choices.
- `resolveBoxLoreColorScheme`, `resolveBoxLoreChromeColors`, `resolveFixedThemeColorScheme`: the same scheme for widgets and Appearance previews. `BoxLoreChromeColors` carries the surface, primary, secondary-container and tertiary roles widgets tint with.
- `ThemePresets`: the sixteen authored light and dark themes, selected by stable `preset:*` keys.
- `CustomThemeSeeds`, `generatePersonalColorScheme`: custom accent seeds and the tonal scheme built from them.
- `ClassicBrandColors`, `BrandSeeds`, `SurfaceStyles`: the boxlore classic palette, named seeds and surface-style keys, including Pure.
- `extractArtworkPalette`, `loadArtworkPalette`: the one cover-colour picker for show and episode pages, the player and Home heroes. Covers without meaningful colour keep the listener's theme.
- `artworkColorScheme`, `artworkPlayerColorScheme`: page and player schemes built from a cover's seed.
- `ArtworkShade`, `ArtworkDuotone`, `artworkDeepShade`: readable text colours, two-tone covers and deep grounds from a seed.
- `ArtworkNavigationColors`, `ArtworkNavigationTheme`: artwork colours owned per navigation entry, so a leaving page never recolours the active one.
- `LocalArtworkColorsEnabled`: the saved artwork-colour opt-out.

### Typography

- `buildBoxLoreTypography`, `GoogleSansFlexTypeface`, `GoogleSansWeight`, `rememberGoogleSansFamily`: Material typography on the bundled Google Sans Flex variable font.
- `FontRoundness`, `LocalFontRoundness`: the Appearance lettering choice mapped to the font's roundness axis.
- `UnboundedFamily`, `SpaceMonoFamily`: display faces for surfaces that deliberately step outside app type; never for regular UI text.

### Shapes, motion and press feedback

- `BoxLoreShapes`, `ExpressiveShapes`, `ExpressiveMotion`: shared shape and motion tokens.
- `Modifier.expressiveClickable`: press feedback for custom surfaces. `ExpressivePressFeedback.Surface` (default) keeps cards and rows steady with a bounded ripple; small custom controls opt into `Control`. Pass the real `shape`.
- Never add `expressiveClickable` to native Material buttons or cards; they own their feedback. Put custom control backgrounds inside the press layer so the whole surface moves together.
- `Modifier.boxloreCombinedClickable`: tablet hover, focus and secondary-click handling for custom controls.
- `m3Shimmer`, `ShimmerScope`: draw-only skeleton shimmer; one scope shares a single clock across a feed.

### Layout and window classes

- `BoxloreWindowLayout`, `LocalBoxloreWindowLayout`: the app-supplied window and fold model; isolated previews default to phone presentation.
- `boxlorePaneSpec`, `LocalBoxlorePaneWidth`, `LocalBoxlorePaneBounds`: pane sizes the shell supplies; features never infer them from the full window.
- `BoxloreCardSpec`, `boxlorePosterColumns`: tablet card families and poster packing; phone layouts are unchanged.
- Gates and state retention: see [adaptive UI and state](../../ARCHITECTURE.md#adaptive-ui-and-state).

### Shared components

- Cards and panels use solid Material 3 surfaces and colour roles, never hard-coded colours, so every theme and Pure style applies.
- `OptimizedImage`: the artwork composable, with `fallbackUrls`, `errorContent`, `showLoadingPlaceholder` and `onLoaded` options.
- `FeedMediaCard`, `ExpressiveFeedMediaCard`, `CuratedEpisodeCard`, `EqualHeightPosterGrid`: discovery cards. `FeedMediaCardSkeleton` shares the loaded layout, so placeholders match cards at any font scale.
- `TabletFeaturedRow`, `AdaptiveOverflowRow`: a feature card beside equal-height posters, and a chip row that packs to the available width.
- `DiscoveryTabSwitcher`: the Explore and Subscriptions tab capsule; pages keep selection.
- `TopBarUtilityActions`: connected Update, Feedback and Settings segments. Update shows only when `LocalUpdateAvailableAction` is set; `LocalFeedbackReplyWaiting` marks Feedback with a dot. Owners keep placement and callbacks.
- `ConnectedOptionSelector`, `PillFilterChip`, `RegionSegmentedSelector`, `ContentRegionPickerSheet`: option chips, genre pills and the content-region picker.
- `ContentLanguagesSheet`: the content-languages sheet. English can be switched off only when `ContentRegions.canSwitchEnglishOff` allows it.
- `BoxloreDialog`, `BoxloreModalBottomSheet`, `BoxloreAlertDialogOverride`, `EngagementBottomSheetScaffold`: phone dialogs and sheets that become bounded, fold-safe dialogs on tablets.
- `RemoveDownloadConfirmationDialog`, `PrivacyConsentDialog`, `RepairProgressPopup`: shared confirmation, consent and repair-status UI; the caller owns visibility.
- `SleepTimerPopup`: the late-night sleep timer prompt; reports `SleepTimerPopupDismissReason`.
- `BoxLoreLoader`, `DownloadButton`, `ExpressivePlayButton`, `AdvancedPlayerControls`, `NewEpisodeBadge`, `HtmlText`: loaders, buttons, player controls and rich text.
- `GenreIcons`, `GenreSuggestions.kt`: icons and keyword suggestions for genre tags and folders.
- `LazyListKeyPolicy`, `ProgressiveSearchScrollLogic`: duplicate-safe lazy-list keys, and when a progressive show search pins to the top.

### Navigation chrome

- `BoxLoreNavigationBar`, `NavigationStyle`: the floating 3+1 bar (Home, Explore, Library plus Lore) or the classic four-tab bar, each with one moving selection indicator.
- `BoxLoreNavigationRail`: the tablet rail, reusing the bar's destinations, selection logic and colours.
- `NavigationAccentColors`: selected tabs use the theme's `primary` and `onPrimary` roles; Lore keeps its aurora inside the same indicator.
- `NavigationChromeMetrics`, `appBottomChromeContentPadding`: chrome and mini-player geometry so overlays clear the bar.
- `LocalAdaptivePlayerCompactProgress`, `Modifier.adaptivePlayerOverlayOffset`: the app-owned player and bar morph progress.
- `PredictiveBackWrapper`: the NavHost peek during system Back; always returns to rest after commit or cancel.

### Artwork and image loading

- `ArtworkAttempts`, `ArtworkLoadPolicy`: try optimized then original for each image, retry a transient failure once, and remember failures and working routes for a bounded time.
- Alternate artwork (`fallbackUrls`, `fallbackImageUrls`) is kept until a load actually fails, never chosen up front. Failed tiles retry only on foreground return or reconnection; there is no polling.
- Missing or failed art shows the decorative `ic_boxlore_app_mark` over a solid fallback; the card's description stays the accessible name.
- `optimizedImageUrl(width)` and `proxyWidth` take pixels, clamped in `ImageRequestSizing.kt`; one value sizes both request and decode. Tablets upgrade in measured buckets.
- `loadArtworkBitmap`: the bounded loader for widgets, share cards and notification artwork, sharing Coil's cache.
- `ArtworkPrefetch.prefetch` warms the exact request `OptimizedImage` makes on phones; `OnScreenArtwork` lets colour extraction reuse the copy on screen.

### Echo

- `EchoCharacter`: the boxlore mascot, playing a named sequence from `assets/echo-character/manifest.json`.
- Clips decode off the main thread (`EchoClipFrames`); eyes are drawn in code, and accents follow the theme unless a scene passes `EchoAccents`.
- With animations off, or `LocalEchoMotion provides false` in tests, Echo shows its rest pose so screens can go idle.

### The boxlore brief

- `BriefingCover`, `BriefingEditorialTheme`, `BriefingRegionMenu`: the brief's cover, editorial colours and region menu.
- `briefingCoverResource`, `briefingPlayerResource`: weekday covers per region and the fixed player square, through `:core:model`'s `BriefingArtwork`.
- `briefingShowPodcast`: the brief's show identity, so player, notifications, history and Android Auto match the masthead.

### Share cards and links

- `ShareManager`: share cards and the system share sheet; reports `share_content` through `:core:analytics`.
- `ShareManager.episodeShareLink`, `copyEpisodeLink`: episode links resolved through the app-installed `EpisodeShareTargetSource`, falling back to the episode's own fields.
- `ShareBottomSheet`: the share sheet, with a preview of the card it sends; short windows show a name row instead.
- `ShareCardRenderer`, `shareCardColors`: the card bitmap, drawn as a boxlore player in tones of the artwork's seed colour (boxlore indigo when the cover has none). The artwork is shown whole with rounded corners.
- `startFirstAvailable`, `openLink`, `SafeUriHandler` (`links/`): open the first intent an installed app handles; never throw when none can.

### Analytics helper

- `TrackScreenView`: the one page-view helper for screens, tabs, sheets and dialogs. It sends once per visit, survives rotation and resizing, and registers with `ScreenViews` from `:core:analytics`.

## Brand identity

- Use the bundled logo and wordmark vectors (`ic_boxlore_logo`, `ic_lore_brand`, `logo_lore`, `ic_boxlore_brief_logo`) through `BoxLoreLogo` and the shared components; never redraw or retype them.
- Write the product name as lowercase **boxlore**, including the brief's show name.
- The brand display font appears only in logo and wordmark artwork; all UI text uses Google Sans Flex.

### Upright brand wordmarks

- Brand wordmarks and the LORE mark are upright; never slant or italicise them.
- Share cards use the plain upright wordmark with a "Listen on" label on the card's bar, clear of story controls; artwork and title stay the focus.
- This README is the one place for these rules.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/designsystem/
  analytics/    TrackScreenView
  briefing/     brief cover, artwork, editorial theme, region menu, show identity
  component/    navigation bar and rail, indicator motion, player overlay offset, predictive back
  components/   OptimizedImage and artwork loading, cards, dialogs and sheets, selectors, sleep prompt
  echo/         EchoCharacter, manifest model, clip decoding, motion, accent tint
  icon/         genre icons and suggestions
  layout/       window, fold, pane and card specifications
  links/        safe link and intent launching
  list/         lazy-list keys, progressive search scrolling
  share/        ShareManager, ShareCardRenderer, ShareCardModel
  theme/        theme, presets, artwork colour, typography, shapes, motion, shimmer, tablet input
src/main/res/
  drawable/, drawable-nodpi/   brand vectors, placeholder covers, brief artwork
  font/                        Google Sans Flex and Roboto Flex variable fonts
src/main/assets/echo-character/  manifest.json, stills (WebP), clips (MP4)
```

## Dependencies

- Project: `:core:model`, `:core:prefs` (lettering axis and font loading), `:core:analytics`.
- Libraries: Compose Material 3, Google Fonts, Material Color Utilities, Palette, graphics shapes, Coil (exposed as `api`), smooth corner rect, coroutines, kotlinx.serialization JSON. Tests add JUnit 5, Robolectric and Compose UI test.
- Must not depend on catalog, network, database, playback, downloads or any feature module. See [`ARCHITECTURE.md`](../../ARCHITECTURE.md#dependency-direction).

## Threading / lifecycle

- Compose UI runs on the main thread. This module owns no application-scoped repositories.
- Coil loads images in the background. Palette extraction runs on `Dispatchers.Default`; Echo clips decode on their own thread and the manifest is read on `Dispatchers.IO`.
- `ShareManager` writes card files on IO and opens the share sheet on the main thread.
- Shimmer, navigation indicator and player-morph progress are read in draw or placement, so animation frames don't recompose screens.
- `BoxloreDialog` and the shared sheets keep one host and their content state across window changes and folding.
- `SleepTimerPopup` saves its pick per showing, so its confirmation survives recreation without setting the timer twice.

## Persistence & identity

- No database, DataStore or SharedPreferences files are owned here.
- Theme values live in `:core:prefs` keys `theme_brand` and `surface_style`: `preset:*` keys, named seeds, `#RRGGBB`, `exact:#RRGGBB` and `custom:1:...`. Keep these formats readable.
- Lettering roundness is mirrored to `boxlore_theme_fast_cache` key `font_roundness` (owned by `:core:prefs`) for readers without Compose.
- Font files: `res/font/google_sans_flex_variable.ttf` (licence: [`licenses/GoogleSansFlex-OFL.txt`](licenses/GoogleSansFlex-OFL.txt)) and `res/font/robotoflex_variable.ttf`.
- Echo sequence IDs (`welcome`, `onboarding-*`, `account-*`, `import-*`, `story-*`, `survey-*`, `feedback-sent`) are called by name from `:app` and several features; keep them stable.
- Artwork memory-cache keys take the form `<url>-w<pixels>`; prefetched and loaded images must keep matching.
- Brief artwork resource names and the `briefing_{region}` show IDs are playback contracts kept for upgrades and restores.

## Testing notes

Tests live in `src/test`; Compose tests use Robolectric. Some shared components are tested in their main consumer, as noted. Phone goldens are local-only; see [`docs/TESTING.md`](../../docs/TESTING.md#screenshots).

| Area | Test classes |
| :--- | :--- |
| Theme and presets | `ThemePresetsTest`, `ThemeBrandTokensTest`, `ClassicBrandColorsTest`, `PresetThemeColorsTest`, `PersonalColorSchemeTest` |
| Artwork colour | `ArtworkPaletteTest`, `ArtworkColorSchemeTest`, `ArtworkPlayerColorSchemeTest`, `ArtworkShadeTest`, `ArtworkDuotoneTest`, `ArtworkNavigationColorsTest`, `ArtworkColorMotionTest` |
| Typography | `GoogleSansFlexTypefaceTest`, `TypographyFlexFamilyTest` |
| Artwork loading, shimmer | `ArtworkLoadPolicyTest`, `ArtworkPrefetchTest`, `ImageRequestSizingTest`, `ShimmerClockTest`, `ShimmerColorLogicTest` |
| Layout and tablet input | `BoxloreWindowLayoutTest`, `BoxloreCardSpecTest`, `EqualHeightPosterGridTest`, `OverflowPackingTest`, `TabletSecondaryClickGestureTest`; `TabletModalTest`, `TabletInputTest` in `:app` |
| Navigation chrome | `FloatingNavigationChromeTest`, `NavigationIndicatorMotionTest`, `NavigationAccentColorsTest`, `NavigationChromePlacementTest`, `AdaptivePlayerOverlayOffsetTest`, `PredictiveBackPeekTest` |
| Press feedback, top bar, cards, tabs | `ExpressivePressFeedbackTest`, `TopBarUtilityActionsTest`, `HomeDiscoveryCardTest`, `DiscoveryTabSwitcherTest` in `:feature:home` |
| Sleep prompt, languages | `SleepPromptMotionTest`, `SleepTimerPopupTest`, `ContentLanguagesSheetCopyTest` |
| Echo, brief | `EchoCharacterModelTest`, `EchoMotionModelTest`, `BriefingArtworkTest`; `BriefingShowTest` in `:feature:briefing` |
| Share, links, page views | `EpisodeShareLinkTest`, `ShareCardModelTest`, `ShareSheetBodyTest`, `SafeLinksTest`, `TrackScreenViewTest`; `ScreenViewCoverageTest` in `:core:testing` |
| Genres and lists | `GenreIconsTest`, `GenreSuggestionsTest`, `LazyListKeyPolicyTest`, `ProgressiveSearchScrollLogicTest` |

```bash
./gradlew :core:designsystem:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs this module's JVM tests with the project suite; phone goldens stay out of it.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:app` README](../../app/README.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:core:analytics` README](../analytics/README.md)
