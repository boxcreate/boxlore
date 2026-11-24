# `:core:designsystem`

## Purpose

Owns boxlore's shared Compose visuals: theme, type, shapes, motion, press feedback, window and pane layout, shared components, navigation chrome, artwork loading, Echo, share cards and the page-view helper. It owns no navigation graphs, repositories, network, Room or business workflows; callers pass data and callbacks in.

## Public API

### Theme and colour

- `BoxLoreTheme`: the app theme from the saved Appearance choices.
- `resolveBoxLoreColorScheme`, `resolveBoxLoreChromeColors`, `resolveFixedThemeColorScheme`: the same scheme for widgets and Appearance previews; `BoxLoreChromeColors` holds the roles widgets tint with.
- `ThemePresets`: the sixteen light and dark themes, keyed by stable `preset:*` keys.
- `CustomThemeSeeds`, `generatePersonalColorScheme`: custom accent seeds and their tonal scheme.
- `ClassicBrandColors`, `BrandSeeds`, `SurfaceStyles`: the boxlore classic palette, named seeds and surface-style keys, including Pure.
- `extractArtworkPalette`, `loadArtworkPalette`: the one cover-colour picker for show and episode pages, the player and Home heroes; colourless covers keep the listener's theme.
- `artworkColorScheme`, `artworkPlayerColorScheme`: page and player schemes from a cover's seed.
- `ArtworkShade`, `ArtworkDuotone`, `artworkDeepShade`: readable text colours, two-tone covers and deep grounds from a seed.
- `ArtworkNavigationColors`, `ArtworkNavigationTheme`: artwork colours per navigation entry, so a leaving page never recolours the active one.
- `LocalArtworkColorsEnabled`: the saved artwork-colour opt-out.

### Typography

- `buildBoxLoreTypography`, `GoogleSansFlexTypeface`, `GoogleSansWeight`, `rememberGoogleSansFamily`: Material typography on the bundled Google Sans Flex variable font.
- `FontRoundness`, `LocalFontRoundness`: the Appearance lettering choice on the font's roundness axis.
- `UnboundedFamily`, `SpaceMonoFamily`: display faces for surfaces outside app type, never regular UI text.

### Shapes, motion and press feedback

- `BoxLoreShapes`, `ExpressiveShapes`, `ExpressiveMotion`: shared shape and motion tokens.
- `Modifier.expressiveClickable`: press feedback for custom surfaces with their real `shape`. `ExpressivePressFeedback.Surface` (default) keeps cards and rows steady with a bounded ripple; small controls opt into `Control`.
- Never add it to native Material buttons or cards, which own their feedback; put custom control backgrounds inside the press layer.
- `Modifier.boxloreCombinedClickable`: hover, focus and secondary click for custom controls.
- `m3Shimmer`, `ShimmerScope`, `SkeletonBlock`, `SkeletonTextLines`: draw-only skeletons on one clock per scope; text lines match the loaded text's height.

### Layout and window classes

- `BoxloreWindowLayout`, `LocalBoxloreWindowLayout`: the app-supplied window and fold model; previews default to phones.
- `boxlorePaneSpec`, `LocalBoxlorePaneBounds`, `dockedPlayerCoversPane`: shell-supplied panes, never inferred from the window; half-open folds use the whole window until a pane opens.
- `rememberBoxloreFoldSafePopupPositionProvider`, `BoxloreTabletopSplit`, `boxloreIsShortPane`, `boxloreContentHeightDp`, `BoxloreSafeInsets.kt`: fold-safe popups, tabletop split, short-pane check and pane height, pane-relative insets.
- `BoxloreCardSpec`, `boxlorePosterColumns`, `Modifier.boxloreReadingColumn`: card families, poster packing by pane width, a reading column for any window.
- Gates and state retention: see [adaptive UI and state](../../ARCHITECTURE.md#adaptive-ui-and-state).

### Shared components

- Cards and panels use solid Material 3 surfaces and roles, never hard-coded colours.
- `OptimizedImage`: the artwork composable (`fallbackUrls`, `errorContent`, `showLoadingPlaceholder`, `onLoaded`).
- `FeedMediaCard`, `ExpressiveFeedMediaCard`, `CuratedEpisodeCard`, `EqualHeightPosterGrid`: discovery cards with an optional show name line; roomy tablet featured cards lead with a larger title. `FeedMediaCardSkeleton` matches the loaded card at any font scale.
- `TabletFeaturedRow`, `AdaptiveOverflowRow`, `rememberAdaptiveOverflowState`: a feature card beside equal-height posters; a width-packed chip row whose More list follows resizes.
- `DiscoveryTabSwitcher`: Explore and Subscriptions tab capsule; pages keep selection, long names grow the floating pill, and icons drop before names shorten.
- `TopBarUtilityActions`: connected Update, Feedback and Settings segments, named in tooltips. Update shows only with `LocalUpdateAvailableAction`, and its sweep runs three times, never with animations off; `LocalFeedbackReplyWaiting` dots Feedback.
- `BoxloreTooltip`: names an icon-only button on hover or long-press.
- `BoxloreTopBar`, `boxloreLargeTitlePresentation`: page header, slim in short panes.
- `ConnectedOptionSelector`, `RegionSegmentedSelector`, `ContentRegionPickerSheet`: option buttons (long names shrink, wrap, stack); region picker card or `compact` chip.
- `BoxloreFilterChip`, `BoxloreMoreChip`, `BoxloreFilterChipDefaults`: the one filter chip, with a sheet container; `PillFilterChip` is it for genre pills.
- `BoxloreDropdownMenu`, `rememberBoxloreMenuState`: grouped, fold-safe menus with selectable rows and destructive groups, saved across recreation.
- `BoxloreTextField`, `BoxloreSearchField`: the text field and search box; hints stay on one line.
- `ContentLanguagesSheet`: English can be switched off only when `ContentRegions.canSwitchEnglishOff` allows it.
- `BoxloreDialog`, `BoxloreModalBottomSheet`, `BoxloreAlertDialog`, `EngagementBottomSheetScaffold`: phone dialogs and sheets that become bounded, fold-safe dialogs on tablets; `BoxloreDialogConfirmButton` (error-coloured when destructive), `BoxloreDialogDismissButton` and `BoxloreDialogIcon` give dialogs one look.
- `BoxloreSnackbarHost`, `BoxloreScrollableCenteredColumn`: snackbars above bottom chrome; centred panels that scroll when short.
- `RemoveDownloadConfirmationDialog`, `PrivacyConsentDialog`, `RepairProgressPopup`: caller-shown confirmation, consent and repair status; `fromWatch` words the first for a copy on the watch.
- `AddToPlaylistSheet` (draft in `AddToPlaylistSheetState`), `PlaylistNameDialog`, `DeletePlaylistDialog`, `PlaylistCover`: stateless playlist UI; `PlaylistCopy` and `AddToPlaylistCopy` hold its resources.
- `SendToWatchIcon`, `WatchEpisodeActions`, `LocalWatchEpisodeActions`: Send to watch for one episode and its state there (`WatchEpisodeCopy`); a null local (no linked watch) hides the icon.
- `SleepTimerPopup`: the late-night sleep timer prompt, reporting `SleepTimerPopupDismissReason`; with `onPlayOnWatch` it also offers a just-paused watch back and waits longer.
- `PlayingOnPill`, `PlaybackPlaceNotice`, `LocalWatchPlayOffer`: where an episode plays while a boxlore watch is in reach, and the offer to hand playback to it; a null offer hides them.
- `BoxLoreLoader`, `DownloadButton`, `ExpressivePlayButton`, `AdvancedPlayerControls`, `NewEpisodeBadge`, `HtmlText`: loaders, buttons, player controls and rich text. `BoxLoreLoader.CircularWavy` in the content colour is the in-button loader.
- `GenreIcons`, `GenreSuggestions.kt`: icons and keyword suggestions for genres and folders.
- `LazyListKeyPolicy`, `ProgressiveSearchScrollLogic`: duplicate-safe lazy-list keys; when a progressive show search pins to the top.

### Navigation chrome

- `BoxLoreNavigationBar`, `NavigationStyle`: the floating 3+1 bar (Home, Explore, Library plus Lore) or the classic four-tab bar, each with one moving indicator.
- `BoxLoreNavigationRail`: the tablet rail, with the bar's destinations, selection and colours.
- `NavigationAccentColors`: selected tabs use `primary` and `onPrimary`; Lore keeps its aurora in the same indicator.
- `NavigationChromeMetrics`, `appBottomChromeContentPadding`, `floatingChromeSideOffsets`: chrome geometry; floating chrome avoids side insets.
- `LocalAdaptivePlayerCompactProgress`, `Modifier.adaptivePlayerOverlayOffset`: the app-owned player and bar morph progress.
- `PredictiveBackWrapper`: the NavHost peek during system Back, back at rest after commit or cancel.

### Artwork and image loading

- `ArtworkAttempts`, `ArtworkLoadPolicy`: optimized then original, one retry for a transient failure, and failures and working routes remembered for a while.
- Alternate artwork (`fallbackUrls`, `fallbackImageUrls`) is used only after a load fails. Failed tiles retry on foreground return or reconnection, never by polling.
- Missing art shows the decorative `ic_boxlore_app_mark` on a solid fallback; the card's description stays its accessible name.
- `optimizedImageUrl(width)` and `proxyWidth` take pixels, clamped in `ImageRequestSizing.kt`; one value sizes request and decode, in measured buckets on tablets and wide windows.
- `loadArtworkBitmap`: the bounded loader for widgets, share cards and notifications, on Coil's cache.
- `ArtworkPrefetch.prefetch` warms the exact request `OptimizedImage` makes on portrait phones; `OnScreenArtwork` lets colour extraction reuse it.

### Echo

- `EchoCharacter`: the boxlore mascot, playing a named sequence from `assets/echo-character/manifest.json`.
- Eyes are drawn in code; accents follow the theme unless a scene passes `EchoAccents`.
- With animations off, or `LocalEchoMotion provides false` in tests, Echo rests so screens can go idle.

### The boxlore brief

- `BriefingCover`, `BriefingEditorialTheme`, `BriefingRegionMenu`: the brief's cover, editorial colours and region menu.
- `briefingCoverResource`, `briefingPlayerResource`: weekday covers per region and the fixed player square, through `:core:model`'s `BriefingArtwork`.
- `briefingShowPodcast`: the brief's show identity, so player, notifications, history and Android Auto match the masthead.

### Share cards and links

- `ShareManager`: share cards and the system share sheet; reports `share_content` through `:core:analytics`.
- `ShareManager.episodeShareLink`, `copyEpisodeLink`: episode links from the app-installed `EpisodeShareTargetSource`, else the episode's own fields.
- `ShareBottomSheet`: the share sheet with a preview of its card, or a name row on short windows.
- `ShareCardRenderer`, `shareCardColors`: the card bitmap, a boxlore player in tones of the artwork's seed colour (boxlore indigo without one). `shareEpisodeDetails` and `sharePodcastDetails` give its details line: an episode's length and date, or a show's genre and rhythm.
- `startFirstAvailable`, `openLink`, `SafeUriHandler` (`links/`): open the first intent an installed app handles; never throw when none can.

### Analytics and text helpers

- `TrackScreenView`: the one page-view helper for screens, tabs, sheets and dialogs; it sends once per visit across rotation and resizing and registers with `ScreenViews` (`:core:analytics`).
- `UiText.asString()`, `currentLocale()`: resolve `UiText` and give `LocaleFormats` the screen's language ([translating](../../docs/i18n/TRANSLATING.md)); `contentLanguageName` and `contentRegionName` name languages and regions in it.

## Brand identity

- Use the bundled logo and wordmark vectors (`ic_boxlore_logo`, `ic_lore_brand`, `logo_lore`, `ic_boxlore_brief_logo`) through `BoxLoreLogo` and the shared components; never redraw or retype them.
- Write the product name as lowercase **boxlore**, including the brief's show name.
- The brand display font appears only in logo and wordmark artwork; all UI text uses Google Sans Flex.

### Upright brand wordmarks

- Wordmarks and the LORE mark are never slanted. Share cards use the plain wordmark with a "Listen on" label on the card's bar, clear of story controls.

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
  share/        ShareManager, ShareCardRenderer, ShareCardModel, ShareCardDetails
  text/         UiText.asString, currentLocale
  theme/        theme, presets, artwork colour, typography, shapes, motion, shimmer and skeletons, tablet input
src/main/res/
  drawable/, drawable-nodpi/   brand vectors, placeholder covers, brief artwork
  font/                        Google Sans Flex and Roboto Flex variable fonts
src/main/assets/echo-character/  manifest.json, stills (WebP), clips (MP4)
```

## Dependencies

- Project: `:core:model`, `:core:prefs` (lettering axis and font loading), `:core:analytics`.
- Libraries: Compose Material 3, Google Fonts, Material Color Utilities, Palette, graphics shapes, Coil (as `api`), smooth corner rect, coroutines, kotlinx.serialization JSON; tests add JUnit 5, Robolectric, Compose UI test and `:core:testing`.
- Must not depend on catalog, network, database, playback, downloads or any feature module. See [`ARCHITECTURE.md`](../../ARCHITECTURE.md#dependency-direction).

## Threading / lifecycle

- UI runs on the main thread; no app-scoped repositories live here.
- Coil loads images off the main thread, Palette runs on `Dispatchers.Default`, Echo clips decode on their own thread (`EchoClipFrames`) and the manifest loads on IO.
- `ShareManager` writes card files on IO and opens the share sheet on the main thread.
- Shimmer, navigation indicator and player-morph progress are read in draw or placement, so animation frames don't recompose screens.
- `BoxloreDialog` and the shared sheets keep their content state across window changes and folding.
- `SleepTimerPopup` saves its pick per showing, so its confirmation survives recreation without setting the timer twice.

## Persistence & identity

- No database, DataStore or SharedPreferences files are owned here.
- Theme values live in `:core:prefs` keys `theme_brand` and `surface_style`: `preset:*` keys, named seeds, `#RRGGBB`, `exact:#RRGGBB` and `custom:1:...`. Keep these formats readable.
- Lettering roundness is mirrored to `boxlore_theme_fast_cache` key `font_roundness` (owned by `:core:prefs`) for readers without Compose.
- Font files: `res/font/google_sans_flex_variable.ttf` (licence: [`licenses/GoogleSansFlex-OFL.txt`](licenses/GoogleSansFlex-OFL.txt)) and `res/font/robotoflex_variable.ttf`.
- Echo sequence IDs (`welcome`, `onboarding-*`, `account-*`, `import-*`, `story-*`, `survey-*`, `feedback-sent`) are called by name from `:app` and features; keep them stable.
- Artwork memory-cache keys take the form `<url>-w<pixels>`; prefetched and loaded images must match.
- Brief artwork resource names and the `briefing_{region}` show IDs are playback contracts for upgrades and restores.

## Testing notes

Compose tests in `src/test` use Robolectric; phone goldens are local-only ([`docs/TESTING.md`](../../docs/TESTING.md#screenshots)).

| Area | Test classes |
| :--- | :--- |
| Theme and presets | `ThemePresetsTest`, `ThemeBrandTokensTest`, `ClassicBrandColorsTest`, `PresetThemeColorsTest`, `PersonalColorSchemeTest` |
| Artwork colour | `ArtworkPaletteTest`, `ArtworkColorSchemeTest`, `ArtworkPlayerColorSchemeTest`, `ArtworkShadeTest`, `ArtworkDuotoneTest`, `ArtworkNavigationColorsTest`, `ArtworkColorMotionTest` |
| Typography | `GoogleSansFlexTypefaceTest`, `TypographyFlexFamilyTest` |
| Artwork loading, shimmer | `ArtworkLoadPolicyTest`, `ArtworkPrefetchTest`, `ImageRequestSizingTest`, `ShimmerClockTest`, `ShimmerColorLogicTest` |
| Layout and tablet input | `BoxloreWindowLayoutTest`, `BoxloreCardSpecTest`, `EqualHeightPosterGridTest`, `OverflowPackingTest`, `TabletSecondaryClickGestureTest`; `TabletModalTest`, `TabletInputTest` in `:app` |
| Navigation chrome | `FloatingNavigationChromeTest`, `NavigationIndicatorMotionTest`, `NavigationAccentColorsTest`, `NavigationChromePlacementTest`, `AdaptivePlayerOverlayOffsetTest`, `PredictiveBackPeekTest` |
| Press feedback, top bar, cards, tabs | `ExpressivePressFeedbackTest`, `TopBarUtilityActionsTest`, `HomeDiscoveryCardTest`, `DiscoveryTabSwitcherTest` in `:feature:home` |
| Shared controls, skeletons, card text | `BoxloreDropdownMenuTest`, `SharedControlsLongTextTest` |
| Sleep prompt, playing on a watch, languages | `SleepPromptMotionTest`, `SleepTimerPopupTest`, `PlaceMenuTest`, `RemoveDownloadCopyTest`, `ContentLanguagesSheetCopyTest` |
| Echo, brief | `EchoCharacterModelTest`, `EchoMotionModelTest`, `BriefingArtworkTest`; `BriefingShowTest` in `:feature:briefing` |
| Share, links, page views, text | `EpisodeShareLinkTest`, `ShareCardModelTest`, `ShareCardDetailsTest`, `ShareSheetBodyTest`, `SafeLinksTest`, `TrackScreenViewTest`, `UiTextComposeTest`; `ScreenViewCoverageTest` in `:core:testing` |
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
