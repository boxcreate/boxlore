# `:feature:settings`

## Purpose

Owns the unified Settings hub, category pages (Account, Sync & Backups, Appearance, Playback, Privacy, Library, Downloads, About, Support), dialogs (Add RSS, Reset analytics), authentication credential management (Google One Tap, Magic Link, Password), and download preference screens (`AutoDownloadSettingsScreen`, `SmartDownloadsSettingsScreen`). It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- Appearance → Player → **Player style** selects Standard (default) or **bunpod**. App wiring persists the choice through `:core:prefs`; settings never constructs a playback repository. The section links to BunPod and credits Kamran Bekirov for the design inspiration. Both layouts keep the same miniplayer and artwork-derived colors; video uses Standard.

- Settings cards, rows and expandable help text use the shared steady Material feedback rather than scaling their whole contents. Existing native controls and connected selectors retain their own feedback. The custom support CTA keeps its 3% response with a critically damped spring and draw-phase scale reads; avatar animations and settings behavior are unchanged.

- Settings and About expose **Check for updates** through an optional app-shell callback. The shell owns availability, the full-screen result, release notes and installation; Settings does not fetch releases or request APK installation itself.

- Custom theme creation is a nested full-page destination under Theme & colors. Back discards the draft and returns to Theme & colors; Save applies and remembers the custom palette without changing display mode. A compact light/dark preview stays visible while the controls scroll, and moves beside them in wide windows. Very short windows use a background/accent strip so color entry remains reachable with the keyboard or enlarged text. Primary/Secondary/Tertiary selectors wrap for larger text; each explains where its accent is used. Quick swatches and gradient hue/saturation sliders are immediately available. Brightness and labelled hex entry expand under **Fine-tune**. Invalid or incomplete hex input keeps the last valid palette visible and disables Save, including after saved-state restoration. Draft colors affect only the preview until Save; the editor chrome keeps the current app palette.
- The custom editor suppresses the press ripple throughout its controls while preserving selection colors, native shape responses, focus indicators and accessibility actions. The color-role selectors also clip to their own rounded shape. Other Settings pages retain normal Material feedback.

- Add RSS uses a single localizable instruction to paste the podcast’s RSS feed link and add it to the library. The feed-link publication warning appears only in Podcast Info when notifications are enabled. Subscription creation enables normal foreground refresh for all supported feeds, including those without HEAD validators.

- `SettingsScreen`, `SettingsViewModel`, `SettingsViewModelAssembler`, and `ProfileSettingsDestination` for the settings hub and sub-pages.
- Shared `SettingsScaffold` headers use a smaller 36sp expanded title and naturally wrap to two lines when needed, including Cloud Sync & Backups. The expanded title smoothly reduces to 22sp on scroll; the separate compact row always uses 22sp and retains two-line support for narrow screens and larger system fonts. Material's two-row app bar measures the title height, so a hidden expanded title cannot inflate the navigation row or force a single-line ellipsis.
- `SettingsScreenConfig` includes `isOnboarding: Boolean` (used by app navigation to keep onboarding mode active and bypass the hub on return), `onSendFeedback: (() -> Unit)?` to route to feedback, and `resolveSettingsBackAction` for deterministic back-handler actions.
- `FeedbackScreen`, `FeedbackViewModel`, `DiagnosticCollector`, and `LogcatCollector` under `feedback/` for sticky draft feedback submissions, in-process sanitized logcat extraction, device diagnostics, and GitHub issue reporting.
- `AutoDownloadSettingsScreen` and `SmartDownloadsSettingsScreen` under `downloads/` for download management.
- `AccountSettingsPage`, `SyncAndBackupsPage`, `AppearanceSettingsPage`, `PlaybackSettingsPage`, `PrivacySettingsPage`, `LibrarySettingsPage`, `DownloadsSettingsPage`, `AboutSettingsPage`, `SupportDevelopmentPage`.
- Sync & Backups export copy names only what the JSON backup writes: subscriptions, folders, history, likes and settings (`FULL_LIBRARY_EXPORT_SUMMARY`, covered by `SyncAndBackupsPageTest`). Settings copy spells the product name boxlore in lowercase, including sentence starts.
- Idle and successful cloud sync use a neutral solid panel with **Cloud sync** as its heading, one last-sync line after success, an automatic-sync explanation and a full-width tonal **Sync now** action. Idle has no redundant ready-status subtitle. Busy and retry presentations remain separate; large text can wrap without squeezing the action beside the status. Echo outfit taps have no rectangular ripple around the transparent artwork and retain their accessible action.
- Account uses a compact **Account & sync** page with an open identity header, one solid sync-status panel and separate account management. Onboarding uses **Connect your library**, an optional-account introduction and a focused signed-in confirmation with **Continue to boxlore**; it omits password reset, sign-out and deletion controls. Existing Google, email-link, password, verification and deletion flows are unchanged. Sync errors have a retry action and retain local library access; syncing is indeterminate and never treated as completed by an animation.
- `onAccountPageVisibilityChanged` reports the actual nested Account destination to the app shell, including Settings-hub and Sync & Backups entry. The shell hides player chrome throughout Account and onboarding without stopping audio or changing restored playback. Account content no longer reserves a mini-player-sized bottom gap; regular Settings retains navigation clearance.
- Echo replaces the Account blob avatar with bundled transparent artwork from the approved asset pack. Only the atlas, animation manifest, shared spin strip and timing contract ship (about 2.9 MB); no previews or duplicate WebP encodings. Atlas decoding runs off the main thread once per page instance. The 192:208 aspect ratio and exact frame durations are preserved. Tapping cycles the supplied outfits via one spin that coalesces rapid requests; actual sync temporarily overrides the outfit and restores it afterward. Animation pauses when clipped out of view or below STARTED, uses draw-phase frame reads, and renders a static pose when motion is disabled. Status text remains accessible independently of the decorative sync character.
- Appearance → Player → Miniplayer exposes **Seek buttons**, default off. `AppearanceUiState` and `AppearanceActions` carry its value and callback; app wiring owns persistence through `:core:prefs`. The existing Playback seek-duration sliders apply to the optional larger miniplayer buttons too.
- Appearance is an overview plus five pages (`ProfileSettingsDestination` `Theme`, `AppearanceHome`, `AppearancePlayer`, `AppearanceNavigation`, `AppearanceText`; each `parent` is Appearance, and Back returns there unless the page was opened directly). The overview shows **Display mode** (also at the top of Theme & colors), then one row per page with its current choice (`appearanceSummary`); hero card previews (`AppearanceHeroPreview`, supplied by the app from Home; without it the Home page shows the choices without previews) appear only on the Home page. **Theme & colors** holds Display mode, the four looks (boxlore classic, Pure, Material 3, Custom) as a two-column picture grid with light and dark previews, the custom theme editor, **Use artwork colors** and **Widgets**. **Home** holds a **Hero card preview** (headed, non-interactive), the hero cards, their colours (Cover's shade, Duotone's colours, or the card color of Tide and Halo) and the top bar shortcuts. **Player** holds the player style and the miniplayer's seek buttons. **Navigation** holds the bar style, its blur and **Start page & tabs** (start page, Explore and Subscriptions landing tabs, Subscriptions tab position). **Text** holds the letter shape and an always-visible preview. Every visual choice uses `SettingsPictureTile` (a picture, the name and a check; the tile is one radio button, picked tiles on the primary container with a primary outline) in `SettingsPictureGrid`, or for the hero cards `SettingsPictureRow`, a sideways-scrolling row whose tiles are sized to the row (`settingsPictureRowTileWidth`: two whole and about a third of the next, 132–200dp) so the cut-off tile shows there is more, and which brings the picked one into view; short lists keep connected buttons. Previews are drawn through `SettingsPicture`, which keeps their buttons from reacting while the tile still takes the tap and hides their contents from accessibility services. Material 3 uses wallpaper colors; Pure retains white/true-black backgrounds. The `theme` entry point opens Theme & colors. Legacy presets, named accents, custom hex colors and locked modes remain readable and appear as Custom without changing persisted values. Leaving Custom remembers all its original inputs atomically so it can be restored later.
- **Use artwork colors** defaults on and scopes artwork-derived palettes to podcast/episode details and the navbar’s accents and subtly tinted surfaces. Pure retains neutral black/white backgrounds. Palette changes ease in after artwork loads; turning the option off restores the app palette. The full-page custom editor keeps its Material component preview pinned, using a compact sample on short screens, with larger text or while the keyboard is open. It has Light/Dark selection, primary/secondary/tertiary pickers, optional suggested supporting colors, hex entry, labelled sliders, and Tinted/Neutral/Pure backgrounds. Existing preset backgrounds remain available as Saved while comparing other background styles. Color roles, swatches and background options have explicit selection semantics, controls retain 48dp touch targets, and decorative preview controls do not expose fake accessibility actions. Save applies coordinated Material Color Utilities roles in one transaction; Back leaves app colors untouched. Text and container colors are derived for readability. Widgets still resolve the same saved app scheme independently of artwork.
- **Blur behind the bar** (Appearance > Navigation, shown only with the Floating bar) picks Off / Soft / Medium / Strong, Soft by default. The page softens into a progressive blur as it scrolls under the floating bar and mini player; the bar itself stays solid. Changes are tracked as `navigation_blur_changed`.
- **Hero cards** (Appearance > Home) picks Block (the solid colour card), Cover (the cover fills the card and fades into a shade behind the text; selected until a listener picks another style), Tide (the whole cover above a wavy panel with a round Play button), Duotone (the cover redrawn in two tones of one colour, with the words straight on it) Halo (a cookie-shaped cover in a progress ring on a card tinted from it) or Marquee (a full-width cover under the top bar, with the next cards beside Play) from a sideways-scrolling row of picture tiles drawn from the real cards, above a larger preview of the picked style with its Jump back in card. With Cover, **Shade behind text** picks Dark, Artwork (a deep shade of each cover's colour) or App theme (a deep shade of the accent colour); with Duotone the same choice is **Duotone colors** (Dark redraws covers in black and white), with Tide and Halo **Card color**, and with Marquee **Cover colors**, where Dark is named **Full color** because Marquee keeps covers as they are (`heroColorsCopy`). They are hidden for Block, and the overview names the choice (for example "Duotone hero cards, artwork tones"). On tablets Home shows Cover instead of Marquee, so Marquee's tile says so ("A full-width cover on phones. Tablets show Cover cards instead.") and the larger preview shows Cover; `TabletSettingsCaptureTest` includes the Home page. Ordering, playback and details actions are the same. Changes are tracked as `hero_card_style_changed` and `hero_cover_shade_changed`, and each pick that changes the look also sends `home_hero_style_selected` with the full look: `trackedForAnalytics` reads the current `AppearanceUiState` at the moment of the pick (`rememberUpdatedState`), so a style pick carries the current colors and a color pick the current style. `AppearanceActionsTrackedTest` checks both callbacks are forwarded and that picks report the full look while re-picks report nothing; `AppearancePagesTest` covers the scrolling tiles, both colour pickers and the summaries; `SettingsPictureRowTest` checks the third tile is cut off on phone widths.

- Dialogs: `AddRssFeedDialog`, `ResetAnalyticsDialog`, `LogsPreviewDialog`.

- Automatic downloads uses the shared two-line settings header and solid grouped surfaces for Downloads, Storage, and While boxlore is closed. A connected 1/2/3/5 selector replaces scrolling quota chips; a short introduction explains per-show activation. **Backup background checks** is explicitly described as optional recovery when show notifications are off or do not arrive; notifications can already trigger downloads while closed. Its network/charging controls appear only when the off-by-default switch is enabled, retaining their preferences when hidden. Concise state-dependent copy explains foreground/push discovery or battery/data costs and Android delays; the header help dialog holds the full network, consent, discovery and retention explanation. Per-show auto-download never enables background checking.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/settings/
  SettingsScreen.kt
  SettingsBackNavigation.kt
  SettingsExternalActions.kt
  SettingsViewModel.kt
  SettingsViewModelAssembler.kt
  ProfileSettingsDestination.kt
  components/
    SettingsRows.kt
    SettingsScaffold.kt (shared column or lazy-list content)
    ThemeLookCard.kt
    InlineAccentPicker.kt
    AccentSwatchGrid.kt
  dialogs/
    AddRssFeedDialog.kt
    ResetAnalyticsDialog.kt
  downloads/
    AutoDownloadSettingsScreen.kt
    SmartDownloadsSettingsScreen.kt
  feedback/
    DiagnosticCollector.kt
    FeedbackDialogs.kt
    FeedbackScreen.kt
    FeedbackSuccessView.kt
    FeedbackViewModel.kt
    LogcatCollector.kt
  pages/
    AboutSettingsPage.kt
    AccountAuthHelpers.kt
    AccountAuthUiComponents.kt
    AccountSettingsDialogs.kt
    AccountSettingsPage.kt
    AccountPageContent.kt
    EchoAvatar.kt
    EchoAnimation.kt
    AccountSignedOutContent.kt
    AnimatedBlobAvatar.kt
    BlobAvatarAccessories.kt
    BlobAvatarCharacter.kt
    BlobAvatarEnvironments.kt
    BlobAvatarGeometry.kt
    BlobAvatarGenreMood.kt
    AppearanceSettingsPage.kt
    ThemeSettingsPage.kt
    AppearanceThemePage.kt, AppearanceHomePage.kt, AppearancePlayerPage.kt, AppearanceNavigationPage.kt, AppearanceTextPage.kt, AppearancePictures.kt
    CustomThemeEditor.kt
    CustomThemeEditorOptions.kt
    CustomThemeColorControls.kt
    CustomThemePreview.kt
    ThemeSettingsLogic.kt
    DownloadsSettingsPage.kt
    EmailVerificationPendingSection.kt
    LibrarySettingsPage.kt
    PlaybackSettingsPage.kt
    PrivacySettingsPage.kt
    SettingsHub.kt
    SupportAtmosphere.kt
    SupportCtaSection.kt
    SupportDevelopmentPage.kt
    SupportTierModels.kt
    SyncAndBackupsPage.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:network`, `:core:prefs`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, Kotlin serialization, AndroidX Credentials, Google ID, Roborazzi.
- Reverse-edge rule: feature modules must not depend on other feature modules.

## Threading / lifecycle

- ViewModels are scoped by app navigation or Activity owners.
- Repositories, ports, and preferences dependencies are application-scoped instances supplied by app wiring.
- Settings surfaces emit glossary analytics through `:core:analytics` (no PostHog direct in the feature).
- UI state is exposed through flows and collected by Compose on the main thread.
- Network and database operations run through injected suspend APIs.

## Persistence & identity

- **Library → LeaveMeAlone** (`ProfileSettingsDestination.Recommendations`, titled LeaveMeAlone, parent Library, deep link `recommendations`) holds every switchable section: On Home (The Brief, Picked for you, Because you like, Time-of-day picks) and On episode pages (Try these episodes). Library opens it from its own card below Discovery (subtext "Turn off recommendations across the app", also used on the Appearance → Home link), and Appearance → Home links to it with `onOpenRecommendationsFromHome`, which records Appearance → Home as the Back target. The page reads `RegionSettings.discoverySections` and saves through `trackedDiscoverySectionSetter`, which sends `recommendation_section_toggled` (source `settings`). In the tablet split it highlights Library and keeps its Back arrow; the rows hold no local state, so resizing shows the stored values. `AppearanceUiState.discoverySections` is read-only: Navigation hides **Explore opens to** while Picked for you is off, since Explore then has only Top. Covered by `AppearancePagesTest`, `AppearanceActionsTrackedTest`, `SettingsBackNavigationTest` and `TabletSettingsLayoutTest`.
- Settings read and write DataStore and `BoxcastPrefs` through `:core:prefs` APIs.
- Stable Compose test tags include `settings_add_rss_*`, `settings_downloads_smart`, `settings_downloads_auto`, `settings_reset_analytics_confirm`, and `settings_reset_analytics_cancel`.

## Testing notes

- Tablet top bars align with their bounded content column and keep a flat surface. Footer clearance follows actual mini-player visibility. Support content and RSS match confirmation remain scrollable in short viewports; phone spacing remains unchanged.

- Tablet settings use the shared Utility cap (720dp), including Smart Downloads, Feedback and Support outside the ordinary settings scaffold. Existing account/editor caps remain smaller or bound each editor column. Phone constraints are unchanged.

- `SettingsPhoneGoldenTest` captures the Settings hub at both portrait phone sizes and short landscape in light/dark. Local captures use `-PphoneGoldens=verify --tests '*PhoneGoldenTest'`; recording never overwrites existing phone baselines. The older dialog/download Roborazzi tests also live in this module. See [capture instructions](../../docs/screenshots/README.md).

- `PlayerStyleSectionTest` checks both selections, the exact design-credit link and large-text RTL accessibility. `AppearanceActionsTrackedTest` verifies that the player-style callback survives the analytics wrapper without toggling the miniplayer.

- Unit tests live under `feature/settings/src/test`.
- `AccountPagePresentationTest` covers the separate onboarding/settings layouts, existing sign-in methods, sync busy/retry states and callbacks, a full-width ready/success action at 200% text in RTL, and Echo press feedback. Optional local render exports use `BOXLORE_UI_TEST_ARTIFACTS`. `EchoAnimationTest` checks the bundled atlas against the manifest, each frame boundary, and coalesced outfit/sync transitions. App navigation tests cover hiding restored playback during Account and onboarding and restoring normal player presentation afterward.
- `AutoDownloadSettingsActionsTest` exercises the same action callbacks and background presentation used by the screen: all four episode limits, invalid-limit rejection, help opening/dismissal, both background-control visibility states, and retaining all network/charging combinations when checks are disabled and re-enabled. No device or screenshot automation is required.
- `SettingsHeaderTitleTest` covers reduced expanded typography, two-line support throughout collapse, constant compact-row size, monotonic expanded size/line-height changes and invalid scroll fractions without rendering or device automation.
- Existing coverage includes Settings ViewModel tests, Account auth helper validation, Appearance actions tracking, back navigation action resolution tests (`SettingsBackNavigationTest`), and Roborazzi golden captures for dialogs.
- `AppearanceActionsTrackedTest` also verifies the miniplayer seek callback is forwarded in both directions through the appearance action wrapper.
- `CustomThemeEditorTest` covers live preview updates, incomplete/invalid hex validation and restoration, suggested-color stability across light/dark previews, atomic color/background saving, Back cancellation, editor-open events, and independently scrollable controls in narrow/large-text RTL and landscape windows. Local preview images can be exported by setting `BOXLORE_UI_TEST_ARTIFACTS` to an existing directory; CI needs no image output or device.
- `ThemePresetResolutionTest` retains coverage for complete legacy palettes and app/widget/preview agreement. `ThemeSettingsLogicTest` covers the four-choice picker, legacy classification without resets, accurate locked modes, cohesive selection, remembering/restoring Custom, and harmless reselection. `AppearanceActionsTrackedTest` covers artwork-toggle and cohesive-theme callbacks. Back-navigation tests cover the old Theme entry point opening Appearance. Shared designsystem tests cover color encoding, contrast, artwork seed selection and entry-scoped navbar ownership; prefs tests cover cache restoration and atomic persistence. These are hermetic JVM checks without device automation.


```bash
./gradlew :feature:settings:testDebugUnitTest
./gradlew :feature:settings:recordRoborazziDebug   # optional
```

## CI relevance

- `unit-tests.yml` runs Settings JVM tests and includes the module in merged coverage verification (`kover`).

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:feature:home` README](../home/README.md)
- [`:feature:library` README](../library/README.md)
- [`:app` README](../../app/README.md)

Custom tablet avatar/stage controls show visible hover and keyboard focus while retaining syncing/disabled guards. Existing settings/account column limits and phone geometry are preserved.

Tablet Settings uses a persistent category/detail split at 672dp of actual available width. Detail pages retain their composition owner through window resize, use a fixed header and clip transitions within their pane. Lateral category selection clears nested Back history; root Back exits the split, while Account and every page with a `parent` (Appearance's pages, Theme & colors, the custom theme editor) keep a Back arrow and return to their parent, and the list highlights the top-level category (`settingsCategoryOf`; `TabletSettingsLayoutTest` covers both). Onboarding and Support remain full-page. Category lists reserve actual playback clearance; ordinary detail/Account width caps remain 720/600dp. Offline local captures cover category pages and standalone download/feedback screens in portrait/landscape and both themes.

Short folded alerts keep RSS fields/errors, analytics explanations and account-deletion information in a scrollable body while native action buttons remain visible. `TabletSettingsDialogTest` checks these actual dialogs at a synthetic tabletop fold with enlarged text, without performing account or network operations.
