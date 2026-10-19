# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **AI-generated summaries:** May contain mistakes. Verify details against the linked pull requests.

## [Unreleased]

### Added
- `EpisodeRecipeId` in `:core:rss` and `scripts/sync/lib/episode-recipe-id.js` compute boxlore episode IDs from (show ID, catalog key), checked against a shared fixture. ([#1139](https://github.com/boxcreate/boxlore/pull/1139)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->
- The catalog sync logs why each vector point is stored or deleted; `scripts/sync/attribution-report.js` summarizes the log. ([#1139](https://github.com/boxcreate/boxlore/pull/1139)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->
- `turso.text()` in the sync's Turso client binds catalog keys as text. ([#1141](https://github.com/boxcreate/boxlore/pull/1141)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->
### Changed
- `StickyEpisodeIdentity.catalogKey` trims with an explicit character set equal to Kotlin's `trim()` on Android, so catalog keys no longer depend on the runtime's Unicode tables. ([#1139](https://github.com/boxcreate/boxlore/pull/1139)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->
- New chart shows get their newest 5 episodes embedded first and the rest of their window after 7 days on the charts (`NEW_ENTRANT_EPISODES`, `NEW_ENTRANT_DEPTH_DAYS`). ([#1141](https://github.com/boxcreate/boxlore/pull/1141)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->
### Fixed
- RSS notification cleanup removes only the RTDB `feedUrl` child instead of the whole `tracked_podcasts` row, which the rules refuse. A refused delete for a row that does not exist still unsubscribes the FCM topic and clears the journal entry, so `RssNotificationSyncWorker` no longer retries indefinitely. ([#1140](https://github.com/boxcreate/boxlore/pull/1140)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- The catalog sync no longer deletes vectors it later re-embeds: the nightly trim fails closed, shows keep the highest cap they earned, partial episode lists never cause pruning, and vectors from a failed upload are spooled and uploaded by the next run. ([#1141](https://github.com/boxcreate/boxlore/pull/1141)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->
- The catalog sync refuses to store objects as `[object Object]` text: its Turso client rejects them, and the show import and latest-episode update scrub Podcast Index values first. ([#1144](https://github.com/boxcreate/boxlore/pull/1144)) <!-- impact:no-user-impact+backend-change --> <!-- copy:locked -->

<!-- readme-copy:start pr=1140
### Fixes
- Turning off episode notifications for an RSS feed now stops sharing its feed link from your device. Links already shared stay in earlier public backups.
readme-copy:end pr=1140 -->
## [v0.0.29] - 2026-10-17

### Added
- Validate optimized APK and app bundle contracts before publication, with an optional manual preflight and fast PR regression coverage for constructors, JSON metadata, serializers, providers and dynamically loaded resources. ([#1106](https://github.com/boxcreate/boxlore/pull/1106)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Support automatic refresh, auto-downloads, Smart Downloads and explicitly opted-in public-feed notifications for RSS-only subscriptions. ([#1098](https://github.com/boxcreate/boxlore/pull/1098)) <!-- impact:user-impact-high+backend-change --> <!-- copy:locked -->
- Artwork-derived Material 3 palettes for podcast and episode details, with an Appearance opt-out. ([#1105](https://github.com/boxcreate/boxlore/pull/1105)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- A full-page custom theme editor with primary, secondary and tertiary colors, background choices and live light/dark previews. ([#1105](https://github.com/boxcreate/boxlore/pull/1105)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add cached update manifests, verified resumable APK updates, a Play-specific release variant, and release notes with upcoming changes. ([#1107](https://github.com/boxcreate/boxlore/pull/1107)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add native compact and full-screen announcements, reviewed sender integration, isolated optimized test builds, and offline release rehearsal. ([#1107](https://github.com/boxcreate/boxlore/pull/1107)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add an independent saved Brief edition preference with explicit-link precedence and edition-only Home refreshes. ([#1113](https://github.com/boxcreate/boxlore/pull/1113)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Bundle weekday regional Brief covers, fixed player artwork and the animated Echo account avatar. ([#1113](https://github.com/boxcreate/boxlore/pull/1113)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add an optional bunpod audio player with persisted appearance preferences, adaptive typography, locale-aware title hyphenation and expressive control shapes. ([#1114](https://github.com/boxcreate/boxlore/pull/1114)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add regression coverage for player style persistence, control interactions, window sizing and artwork palette contrast. ([#1114](https://github.com/boxcreate/boxlore/pull/1114)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add hermetic tests for welcome reel velocity and wrapping, word-loop ordering and timing, readable holds, reduced motion and long-running clocks. ([#1115](https://github.com/boxcreate/boxlore/pull/1115)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Echo onboarding conversation (`EchoOnboardingScreen`, `EchoShowDeck`, holo show cards with media3 clip previews) on the `guided_v1` next-turn contract and probe deck. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Shared artwork palette extractor in `:core:designsystem` (`ArtworkPalette`, `ArtworkColorScheme`, `OnScreenArtwork`), used by detail pages and the player. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Home hero card style (`HeroCardStyle`, Standard/Classic, `ClassicHeroCards`) and navigation blur level (`NavigationBlurLevel`, `FloatingNavigationBlur` on Haze 1.6.9) preferences. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `HomeRevealStage` staged cold-start reveal and a carousel-matched hero skeleton. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `PodcastInfoLoadRecovery` and `BriefingLoadingContent`. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add adaptive tablet/foldable window, card and pane specifications, side navigation and retained detail/player hosts. ([#1118](https://github.com/boxcreate/boxlore/pull/1118)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Add a tablet-only alpha startup screen with persistent acknowledgment before Welcome. ([#1118](https://github.com/boxcreate/boxlore/pull/1118)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Home hero card styles Cover (replaces Classic), Tide, Duotone, Halo and Marquee alongside Block, each with its own skeleton and `HeroCoverShade` colour options. Marquee adds a pinned cover, frosted top bar, scroll stop, auto-advance and label chips. ([#1119](https://github.com/boxcreate/boxlore/pull/1119)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Settings Appearance overview with Home, Player, Navigation and Text pages and picture tiles. ([#1119](https://github.com/boxcreate/boxlore/pull/1119)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `EchoCharacter` in `:core:designsystem`: manifest sequences of stills and alpha-packed MP4 motion clips decoded with `MediaCodec`, LED eyes on the tracked screen behind a glass mask, eye idle scripts, theme-tinted accents and a stage shape; `LocalEchoMotion` holds Echo still in tests. ([#1124](https://github.com/boxcreate/boxlore/pull/1124)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Library import screens in `:app` (`ImportPage`, `ImportFlowModel`): Echo on its stage, progress checklist, Start fresh, done and failure screens, with copy in `library_import.xml` and `ImportDialogAdaptiveTest` window-transition coverage. ([#1124](https://github.com/boxcreate/boxlore/pull/1124)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `EchoPrismCard` and `EchoPrismLayout`: the show deck's Prism collector card, sized from measured text so the pitch is never cut, with a compact arrangement for short slots. ([#1127](https://github.com/boxcreate/boxlore/pull/1127)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `BlockHeroColors`: Block hero cards take the Dark / Artwork / App theme choice; `HeroCoverShade.forStyle` keeps Block in the app theme until a colour is picked. ([#1127](https://github.com/boxcreate/boxlore/pull/1127)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `HeroTideProgress`: Wavy or Circle ring around Tide's Play disc. ([#1127](https://github.com/boxcreate/boxlore/pull/1127)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `MarqueeBackdrop` and `MarqueeBarFrost`: Marquee's uncropped cover over a blurred wash, and a live Haze frost owned by the top bar. ([#1127](https://github.com/boxcreate/boxlore/pull/1127)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Sixteen complete light/dark theme presets in Minimal, Colorful and Bold collections. ([#1099](https://github.com/boxcreate/boxlore/pull/1099)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Add hermetic JVM coverage for setup actions, button semantics, narrow and enlarged-text layouts, first-line dismissal alignment and RTL geometry. ([#1116](https://github.com/boxcreate/boxlore/pull/1116)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Support worlds experiment: `SupportPageHost` reads the `support-world` flag once per visit and shows today's power-cell page (control) or one of five themed worlds (neon, orbit, alchemy, abyss, sky) each rendered by the same `SupportDevelopmentPage` through a `SupportPageStyle` (art, fonts, names and copy only), with bundled 3D art and display fonts. ([#1122](https://github.com/boxcreate/boxlore/pull/1122)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Support analytics: `support_entry_viewed` (Settings button impression) and `support_page_left` (dwell, tiers viewed, pay tapped); Support events now carry `support_world`, `assignment_source`, `tier_index`, `amount_usd` and `payments_live`. ([#1122](https://github.com/boxcreate/boxlore/pull/1122)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Settings › Library › LeaveMeAlone (`RecommendationsSettingsPage`, `DiscoverySections` in `:core:prefs`) turns off the Brief, Picked for you and Explore's For you tab, Because you like, time-of-day rows and episode pages' Try these episodes; sections that are off make no requests (`collectDiscoverySection`). Appearance › Home links to it. ([#1123](https://github.com/boxcreate/boxlore/pull/1123)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- `recommendation_section_toggled` analytics event for each switch and for the Brief card's Always hide. ([#1123](https://github.com/boxcreate/boxlore/pull/1123)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Opening an emailed sign-in link opens Account & sync, which shows Signing you in, then the first sync, or why the link failed (Send a new link for used or expired links, Try again otherwise). `EmailLinkSignIn` in `core:auth` holds the app-scoped sign-in state, and `SignInLinkExpiredException` marks used or expired links; the sign-in toasts are removed. ([#1135](https://github.com/boxcreate/boxlore/pull/1135)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Add public announcement composition and offline preview tooling with draft review and contract tests. ([#1108](https://github.com/boxcreate/boxlore/pull/1108)) <!-- impact:no-user-impact --> <!-- copy:locked -->
### Changed
- Replace blanket R8 keeps with scoped reflection and storage contracts, enable resource shrinking with dynamic-resource retention, and exclude the debug App Check SDK from release builds. ([#1106](https://github.com/boxcreate/boxlore/pull/1106)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Link Lore background and logo colors to swipe progress, including cancelled gestures and late artwork palettes. ([#1101](https://github.com/boxcreate/boxlore/pull/1101)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Refine Lore cards with measured text fitting, a compact podcast footer, larger playback controls, matching swipe cues, and a wavy playback loader. ([#1101](https://github.com/boxcreate/boxlore/pull/1101)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Temporarily compact the Floating mini-player on the main Lore screen and restore the previous browsing state on exit. ([#1101](https://github.com/boxcreate/boxlore/pull/1101)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Clarify Lore queue replacement and the details-page action for adding episodes to the existing queue. ([#1101](https://github.com/boxcreate/boxlore/pull/1101)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Refresh Home discovery headers, poster and featured cards, mix layouts and briefing presentation with Material 3 components and matching loading states. ([#1102](https://github.com/boxcreate/boxlore/pull/1102)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Update classic light/dark colours, upright wordmarks, LORE and briefing graphics, adaptive launcher, splash and notification icons. ([#1102](https://github.com/boxcreate/boxlore/pull/1102)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Replace share-card branding with a compact “Listen on” wordmark signature. ([#1102](https://github.com/boxcreate/boxlore/pull/1102)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Redesign episode details with Material 3 surfaces, connected playback controls, separate listening progress and responsive title/metadata handling. ([#1104](https://github.com/boxcreate/boxlore/pull/1104)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Consolidate show-notes link and chapter parsing; add branded resource pills and the five latest episodes from the same show. ([#1104](https://github.com/boxcreate/boxlore/pull/1104)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Refined Podcast Info cards, metadata, connected subscription controls, genre confirmation and in-list search. ([#1105](https://github.com/boxcreate/boxlore/pull/1105)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Publish verified update metadata, serialize metadata jobs, and prepare release announcements for approval instead of automatic delivery. ([#1107](https://github.com/boxcreate/boxlore/pull/1107)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Extend optimized release contract validation to direct APK and Play bundle outputs, including installer exclusion and test-artifact rejection. ([#1107](https://github.com/boxcreate/boxlore/pull/1107)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Standardized Explore browse, collections and search results on expressive discovery cards, with matching skeletons and distinct compact show rails for concept search. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Added a collapsing Explore header, persistent player-aware tab switcher and horizontal paging with independent tab scroll state. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Shared the animated discovery tab control with Subscriptions while preserving fixed/floating preferences and badges. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Refreshed search suggestions, result headings and loading/empty states. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Recompose Home heroes with larger attached artwork, adaptive titles, bounded context typography and playing progress; replace nested hero grids with flat episode rows and a fading peek label. ([#1110](https://github.com/boxcreate/boxlore/pull/1110)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Connect Your shows and Daily mix into one animated surface with expressive selectors, matched loading geometry and bounded nested-scroll handoff. ([#1110](https://github.com/boxcreate/boxlore/pull/1110)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Standardize stable surface press feedback, retain subtle feedback for small custom controls and use the podcast-mic B for artwork placeholders. ([#1110](https://github.com/boxcreate/boxlore/pull/1110)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Redesign Brief cards and pages with themed Material 3 surfaces, three Home headlines, improved story layout and a thinner seekable wavy progress track. ([#1113](https://github.com/boxcreate/boxlore/pull/1113)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Separate onboarding and Settings account layouts, clarify sync states, hide player chrome during account restore and smooth import-to-account navigation. ([#1113](https://github.com/boxcreate/boxlore/pull/1113)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Use solid Daily Mix episode-card surfaces across themes. ([#1113](https://github.com/boxcreate/boxlore/pull/1113)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Redesign the first-launch welcome screen with curated bundled cover reels, coordinated headline entry, accent-coloured rotating words and icons, and compact Material 3 discovery/search/import actions. ([#1115](https://github.com/boxcreate/boxlore/pull/1115)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Share lifecycle-aware, system-scaled motion across welcome artwork and copy, with stable accessibility semantics, reduced-motion presentation and responsive text layout. ([#1115](https://github.com/boxcreate/boxlore/pull/1115)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Suggestions screen lanes with Your picks; nothing preselected. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `HomeYourShowsRanker` ranks Your shows within a budget; Because you like seed card redesign; the brief card hides only when finished or dismissed. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Player colours use a tonal scheme seeded from the cover; split-pill mini player; `PlayerSheetRevealLogic` fade-through. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Lore cards show full text with `LoreDeckMotion`; `m3Shimmer` is a draw-only `Modifier.Node`. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Adapt Home modules, discovery rows, Settings, reading surfaces, dialogs and keyboard/mouse input to available tablet panes. ([#1118](https://github.com/boxcreate/boxlore/pull/1118)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- `HeroCardStyle.DEFAULT` is Cover on the Artwork shade, and `HeroCardStylePreferences` saves only picked values. ([#1119](https://github.com/boxcreate/boxlore/pull/1119)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- On tablets the artwork hero cards use the Hero width bound and Marquee falls back to Cover (`homeHeroStyle`); below `ShortHeroHeight` (tabletop folds) Duotone moves its time into the copy and Halo shows as Cover. ([#1119](https://github.com/boxcreate/boxlore/pull/1119)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Onboarding Welcome and the Echo conversation use the shared `EchoCharacter`; 8.7 MB of onboarding strips and the Settings Echo atlas are replaced by 1.5 MB of shared assets. ([#1124](https://github.com/boxcreate/boxlore/pull/1124)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Account & sync page redesigned: sign-in method cards, check-email screens, one sync status panel, and animated sync tiles with a "No need to wait" note during onboarding; `AccountPageAdaptiveTest` covers window transitions. ([#1124](https://github.com/boxcreate/boxlore/pull/1124)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Appearance › Home groups each hero style's options with its live preview (`AppearanceHeroLookCard`); `SettingsPictureRow` tiles are wider, snap on fling and never fit a whole number on screen. ([#1127](https://github.com/boxcreate/boxlore/pull/1127)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Splash holds at least 350 ms and fades out (`SplashHoldPolicy`); Welcome restores the earlier cover glide and drift (`WelcomeEntryTiming`); show deck artwork is prefetched when the deck arrives. ([#1127](https://github.com/boxcreate/boxlore/pull/1127)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Streamlined automatic download settings with grouped controls, a connected episode-limit selector, and a separate help dialog. ([#1097](https://github.com/boxcreate/boxlore/pull/1097)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Clarified that background episode checks are an optional backup when show notifications are off or do not arrive. ([#1097](https://github.com/boxcreate/boxlore/pull/1097)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Appearance now has a dedicated Theme page with horizontal split-mode previews and one inline color-customization section. ([#1099](https://github.com/boxcreate/boxlore/pull/1099)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Rebuild the custom theme editor with a persistent preview, accessible color controls, validated hex input and restored draft state. ([#1112](https://github.com/boxcreate/boxlore/pull/1112)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Refine Classic, Pure and wallpaper-derived Material 3 color roles while preserving saved theme identities and custom seeds. ([#1112](https://github.com/boxcreate/boxlore/pull/1112)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Replace the Home setup banner with a responsive Material 3 split layout, neutral raised action tiles, localized copy and an aligned dismissal control; preserve existing callbacks, eligibility, analytics and hero presentation. ([#1116](https://github.com/boxcreate/boxlore/pull/1116)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- `CrossPromotionDetector` is rebuilt on `CrossPromotionTitles`, `CrossPromotionNames` and `CrossPromotionDescription`; recall rose from about 0.2 to 0.83–0.91 at 0.92–0.98 precision on 817 hand-labelled catalog episodes (`CrossPromotionCorpusTest`). `CrossPromotionResolver` searches shorter name forms and matches colon, host and parenthetical titles. ([#1120](https://github.com/boxcreate/boxlore/pull/1120)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Info pages, the player and hero cards share one artwork colour picker (`selectArtworkSeed`, `artworkColorScheme`, `ArtworkShade`); the info and player phone goldens are re-recorded for it. ([#1121](https://github.com/boxcreate/boxlore/pull/1121)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Every Support pay tap, including the control's, opens a notice that payments aren't set up yet and nothing was charged. ([#1122](https://github.com/boxcreate/boxlore/pull/1122)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Privacy settings and `docs/PRIVACY.md` describe approximate location and the Support page design test. ([#1122](https://github.com/boxcreate/boxlore/pull/1122)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- `PodcastRepository.getHomeRecommendations` replaces `getHomeBootstrapData` and asks bootstrap for recommendations only (`include`); the Brief card's Always hide is now the LeaveMeAlone switch and can be undone. ([#1123](https://github.com/boxcreate/boxlore/pull/1123)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- API calls authenticate with an API session (`ApiSessionManager`) instead of the bundled app key; App Check runs only during the session exchange, so requests no longer wait on it. ([#1130](https://github.com/boxcreate/boxlore/pull/1130)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Expired sessions renew silently with a Keystore-backed device-key proof; limited sessions retry App Check in the background. ([#1130](https://github.com/boxcreate/boxlore/pull/1130)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- New `api_session_status` analytics event; `app_check_status` now includes a failure reason. ([#1130](https://github.com/boxcreate/boxlore/pull/1130)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Share links, the email sign-in link and the privacy and account-deletion links use `https://boxlore.in`; sign-in emails use `boxlore.in` as their link domain so the link opens the app directly. The app verifies `boxlore.in` `/share`, `/auth` and `/__/auth/links` as App Links and keeps opening `aswin.cx` share and sign-in links; `WebAppLinks` holds the verified hosts, paths and share deep links, and `WebAppLinksManifestTest` keeps the manifest in step. ([#1133](https://github.com/boxcreate/boxlore/pull/1133)) <!-- impact:user-impact-low --> <!-- copy:locked -->
- Require authored PR release copy and preserve its wording while ordering release notes; remove Groq rewriting. ([#1108](https://github.com/boxcreate/boxlore/pull/1108)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- Simplify Echo clip decoding in `EchoClipFrames` to resolve SonarCloud issues; behaviour unchanged. ([#1125](https://github.com/boxcreate/boxlore/pull/1125)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- Tracked-feed backfill sends a scoped read-only service credential (`BOXLORE_SVC_BACKFILL_KEY`) to the catalog lookup when configured, falling back to the app key. ([#1126](https://github.com/boxcreate/boxlore/pull/1126)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- The announcement composer guide moved to `docs/ANNOUNCEMENT_COMPOSER.md`; `admin-panel/` stays ignored apart from the public composer files, now listed individually. ([#1129](https://github.com/boxcreate/boxlore/pull/1129)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- README screenshots now show current light-mode screens for Lore, Explore, Welcome, Home, both players, Home styles, themes, and sync and backups. ([#1132](https://github.com/boxcreate/boxlore/pull/1132)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- Replaced the featured image and kept the previous version under docs/images/archive. ([#1132](https://github.com/boxcreate/boxlore/pull/1132)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- README screenshots now show two compact feature labels per screen, placed so they do not cover the app. ([#1134](https://github.com/boxcreate/boxlore/pull/1134)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- README screenshots now use larger phones, with feature labels that rest on the phone frame instead of the app. ([#1136](https://github.com/boxcreate/boxlore/pull/1136)) <!-- impact:no-user-impact --> <!-- copy:locked -->
### Fixed
- Preserve WorkManager input-merger constructors in optimized releases so one-time download and notification-hydration work can start. ([#1106](https://github.com/boxcreate/boxlore/pull/1106)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Repair existing RSS imports without catalogue matching or changing saved show and episode identities. ([#1098](https://github.com/boxcreate/boxlore/pull/1098)) <!-- impact:user-impact-high+backend-change --> <!-- copy:locked -->
- Resolve mix artwork from episode and show metadata before falling back to the parent show image. ([#1102](https://github.com/boxcreate/boxlore/pull/1102)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Collapse the caught-up Daily Mix state and center card titles with or without a progress indicator. ([#1102](https://github.com/boxcreate/boxlore/pull/1102)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Improve cross-show promotion detection and matching for longer introductions and scheduled releases. ([#1104](https://github.com/boxcreate/boxlore/pull/1104)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Prevent generic social platform labels and reserved URL routes from being displayed as profile usernames. ([#1104](https://github.com/boxcreate/boxlore/pull/1104)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Preserve title line spacing and align episode-selection controls with the adaptive mini-player. ([#1105](https://github.com/boxcreate/boxlore/pull/1105)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Keep loading, navigation and predictive Back color transitions consistent with their destination. ([#1105](https://github.com/boxcreate/boxlore/pull/1105)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Route notification actions consistently on cold and warm launches, and restrict GitHub release alerts to eligible install channels. ([#1107](https://github.com/boxcreate/boxlore/pull/1107)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Preserve updater recovery through permission settings, cancelled installation and process recreation, and clear completed update sessions. ([#1107](https://github.com/boxcreate/boxlore/pull/1107)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Return from collection pages to the saved Explore feed with predictive Back and a consistent slide transition. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Recover missing search-result genres from unambiguous matching results and suppress generic Podcast genre chips. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Prevent rounded search/tab controls and Home utility-button press feedback from clipping at their edges. ([#1109](https://github.com/boxcreate/boxlore/pull/1109)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Sort fresh-drop and new-episode grids newest first, separate artwork playback from episode-detail taps and route the offline Mix action to downloads. ([#1110](https://github.com/boxcreate/boxlore/pull/1110)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Unify artwork fallback sources and bounded recovery across UI, playback, widgets, share cards and notification artwork while preserving optimized sizes and existing cache identities. ([#1111](https://github.com/boxcreate/boxlore/pull/1111)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Retain valid feed artwork when catalog image fields are blank, and expire failed playback bitmap requests instead of caching failures indefinitely. ([#1111](https://github.com/boxcreate/boxlore/pull/1111)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Repair legacy numeric Brief artwork references in listening history, queue and downloads on open and restored writes, preserving all non-artwork state. ([#1113](https://github.com/boxcreate/boxlore/pull/1113)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Podcast Info no longer spins forever when show details fail. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- User-facing strings use boxlore, all lowercase. ([#1117](https://github.com/boxcreate/boxlore/pull/1117)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Keep tablet playback controls clear of system navigation, preserve ranked result ordering and improve artwork-themed dark-mode foregrounds. ([#1118](https://github.com/boxcreate/boxlore/pull/1118)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Halo's Play badge is no longer clipped. ([#1119](https://github.com/boxcreate/boxlore/pull/1119)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- A JSON backup the restore rejects no longer shows "You're all set" with -1 shows (`jsonImportOutcome`). ([#1124](https://github.com/boxcreate/boxlore/pull/1124)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Import failures no longer show raw exception text, and "Nothing in your library changed" appears only when nothing was written. ([#1124](https://github.com/boxcreate/boxlore/pull/1124)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Playback: safe artwork URIs in every queue path, controller reconnection with backoff, Play next without duplicates, resume from idle, guarded service start-up, and `PlaybackErrorSkipGuard` stopping endless skips over failing streams. ([#1128](https://github.com/boxcreate/boxlore/pull/1128)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- PostHog start-up moved off the main thread (`PostHogStartup`); paged completed-episode IDs; folder writes tolerate deleted folders; streaming RSS parsing for very large feeds; download starts and removals that no longer crash. ([#1128](https://github.com/boxcreate/boxlore/pull/1128)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Duplicate list keys in podcast info and subscription reordering, `"null"` episode route arguments, deferred Cast session sync, onboarding genre fallback and video overlay insets. ([#1128](https://github.com/boxcreate/boxlore/pull/1128)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Explain app-open discovery when enabling per-show auto-download without notifications or background checks, with an optional notification action that preserves the auto-download choice. ([#1097](https://github.com/boxcreate/boxlore/pull/1097)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Regular navigation highlights use the active theme accent, and Classic-navigation player placement includes Android system navigation insets. ([#1099](https://github.com/boxcreate/boxlore/pull/1099)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Remove custom theme editor press ripples and separate Pure Home mix surfaces without outlines or transparent resting cards. ([#1112](https://github.com/boxcreate/boxlore/pull/1112)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Player-only artwork links no longer reach synced history and queue, and `repairPlaybackArtworkLinks` repairs rows older builds saved. ([#1121](https://github.com/boxcreate/boxlore/pull/1121)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Fullscreen transcript controls fit the visible screen height (`fitVisibleHeight`). ([#1121](https://github.com/boxcreate/boxlore/pull/1121)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- `/sync` lookups are sent in chunks of 20, so libraries with more than 40 shows get every update. ([#1130](https://github.com/boxcreate/boxlore/pull/1130)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
### Removed
- Analytics events with no remaining caller (`library_history_tracking_notice`, `auto_chapters_lifecycle`, `auto_transcript_lifecycle`) and the unused `subscription_count_bucket` launch enrichment; the glossary's person properties now match what the app sets. ([#1131](https://github.com/boxcreate/boxlore/pull/1131)) <!-- impact:no-user-impact --> <!-- copy:locked -->

## [v0.0.28] - 2026-10-04

### Added
- Scroll-adaptive floating miniplayer with coordinated Lore navigation docking, cookie artwork, compact progress and active playback motion. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Opt-in miniplayer seek controls in Appearance settings. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Dedicated Support boxlore screen in Settings featuring interactive 3D tactical artifacts, particle effects, and lore inspection stories. ([#1090](https://github.com/boxcreate/boxlore/pull/1090)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- PostHog telemetry tracks for support page impressions, tier toggles, and donation button clicks. ([#1090](https://github.com/boxcreate/boxlore/pull/1090)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
### Changed
- Require explicit installation-local consent for six-hour background auto-download discovery, with network, battery and charging restrictions enforced for discovery and its transfers. ([#1094](https://github.com/boxcreate/boxlore/pull/1094)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Share a six-hour freshness window for routine foreground feed refreshes, processing up to ten due feeds per pass with two concurrent requests. ([#1094](https://github.com/boxcreate/boxlore/pull/1094)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Refine regular miniplayer controls, progress, full-player expansion and floating navigation selection motion. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Reduce startup contention through deferred optional work, conditional widget initialization, cached discovery ranking and bounded serial-show resolution. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->
### Fixed
- Restore automatic episode downloads with durable push processing, publisher RSS discovery, and foreground recovery when episode pushes are missing. ([#1092](https://github.com/boxcreate/boxlore/pull/1092)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Preserve interrupted automatic downloads and protect manual downloads from automatic retention. ([#1092](https://github.com/boxcreate/boxlore/pull/1092)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Allow automatic downloads independently of episode notification settings. ([#1092](https://github.com/boxcreate/boxlore/pull/1092)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Preserve the visible New Episodes queue order through playback, restoration, Android Auto, and Cast, deferring Smart Queue fallback until completion and rejecting stale refill results. ([#1093](https://github.com/boxcreate/boxlore/pull/1093)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Restore saved progress on automatic episode transitions without treating the live player clock as an explicit seek or refreshing the history timestamp before resume policy runs. ([#1093](https://github.com/boxcreate/boxlore/pull/1093)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Cancel RSS HTTP calls when background consent or device conditions are revoked, and prevent background metadata hydration from starting downloads through an ungated callback. ([#1094](https://github.com/boxcreate/boxlore/pull/1094)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Keep floating selectors and Play All aligned with the compact player. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Preserve visible Home content during refreshes and stabilize shimmer, skeleton and artwork handoffs. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Share prefetched Lore artwork accents across cards and page colours; wrap long Settings titles onto two lines. ([#1095](https://github.com/boxcreate/boxlore/pull/1095)) <!-- impact:user-impact-high --> <!-- copy:locked -->

## [v0.0.27] - 2026-09-26

### Added
- In-app feedback screen revamp with sticky draft persistence. ([#1087](https://github.com/boxcreate/boxlore/pull/1087)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- General stability optimizations and performance enhancements. ([#1087](https://github.com/boxcreate/boxlore/pull/1087)) <!-- impact:user-impact-high --> <!-- copy:locked -->
### Fixed
- Fixed critical bugs related to Google sign in not working and onboarding authentication flow ([#1086](https://github.com/boxcreate/boxlore/pull/1086)) <!-- impact:user-impact-critical --> <!-- copy:locked -->

## [v0.0.26] - 2026-09-25

### Added
- Realtime cloud sync engine supporting subscriptions, listening history, and monotonic queue synchronization across devices. ([#1082](https://github.com/boxcreate/boxlore/pull/1082)) <!-- impact:user-impact-high+backend-change --> <!-- copy:locked -->
- Lifecycle and milestone-driven sync triggers in `CloudSyncTriggerCoordinator` with guaranteed background flushes via `CloudSyncWorker`. ([#1082](https://github.com/boxcreate/boxlore/pull/1082)) <!-- impact:user-impact-high+backend-change --> <!-- copy:locked -->
- Account switch isolation, active session teardown, and rich metadata transport with automatic local backfill. ([#1082](https://github.com/boxcreate/boxlore/pull/1082)) <!-- impact:user-impact-high+backend-change --> <!-- copy:locked -->
- Live sync status indicators and interactive manual "Sync Now" control in Account Settings. ([#1082](https://github.com/boxcreate/boxlore/pull/1082)) <!-- impact:user-impact-high+backend-change --> <!-- copy:locked -->
- Room database schema version 37 with `MIGRATION_36_37` adding `unsubscribedAt`, `isDirty`, and `syncedAt` to podcasts, `likedAt` to listening history, and new `queue_metadata` table. ([#1072](https://github.com/boxcreate/boxlore/pull/1072)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Monotonic sequence versioning and tombstone tracking in `QueueDao` and `QueueRepository` to resolve the empty queue deletion bug. ([#1072](https://github.com/boxcreate/boxlore/pull/1072)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- introduce Firebase Authentication and redesign account settings ([#1069](https://github.com/boxcreate/boxlore/pull/1069)) <!-- impact:user-impact-medium -->
- add cloud sync contracts and token authenticator ([#1075](https://github.com/boxcreate/boxlore/pull/1075)) <!-- impact:no-user-impact+backend-change -->
- extract feature settings, history, and core auth ([#1083](https://github.com/boxcreate/boxlore/pull/1083)) <!-- impact:no-user-impact -->
### Changed
- Removed deprecated remote auto-transcription polling and AI chapter generation triggers from the player interface. ([#1071](https://github.com/boxcreate/boxlore/pull/1071)) <!-- impact:user-impact-low --> <!-- copy:locked -->
### Fixed
- Decoupled episode like timestamps from playback progress so playback updates do not clobber user likes. ([#1072](https://github.com/boxcreate/boxlore/pull/1072)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Fixed `PlaybackQueueCoordinator` to delete removed queue items directly instead of replacing the entire table. ([#1072](https://github.com/boxcreate/boxlore/pull/1072)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- resolve account authentication and settings refinements ([#1070](https://github.com/boxcreate/boxlore/pull/1070)) <!-- impact:user-impact-medium -->

## [v0.0.25] - 2026-09-08

### Added
- Added interactive same-show continuation banner with expandable episode preview and quick-refill action in the player queue sheet. ([#1019](https://github.com/boxcreate/boxlore/pull/1019)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Room schema v35 with `FolderEntity` and `PodcastFolderCrossRef` for persistent subscription folders and show memberships. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- `FolderRepository` and `RoomFolderRepository` in `:core:catalog` managing folder CRUD, genre linking, and automatic organization. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- `LibraryBackupFolderLogic` and extended `LibraryBackupManager` v6 supporting backup/restore of folder structures, memberships, folder sort preferences, and custom podcast tags. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Direct-clickable show slots, overflow mini-clusters (`+N`), and `FolderExpandedDialog` for enlarged folder interactions. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Drag-and-drop folder reordering with persistent manual order in `subscriptionFolderManualOrder` and custom scoped reorder bar. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Offline chapter and transcript persistence for downloaded episodes, including Room migration v35 -> v36. ([#1065](https://github.com/boxcreate/boxlore/pull/1065)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Automatic fallback to show note description timestamps for offline chapter generation when remote JSON chapters are absent. ([#1065](https://github.com/boxcreate/boxlore/pull/1065)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Subscriptions tab presentation style preference (`top` vs `floating`) in `:core:prefs`, Settings UI, and Subscriptions screen. ([#1048](https://github.com/boxcreate/boxlore/pull/1048)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Elevated Play All FAB animation when floating tab selector is active. ([#1048](https://github.com/boxcreate/boxlore/pull/1048)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Backup and restore support for `subscriptionsTabStyle`, `navigationStyle`, and `fontRoundness` in `LibraryBackupManager`. ([#1048](https://github.com/boxcreate/boxlore/pull/1048)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Custom podcast genre tag and icon picker for subscribed shows (#1051, #1032). ([#1055](https://github.com/boxcreate/boxlore/pull/1055)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Room migration 13 to 14 adding `custom_genre` and `custom_genre_icon` columns to `podcasts`. ([#1055](https://github.com/boxcreate/boxlore/pull/1055)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Curated 30+ Material rounded icon catalog in `GenreIcons.kt`. ([#1055](https://github.com/boxcreate/boxlore/pull/1055)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Hybrid user-priority ordering for custom tags in Subscriptions filter chips. ([#1055](https://github.com/boxcreate/boxlore/pull/1055)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- time-of-day rotation and cross-day variation for because you like rail ([#1063](https://github.com/boxcreate/boxlore/pull/1063)) <!-- impact:user-impact-medium -->
### Changed
- Subscriptions screen grid rendering to support sized folder items alongside unfiled podcasts. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Enhanced subscription sort sheet with 3-tier folder and intra-folder sort options and auto-organize controls. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Enforced canonical genre normalization (mapping `"Technology"` to `"Tech"` and auto-assigning `"tech"` icon). ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Resolved out-of-memory errors during large podcast pagination and feed parsing by streaming responses and bounding in-memory cache sizes (#1026). ([#1044](https://github.com/boxcreate/boxlore/pull/1044)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Modernize ktlint setup with Compose-friendly `.editorconfig`, remove 24 legacy XML baseline files, and apply repository-wide code formatting. ([#1020](https://github.com/boxcreate/boxlore/pull/1020)) <!-- impact:no-user-impact --> <!-- copy:locked -->
- Enabled Gradle configuration cache in gradle.properties, reducing build configuration time for local and CI builds ([#1021](https://github.com/boxcreate/boxlore/pull/1021)) <!-- impact:no-user-impact -->
### Fixed
- Fixed same-show continuation in `SmartQueueEngine` by restoring forward chronological queries in `LocalEpisodeCatalogDao` and `RssEpisodeDao` (#1017). ([#1019](https://github.com/boxcreate/boxlore/pull/1019)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Preserved `preferredSort` and `contextSourceId` across `QueueManager` and `PlaybackQueueCoordinator` for accurate refill sequencing. ([#1019](https://github.com/boxcreate/boxlore/pull/1019)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Fixed an `IllegalArgumentException` crash when navigating to an episode with a missing, empty, or special-character description from Home feed recommendations by marking `episodeDescription` nullable with a default null value in `NavGraphPodcastEpisodeDestinations` (#1023). ([#1030](https://github.com/boxcreate/boxlore/pull/1030)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Prevent background `ForegroundServiceStartNotAllowedException` and `RemoteServiceException` crashes on Android 14+ by routing background auto-downloads and smart downloads directly through `DownloadManager` without launching `MediaDownloadService` (#1024). ([#1033](https://github.com/boxcreate/boxlore/pull/1033)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Eliminate silent disk storage leaks in `SimpleCache` by ensuring background download removals directly evict media cache files even when background service starts are blocked. ([#1033](https://github.com/boxcreate/boxlore/pull/1033)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Fix notification auto-downloads failing or getting stuck in downloading state by awaiting completion under WorkManager wake locks and reconciling stale orphaned download records. ([#1033](https://github.com/boxcreate/boxlore/pull/1033)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Prevent WorkManager 10-minute execution ceiling aborts during smart downloads by transitioning to sequential downloads with an 8.5-minute time budget. ([#1033](https://github.com/boxcreate/boxlore/pull/1033)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Reorder jump glitch and touch targeting in subscriptions drag-and-drop grid. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Keyboard dismissal and state retention in folder creation bottom sheet. ([#1059](https://github.com/boxcreate/boxlore/pull/1059)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Preserved notification and auto-download toggles on Podcast Info pull-to-refresh and routed subscribed shows to direct publisher feed refresh. ([#1016](https://github.com/boxcreate/boxlore/issues/1016)) ([#1022](https://github.com/boxcreate/boxlore/pull/1022)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Fixed latent `notificationsEnabled` and `autoDownloadEnabled` mapping in RSS repository entity conversion. ([#1022](https://github.com/boxcreate/boxlore/pull/1022)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Prevent SecurityException crashes caused by system PendingIntent UID quota exhaustion in FCM notifications and Media3 playback session setup (#1025). ([#1034](https://github.com/boxcreate/boxlore/pull/1034)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Preserved show name metadata during Android Auto playback and resolved session restore reversions when replaying completed episodes (#1035). ([#1036](https://github.com/boxcreate/boxlore/pull/1036)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Fixed Android Auto reconnection failure by registering `MediaButtonReceiver` and implementing `onPlaybackResumption` with queue hydration and repeat session recovery (#1038). ([#1045](https://github.com/boxcreate/boxlore/pull/1045)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Fixed disabled or missing chapters and transcripts during offline playback of downloaded episodes. ([#1065](https://github.com/boxcreate/boxlore/pull/1065)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Fixed player dynamic theme application on inline transcript header and fullscreen button. ([#1065](https://github.com/boxcreate/boxlore/pull/1065)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Calibrated player metadata typography and muted colors in transcript mode without visual conflict with inactive transcript lines. ([#1065](https://github.com/boxcreate/boxlore/pull/1065)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Adapted the Cast button in the expanded player sheet to respect dark and light themes and match the Share button theming (#1037). ([#1039](https://github.com/boxcreate/boxlore/pull/1039)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Handled broken pipe and network disconnect exceptions safely during Android Auto artwork streaming (#1027). ([#1041](https://github.com/boxcreate/boxlore/pull/1041)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Enforce `MediaController` main-thread affinity in `PlaybackRepository` to prevent `IllegalStateException` on background session restoration. ([#1042](https://github.com/boxcreate/boxlore/pull/1042)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Protect `LazyColumn` and `LazyRow` lists against duplicate keys to prevent `IllegalArgumentException` in recommendation and explore feeds. ([#1043](https://github.com/boxcreate/boxlore/pull/1043)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Fixed Video Spotlight card tap animation in `FeaturedVideoPodcastsShowcase` by placing `expressiveClickable` before shadow, border, and surface background (#1031). ([#1046](https://github.com/boxcreate/boxlore/pull/1046)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Forwarded `onSetSubscriptionsTabStyle` callback in `AppearanceActions.trackedForAnalytics()` to register clicks in Settings > Appearance. ([#1048](https://github.com/boxcreate/boxlore/pull/1048)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Streamlined JSON library restore with real-time show-by-show progress reporting and active wavy loader animation throughout feed downloads. ([#1050](https://github.com/boxcreate/boxlore/pull/1050)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Refactored post-restore notification card to a single dynamic button that triggers the system runtime permission dialog and refreshes on resume. ([#1050](https://github.com/boxcreate/boxlore/pull/1050)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Reset custom tags and icons to default catalog genres when unsubscribing from a show. ([#1055](https://github.com/boxcreate/boxlore/pull/1055)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Prompt Material 3 confirmation dialog before removing downloaded episodes across Podcast Info, Episode Info, Expanded Player, and Downloaded Episodes screen (#1061). ([#1064](https://github.com/boxcreate/boxlore/pull/1064)) <!-- impact:user-impact-medium --> <!-- copy:locked -->

## [v0.0.24] - 2026-08-30

### Added
- Added regression coverage for unsubscribe attempts while podcast data is still loading. ([#1009](https://github.com/boxcreate/boxlore/pull/1009)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Added an alternate compact playback widget with Next and seek-forward controls. ([#1010](https://github.com/boxcreate/boxlore/pull/1010)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Regression tests covering normalized, whitespace, and case variants of briefing market ([#1008](https://github.com/boxcreate/boxlore/pull/1008)) <!-- impact:user-impact-medium -->
### Changed
- Improved playback metadata sizing, New Episodes title wrapping, widget picker previews, and idle states. ([#1010](https://github.com/boxcreate/boxlore/pull/1010)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Redesigned the artwork sharing sheet and refined Story and square share-card composition. ([#1011](https://github.com/boxcreate/boxlore/pull/1011)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Core model module documentation updated to describe briefing-market behavior ([#1008](https://github.com/boxcreate/boxlore/pull/1008)) <!-- impact:user-impact-medium -->
### Fixed
- Made playback progress persistence service-authoritative and sequence-safe, preventing stale UI or lifecycle writes from restoring old resume positions and ensuring restored player progress is available immediately. ([#1012](https://github.com/boxcreate/boxlore/pull/1012)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Prevented delayed Podcast Info loads from undoing an unsubscribe by re-reading live subscription state before applying page data and preserving subscription fields during delayed feed results. ([#1009](https://github.com/boxcreate/boxlore/pull/1009)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Briefing market handling now preserves explicit `global` market before storefront canonicalization ([#1008](https://github.com/boxcreate/boxlore/pull/1008)) <!-- impact:user-impact-medium -->

## [v0.0.23] - 2026-08-28

### Changed
- Reduced background playback polling, paused-service lifetime, outro polling, and download notification wakeups while preserving active playback and Cast behavior. ([#1006](https://github.com/boxcreate/boxlore/pull/1006)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Unified Your Shows scoring around one subscription-recency policy and added stable, lifecycle-aware Home order refreshes. ([#1006](https://github.com/boxcreate/boxlore/pull/1006)) <!-- impact:user-impact-high --> <!-- copy:locked -->

## [v0.0.21] - 2026-08-27

### Added
- Add Chromecast playback with active route discovery, synchronized queue transfer, remote volume, and integrated expanded-player controls. ([#1004](https://github.com/boxcreate/boxlore/pull/1004)) <!-- impact:user-impact-high --> <!-- copy:locked -->
### Fixed
- Preserve Cast queue metadata and recover sender controls across connection, sleep/wake, and disconnect transitions. ([#1004](https://github.com/boxcreate/boxlore/pull/1004)) <!-- impact:user-impact-high --> <!-- copy:locked -->

## [v0.0.20] - 2026-08-26

### Added
- Added App theme and System appearance modes for all RemoteViews widget families, backed by shared boxlore color-scheme resolution. ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Added a dismissible Home Video Spotlight with bundled video podcast metadata, TED HD/SD feeds, and PostHog funnel attribution through playback start. ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Added configurable default tabs for Explore and Subscriptions, including backup/restore and fast-cache hydration. ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Playback **Smart queue** switch (`same_show_queue_only`; default on). Off limits auto-queue to later episodes of the current show. ([#1001](https://github.com/boxcreate/boxlore/pull/1001)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Appearance **Cleaner Home** (`home_shortcuts_in_library`; default off) moves Settings and Feedback from Home to the Library top bar. ([#1001](https://github.com/boxcreate/boxlore/pull/1001)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Queue empty state explains when Smart queue is off and offers Turn on. ([#1001](https://github.com/boxcreate/boxlore/pull/1001)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
### Changed
- Custom accents can now use a Material 3 seed or pin the selected RGB as the exact primary color. ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Refined shared Settings and Privacy layout spacing and alignment. ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Reordered README Upcoming by listener priority and removed the stale PR #1000 entry from README only. ([#1002](https://github.com/boxcreate/boxlore/pull/1002)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
### Fixed
- Repaired publisher-feed catalog cutover after library restore by reconciling legacy listener identities and retrying incomplete catalogs. ([#1000](https://github.com/boxcreate/boxlore/pull/1000)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Added bounded direct-feed ingestion across JSON restore, OPML, onboarding, Home, and Podcast Info subscription paths. ([#1000](https://github.com/boxcreate/boxlore/pull/1000)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Turning Smart queue on from an empty queue refills via the playback service player instead of the session forwarding player. ([#1001](https://github.com/boxcreate/boxlore/pull/1001)) <!-- impact:user-impact-medium --> <!-- copy:locked -->

## [v0.0.19] - 2026-08-18

### Added
- Home Your Shows Daily / Offline Mix switch, persisted as `home_mix_mode`, with a 15-item unfinished-download rail and queue. ([#996](https://github.com/boxcreate/boxlore/pull/996)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Added Podcast Info episode multi-selection with bulk download, played/unplayed, playback, queue, and full-feed range actions. ([#997](https://github.com/boxcreate/boxlore/pull/997)) <!-- impact:user-impact-high --> <!-- copy:locked -->
### Fixed
- One-time repair migrates legacy OPML `rss:` subscriptions to Podcast Index identity on exact feed-URL or GUID matches (v0.0.18/0.0.19 upgraded installs only; never loops). ([#993](https://github.com/boxcreate/boxlore/pull/993)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- OPML import defers a feed on transient Podcast Index lookup failure instead of creating an RSS fallback. ([#993](https://github.com/boxcreate/boxlore/pull/993)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Now Playing widget session restore hops to the MediaController main thread. ([#993](https://github.com/boxcreate/boxlore/pull/993)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- After Google Backup restore, Smart Downloads toggle and periodic mixtape sync stay aligned; missing local download artwork falls back to the show image. ([#996](https://github.com/boxcreate/boxlore/pull/996)) <!-- impact:user-impact-high --> <!-- copy:locked -->

## [v0.0.18] - 2026-08-15

### Added
- First-class local episode catalog (`local_episode_feeds` / `local_episodes`) for subscribed PI shows with an HTTPS feed, with sticky `episodeId` upsert and a per-show ready gate. ([#986](https://github.com/boxcreate/boxlore/pull/986)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- Appearance **Open app to** can land on Downloads after a cold start (Back goes to Home, same as Subscriptions) ([#990](https://github.com/boxcreate/boxlore/pull/990)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
### Changed
- Subscribe and Home sync persist the publisher feed; ready shows serve list/search/`getEpisode` from Room only. ([#986](https://github.com/boxcreate/boxlore/pull/986)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- "Missing episodes?" remains unsubscribed extras opt-in; the confirm dialog explains Podcast Index, once-per-podcast extras, and subscribe for the complete list. ([#986](https://github.com/boxcreate/boxlore/pull/986)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- Check New Episodes notifies on first RSS see when the PI episode id differs (`rss-new-after-pi`); same PI id stays a quiet baseline. ([#986](https://github.com/boxcreate/boxlore/pull/986)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
### Fixed
- Subscribed shows no longer hide publisher episodes that Podcast Index has not indexed yet. ([#986](https://github.com/boxcreate/boxlore/pull/986)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- Episode artwork falls back to channel / show image when the item has no itunes:image. ([#986](https://github.com/boxcreate/boxlore/pull/986)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- OPML import matches Podcast Index first (feed URL variants, title/feed search, then publisher-feed GUID / redirected URL) and only creates a true `rss:` row when the show is not in the catalog. Settings → Add RSS is unchanged. ([#984](https://github.com/boxcreate/boxlore/pull/984)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Subscribe catalog ingest now runs on application-scoped `SubscriptionForegroundSync`, so leaving Podcast Info does not cancel the first publisher-feed persist ([#989](https://github.com/boxcreate/boxlore/pull/989)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Unsubscribed local catalogs with a TTL are no longer treated as ready, so show pages keep using Podcast Index (including new PI episodes) ([#989](https://github.com/boxcreate/boxlore/pull/989)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Subscription launch/refresh sync now sweeps expired 14-day local catalogs ([#989](https://github.com/boxcreate/boxlore/pull/989)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Pin progressive Find-a-show results to the top in onboarding search and Explore when catalog hits prepend over local matches. ([#987](https://github.com/boxcreate/boxlore/pull/987)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Stop the expanded player from minimizing when a content swipe reaches the top; collapsing now takes a new downward swipe. ([#988](https://github.com/boxcreate/boxlore/pull/988)) <!-- impact:user-impact-medium --> <!-- copy:locked -->

## [v0.0.17] - 2026-08-14

### Added
- Manual subscription sort: long-press-drag artwork on the unfiltered Shows list; first drop seeds `subscription_manual_order` and switches `subscription_sort` to Manual. New shows append A–Z; unsubscribe drops the id. ([#982](https://github.com/boxcreate/boxlore/pull/982)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Up to five Home pins (`home_pinned_podcast_ids`) lead Your Shows after mixtape. Long-press a cover for Pin / Unpin; show-page overflow has the same actions. A sixth pin is refused with a snackbar. ([#982](https://github.com/boxcreate/boxlore/pull/982)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Long-press a Downloads show or episode to enter multi-select with that row checked (checklist toggle remains). ([#980](https://github.com/boxcreate/boxlore/pull/980)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
### Fixed
- JSON library backup/restore now keeps Missing episodes? (direct publisher feed) opt-ins and refreshes those shows on import. ([#978](https://github.com/boxcreate/boxlore/pull/978)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Draw library import (JSON and OPML) in the activity window so OEM Dialog status-bar scrims cannot mismatch the page color. ([#979](https://github.com/boxcreate/boxlore/pull/979)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Restore predictive-back peek scale after Back from a cold-start Subscriptions screen so Home is not left at 0.9. ([#981](https://github.com/boxcreate/boxlore/pull/981)) <!-- impact:user-impact-medium --> <!-- copy:locked -->

## [v0.0.16] - 2026-08-14

### Fixed
- Follow-up to #971: subscribed Home chips, Library New Episodes, and Podcast Info rematch publisher-feed extras against a 1000-oldest PI baseline and refresh Room tips on launch, resume, Subscriptions appear, 15-minute periodic sync, and FCM — without opening each show. ([#975](https://github.com/boxcreate/boxlore/pull/975)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
- Open-app-to-Subscriptions runs a live PI `/sync` (`requestRefresh`) instead of a cache-only Room read; Home still delays 2s for first paint. ([#975](https://github.com/boxcreate/boxlore/pull/975)) <!-- impact:user-impact-critical --> <!-- copy:locked -->

## [v0.0.15] - 2026-08-12

### Added
- PodcastRepository unions cached publisher-feed extras after the PI page for getEpisodes, offset-0 pagination, and in-show search (skip rss: rows; merged lists are not cached) ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- Check New Episodes polls HTTPS feedUrl on RTDB tracked_podcasts when present (lastRssKey = guid else enclosure); unmatched feed-only drops omit PI episodeId and open the podcast page; GHA never mints negative ids ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- NewEpisodePushHydration refreshes an opted-in feed on FCM, promotes the local tip with markAsNew, and notifies / autodownloads with the local episode id ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
### Changed
- Subscribe auto-opts in to Missing episodes? when PI vs the publisher feed is disconnected; notifications stay off by default and still do not register on subscribe ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- setNotificationsEnabled(true) writes RTDB tracked_podcasts/{id} including HTTPS feedUrl when the show is opted in; foreground sync heals existing notifying + opted-in rows ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
- LatestEpisodeTipLogic never writes an older tip over a newer one; same date + different id still replaces (PI catch-up); feed promotes use updateLatestEpisode(..., markAsNew = true) ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->
### Fixed
- Home Your Shows NEW, the New episodes chip, Podcast Info, and Smart Queue no longer miss publisher-feed episodes that Podcast Index has not ingested yet ([#971](https://github.com/boxcreate/boxlore/pull/971)) <!-- impact:user-impact-critical+backend-change --> <!-- copy:locked -->

## [v0.0.14] - 2026-08-07

### Added
- Redesigned Subscriptions UI with genre filter chips, denser episode rows, image‑first show grid, sort/hide‑played options, and elevated tabs; added persistent open_app_to preference in DataStore with analytics tracking; introduced SubscriptionForegroundSync process that runs once per process after onboarding to perform foreground latest‑episode sync for subscriptions. ([#958](https://github.com/boxcreate/boxlore/pull/958)) <!-- impact:user-impact-high -->
- Implemented home‑screen RemoteViews widgets (now playing 4×2, compact bar 4×1, playback controls 2×2, scrollable Subscriptions and New Episodes 4×3) via new :feature:widgets module with providers, coordinators, snapshot stores, RemoteViewsService adapter, artwork loader, theme sync, and integration into :app through WidgetPlaybackSource and WidgetLibrarySource, supporting system accent colors, deep links, and widget picker previews. ([#959](https://github.com/boxcreate/boxlore/pull/959)) <!-- impact:user-impact-high -->
### Changed
- Navigation start‑destination now respects open_app_to setting, launching Subscriptions directly when selected and using Back to return to Home; HomeViewModel delegating foreground sync to SubscriptionForegroundSync. ([#958](https://github.com/boxcreate/boxlore/pull/958)) <!-- impact:user-impact-high -->
- Updated CTA button texts to “Continue with N”, “Start without adding”, and “Add N & start”, and refined loader copy to “Subscribed to N shows”, aligning with FeedMediaCard bottom‑art clipping. ([#960](https://github.com/boxcreate/boxlore/pull/960)) <!-- impact:user-impact-medium -->
### Fixed
- Restored suggestions loading gate to display a real loader during AI loading or synthesizing in search and OPML onboarding, eliminating empty “No suggestions yet” state; cleared stale curriculum on continue/back and filtered seed picks out of similar‑show lanes, preventing auto‑selection of recommendations in search/OPML flows. ([#960](https://github.com/boxcreate/boxlore/pull/960)) <!-- impact:user-impact-medium -->

## [v0.0.13] - 2026-07-29

### Added
- An all‑new way to search shows and episodes: typo‑tolerant podcast search powered by Meilisearch, plus concept search for shows and episodes powered by Qdrant. Explore chips are **Find a show** and **By idea**, with typeahead results grouped as Matches and Also found. ([#955](https://github.com/boxcreate/boxlore/pull/955)) <!-- impact:user-impact-high -->
- Discovery expands with new chart countries — France, Germany, Netherlands, Singapore, Spain, Brazil, Russia, and Indonesia — alongside a country picker and language filters, and stronger multilingual recommendations using Qwen3 embeddings. ([#953](https://github.com/boxcreate/boxlore/pull/953)) <!-- impact:user-impact-high+backend-change -->
### Changed
- Faster content delivery from backend optimizations so charts, search, and recommendations reach you sooner. ([#953](https://github.com/boxcreate/boxlore/pull/953)) <!-- impact:user-impact-high+backend-change -->
- NotebookLM‑style briefings are tighter and less sloppy — more aware of repetition, depth, and which stories matter. ([#949](https://github.com/boxcreate/boxlore/pull/949)) <!-- impact:user-impact-high -->
- UI polish across startup, onboarding suggestions, Home and Explore search, and several other screens — including equal‑height discovery posters and Explore For You card layout. ([#954](https://github.com/boxcreate/boxlore/pull/954)) ([#952](https://github.com/boxcreate/boxlore/pull/952)) ([#953](https://github.com/boxcreate/boxlore/pull/953)) <!-- impact:user-impact-medium -->

## [v0.0.12] - 2026-07-25

### Added
- Floating bottom navigation chrome (3‑tab pill + Lore FAB) with default navigation_style set to floating (classic selectable in Settings → Appearance → Navigation); Round lettering preset added with lighter GoogleSansWeight scale (Soft and Crisp retained); HTTPS‑only App Links for aswin.cx share endpoints with autoVerify and updated assetlinks.json (Play signing SHA‑256). ([#949](https://github.com/boxcreate/boxlore/pull/949)) <!-- impact:user-impact-high -->
- Intent‑aware soft‑expire for implicit playback via UserPreferencesRepository.restartForgottenEpisodesStream and PlaybackSkipPolicy logic to restart episodes after 7 days without lastPlayedAt; Home discovery layout overhaul using EqualHeightPosterGrid and updated card components (FeedMediaCard, PodcastCard, CuratedEpisodeCard) to provide equal‑height title‑only posters across rails and grids. ([#950](https://github.com/boxcreate/boxlore/pull/950)) <!-- impact:user-impact-high -->
- Hermetic cache encode/decode helpers introduced for shared recommendation data, accompanied by unit tests. ([#947](https://github.com/boxcreate/boxlore/pull/947)) <!-- impact:user-impact-medium -->
- Google Sans Flex variable font (OFL) integrated with new Appearance → Lettering presets (Crisp, Soft, Round) and preview toggle; ConnectedOptionSelector chip component added for theme and lettering settings; DataStore keys font_roundness and boxlore_theme_fast_cache with BoxLoreTheme(fontRoundness) and LocalFontRoundness enable fast theme caching; typography across headers, onboarding, HtmlText, About, share cards, and auto‑collage badges now uses Flex font respecting roundness. ([#948](https://github.com/boxcreate/boxlore/pull/948)) <!-- impact:user-impact-medium -->
### Changed
- Mini‑player geometry and padding tuned to avoid overlap with floating navigation; briefing playback and duration helpers refined for deterministic story timing; Detekt rule set cleaned up for navigation chrome and player geometry utilities. ([#949](https://github.com/boxcreate/boxlore/pull/949)) <!-- impact:user-impact-high -->
- Smart Queue now labels soft‑expired resumes as “Starting over” via QueueLabelLogic and SOURCE_RESUME_STALE; Mixtape and Your Shows cards suppress progress bar and “Xm left” for soft‑expired picks, cache invalidated on preference toggle through HomeMixtapeCache; Settings UI adds Playback → Resume → Restart forgotten episodes toggle and integrates with backup/restore via LibraryBackupManager. ([#950](https://github.com/boxcreate/boxlore/pull/950)) <!-- impact:user-impact-high -->
- Explore For You now hydrates from BoxcastPrefs recommendation cache and refreshes via getHomeBootstrapData, eliminating the empty‑seed early‑return path. ([#947](https://github.com/boxcreate/boxlore/pull/947)) <!-- impact:user-impact-medium -->
- Default lettering roundness defaulted to Soft (ROND 50) on fresh installs with sanitization fallback; theme system/light/dark chip style unified via ConnectedOptionSelector and fast‑cache integration. ([#948](https://github.com/boxcreate/boxlore/pull/948)) <!-- impact:user-impact-medium -->
- Node test suite added for CSV helpers; Podcast Index dump export now normalizes CR/LF, CSV import made quote‑aware and skips rows with mismatched field counts. ([#945](https://github.com/boxcreate/boxlore/pull/945)) <!-- impact:no-user-impact+backend-change -->
### Fixed
- Detekt warnings resolved with targeted @Suppress annotations and updated module READMEs. ([#950](https://github.com/boxcreate/boxlore/pull/950)) <!-- impact:user-impact-high -->
- Fixed blank state in For You on fresh installs by sourcing Home bootstrap recommendations from BoxcastPrefs cache. ([#947](https://github.com/boxcreate/boxlore/pull/947)) <!-- impact:user-impact-medium -->
### Removed
- Roborazzi verification task (:feature:home:verifyRoborazziDebug) removed from CI unit‑tests workflow, rendering goldens local‑optional. ([#949](https://github.com/boxcreate/boxlore/pull/949)) <!-- impact:user-impact-high -->
- Legacy Google Sans TTF assets removed. ([#948](https://github.com/boxcreate/boxlore/pull/948)) <!-- impact:user-impact-medium -->

## [v0.0.11] - 2026-07-22

### Added
- Expressive listening history insights: session rollups, calendar & stats UI, Room persistence layer, and playback telemetry integration. ([#932](https://github.com/boxcreate/boxlore/pull/932)) <!-- impact:user-impact-high -->
- PlaybackControlSync in :core:playback to preserve playback speed and seek sizes across session clear and restore UI sync ([#933](https://github.com/boxcreate/boxlore/pull/933)) <!-- impact:user-impact-high -->
- Home now displays up to three curated editorial rows per daypart using PodcastRepository.getCuratedVibes with playable filtering and cross‑row de‑duplication ([#942](https://github.com/boxcreate/boxlore/pull/942)) <!-- impact:user-impact-medium -->
- In‑memory SignalLog for ranking engine mutations and model movement, with Debug UI panels (Signals, Taste, Model) replacing animated learner visualizations. ([#897](https://github.com/boxcreate/boxlore/pull/897))
### Changed
- Refresh detekt baseline to clear signature‑drift findings ([#933](https://github.com/boxcreate/boxlore/pull/933)) (+ 1 related changes) <!-- impact:user-impact-high -->
- Greeting line on Home now shows title only without subtitle; editorial rows are rendered flush with the feed ([#942](https://github.com/boxcreate/boxlore/pull/942)) <!-- impact:user-impact-medium -->
- Enabled SignalLog capture by default in debug builds, disabled in release unless opted‑in; Learner engine now uses atomic exposure resolution with synchronized enable/disable of SignalLog. ([#897](https://github.com/boxcreate/boxlore/pull/897))
- Updated actions/checkout to v7 in GitHub Actions workflows ([#886](https://github.com/boxcreate/boxlore/pull/886)) <!-- impact:no-user-impact -->
- Updated actions/setup-java to v5.6.0 ([#888](https://github.com/boxcreate/boxlore/pull/888)) <!-- impact:no-user-impact -->
- Changed actions/cache action version from 4 to 6 ([#889](https://github.com/boxcreate/boxlore/pull/889)) <!-- impact:no-user-impact -->
- Updated @libsql/client to 0.17.4 in Gradle build scripts. ([#894](https://github.com/boxcreate/boxlore/pull/894)) <!-- impact:no-user-impact -->
- Pinned transitive dependencies (Netty 4.1.136, BouncyCastle 1.84, jose4j 0.9.6, jdom2 2.0.6.1, commons‑compress 1.26.2, protobuf 3.25.5) and overridden protobufjs 7.6.5, uuid 11.1.1 to address Dependabot Maven alerts and npm audit findings. ([#896](https://github.com/boxcreate/boxlore/pull/896)) <!-- impact:no-user-impact -->
- modular hardening, AGP 9 toolchain, merge-queue tests, Develocity ([#902](https://github.com/boxcreate/boxlore/pull/902)) <!-- impact:no-user-impact -->
- Modular Android hardening (plans A0–A8 / B0–B10): introduced single AppContainer composition root, extracted core modules (:core:prefs, :core:downloads, :core:analytics, :core:ranking, :core:rss), slimmed MainActivity to shell over BoxLoreNavHost, replaced reflective DownloadServiceLauncher, upgraded to AGP 9 toolchain with Develocity wiring, added module README contracts. ([#903](https://github.com/boxcreate/boxlore/pull/903)) <!-- impact:no-user-impact -->
- Removed .github/dependabot.yml and disabled Dependabot security updates via repository settings, halting automatic version‑update and security PRs. ([#905](https://github.com/boxcreate/boxlore/pull/905)) <!-- impact:no-user-impact -->
- Changed CI workflow to run unit, detekt, ktlint, and Kover checks only on merge queue or manual dispatch ([#906](https://github.com/boxcreate/boxlore/pull/906)) <!-- impact:no-user-impact -->
- Changed CI workflow to run unit and instrumented tests only when the `merge-ci` label is present, and updated merge queue script accordingly. ([#907](https://github.com/boxcreate/boxlore/pull/907)) <!-- impact:no-user-impact -->
- CI documentation updated to clarify merge-ci labeling and PR template flow ([#908](https://github.com/boxcreate/boxlore/pull/908)) <!-- impact:no-user-impact -->
- Added cloud-agent next-wave playbook documentation (docs/PLAN_CLOUD_AGENT_NEXT.md) with phase constraints and verification gates ([#909](https://github.com/boxcreate/boxlore/pull/909)) <!-- impact:no-user-impact -->
- Reorganized feature and playback extraction under LOC caps: split home/playback, defined module boundaries so UI flows reside in :feature:* and shared engines in :core:*. ([#919](https://github.com/boxcreate/boxlore/pull/919)) <!-- impact:no-user-impact -->
- Phase 2 package alignment: moved PostHog façade to :core:analytics, replaced analytics glossary, migrated satellite/prefs, aligned playback/database package with Auto Phase C, and catalog package with Phase 2 final signoff. ([#921](https://github.com/boxcreate/boxlore/pull/921)) <!-- impact:no-user-impact -->
- Architecture‑as‑code and test harness added: Konsist boundary guards enforce graph constraints (no feature→feature, no Hilt/MockK, catalog boundaries), expanded hermetic JVM suites and :core:testing fakes, raised merged Kover floor, integrated Roborazzi verification in unit gate, added ARCHITECTURE.md and TESTING.md hubs with ranking cross‑links. ([#930](https://github.com/boxcreate/boxlore/pull/930)) <!-- impact:no-user-impact -->
- Changed CI configuration to require CodeRabbit review threads resolved before merge by adding coderabbit-threads-resolved check and updating master merge queue ruleset ([#934](https://github.com/boxcreate/boxlore/pull/934)) <!-- impact:no-user-impact -->
- Reduced CI required checks to testDebugUnitTest and CodeRabbit‑threads‑resolved; disabled automatic CodeRabbit request_changes_workflow; enforced policy that all CodeRabbit threads must be resolved before merge and blocks merges on CHANGES_REQUESTED, requiring manual merge; removed merge-queue-external-gates.yml (Sonar + CodeRabbit merge‑queue). ([#935](https://github.com/boxcreate/boxlore/pull/935)) <!-- impact:no-user-impact -->
### Fixed
- Restore artwork tint colors after process death by using Coil ImageLoader.execute with disk+memory cache and PlaybackArtworkResolver ([#933](https://github.com/boxcreate/boxlore/pull/933)) (+ 1 related changes) <!-- impact:user-impact-high -->
- Preserved MediaController playback state on app reopen; restoreLastSession() no longer forces isPlaying = false, keeping mini and full player synchronized. ([#904](https://github.com/boxcreate/boxlore/pull/904)) <!-- impact:user-impact-medium -->
- Fixed Android Auto artwork pipeline by adding a durable AutoArtworkSourceStore, a shared AutoArtworkDownloader with HTTPS redirect validation and lenient image content‑type handling, refreshed collage signatures with shorter TTLs, and automatic collage refresh on queue changes, eliminating stale collages and blank episode covers. ([#939](https://github.com/boxcreate/boxlore/pull/939)) <!-- impact:user-impact-medium -->
- LearningEventLog startup gating now defaults to off in release builds unless BoxcastPrefs.resolveLearnerLogEnabled indicates an explicit opt‑in. ([#936](https://github.com/boxcreate/boxlore/pull/936)) <!-- impact:no-user-impact -->
### Removed
- Migrated Firebase Hosting config to aswin.cx; removed `.agents` Android‑ASO skill and associated documentation. ([#932](https://github.com/boxcreate/boxlore/pull/932)) <!-- impact:user-impact-high -->
- Adaptive `/content/sections/v1` recipe rails and related UI wiring from Home surface ([#942](https://github.com/boxcreate/boxlore/pull/942)) <!-- impact:user-impact-medium -->

## [v0.0.10] - 2026-07-17

### Added
- Recommendation‑driven discovery sections on Home replace time‑of‑day rails; Adaptive Learner debug inspector with genre‑affinity graphs (excluding placeholder Podcast); bounded aggregate summaries (genre affinity, duration, maturity, novelty, recent‑section) for personalization; stale‑while‑revalidate caching for discovery rails with skeleton placeholders. ([#882](https://github.com/boxcreate/boxlore/pull/882)) <!-- impact:user-impact-high+backend-change -->
- Shared ImportLoaderView with morphing animation to success checkmark added for library import flow. ([#883](https://github.com/boxcreate/boxlore/pull/883)) <!-- impact:user-impact-low -->
### Changed
- Visual hierarchy and spacing adjusted for top‑level, child, and nested section headers; daypart rotation enabled via ordering and short‑lived retention of recently visible sections; network loading logic updated to preserve in‑flight section loads and prevent data clearing on empty refresh; Gradle wrapper upgraded and R8 compatibility enabled for release builds. ([#882](https://github.com/boxcreate/boxlore/pull/882)) <!-- impact:user-impact-high+backend-change -->
- Library import flow UI refined with clearer selector, progress, and success screens; cross‑promotion detection widened via additional title patterns, description cues, and improved match scoring; import UI extracted from MainActivity into ui.libraryimport package. ([#883](https://github.com/boxcreate/boxlore/pull/883)) <!-- impact:user-impact-low -->
- Documentation rewritten for recommendation‑system guide covering UI rails, on‑device bandit logic, API contracts, and diagnostics; obsolete recommendation_logic documentation removed. ([#880](https://github.com/boxcreate/boxlore/pull/880)) <!-- impact:no-user-impact -->
### Fixed
- Exclusion of placeholder Podcast genre from on‑device learner calculations fixed; blank gaps under greeting eliminated by painting cached rails immediately. ([#882](https://github.com/boxcreate/boxlore/pull/882)) <!-- impact:user-impact-high+backend-change -->

## [v0.0.9] - 2026-07-16

### Added
- RSS feed subscription with on-device HTTPS fetch (25 MB cap), Podcasting 2.0 parsing, new `rss_episodes` table and deterministic `rss:` IDs; Settings hub replaces Profile screen with six sub‑pages (Appearance, Playback, Downloads, Library, Privacy, About) plus custom HSV accent colour, three background styles, privacy disclosures, public website, reset‑analytics‑ID and deletion‑request flows; backup format v3 and OPML import/export restoring RSS feeds via URL re‑fetch. ([#874](https://github.com/boxcreate/boxlore/pull/874)) <!-- impact:user-impact-high -->
- Global intro/outro trimming with configurable durations and per‑podcast overrides preserving resume and completion semantics; customizable backward and forward seek increments configurable in settings and applied to player UI, notification controls, and Android Auto. ([#875](https://github.com/boxcreate/boxlore/pull/875)) <!-- impact:user-impact-high -->
- Adaptive content orchestration with on-device ranking foundation, versioned model storage, deterministic fallback for Home, discovery, queue, and downloads; isolation of adaptive state from library database and exclusion from device backup. ([#876](https://github.com/boxcreate/boxlore/pull/876)) <!-- impact:user-impact-high -->
- Material 3 Expressive episode details layout with artwork header, cohesive action controls, sticky playback rail component; visible buffering state UI in PlaybackController reflecting network buffering status. ([#877](https://github.com/boxcreate/boxlore/pull/877)) <!-- impact:user-impact-high -->
- In‑app announcement dialog extracted to `InAppAnnouncementDialog` with category layout presets; Play Store install suppression of What’s New / NEW RELEASE GitHub‑download prompts; release announce payloads now support up to five changelog bullets; PostHog events (viewed, dismissed, action) emitted for announcements. ([#870](https://github.com/boxcreate/boxlore/pull/870)) <!-- impact:user-impact-medium -->
### Changed
- Navigation, library and player UI migrated to Material 3 components (ShortNavigationBar, collapsing large titles, revised player sheet settle to eliminate overshoot and keep mini‑player controls responsive); region controls redesign – removal of nudge banners, addition of segmented selector in Settings and region chip on Explore charts header; toolchain and dependencies upgraded to AGP 8.10.1, Kotlin 2.4.0, KSP 2.3.10, Room 2.8.4, rss‑parser 6.1.7. ([#874](https://github.com/boxcreate/boxlore/pull/874)) <!-- impact:user-impact-high -->
- Preserved existing released API behavior as a fallback when adaptive ranking is unavailable. ([#876](https://github.com/boxcreate/boxlore/pull/876)) <!-- impact:user-impact-high -->
- Home feed hierarchy now incorporates adaptive recommendations via HomeFeedRepository, ordering content based on time-of-day; styling of notes, promotions, and recommendation sections in episode details UI updated to match new design. ([#877](https://github.com/boxcreate/boxlore/pull/877)) <!-- impact:user-impact-high -->
- Announcement dialog no longer dismisses on outside tap or system back; only top‑right X or Dismiss button clears it (primary CTA still clears after opening the route). ([#870](https://github.com/boxcreate/boxlore/pull/870)) <!-- impact:user-impact-medium -->
- CI pipeline automatically deletes orphan `release/v*` branches after successful publish via `delete_branch_on_merge` and `prepare_release.py delete‑release‑branch`. ([#871](https://github.com/boxcreate/boxlore/pull/871)) <!-- impact:no-user-impact -->

## [v0.0.8] - 2026-07-12

### Added
- Extended notification workflow with dry‑run mode, Admin UI support, collapse‑key input, sound routing options (chime, announcement, silent), HTML5 preview, live validation of route prefixes and image asset status, persisted notification templates in localStorage, workflow execution list view, updated compose dialog card layout in MainActivity.kt to Material 3 color tokens, custom action button labels, visibility controls, and dispatch readiness lock/unlock state banners. ([#861](https://github.com/boxcreate/boxlore/pull/861))
- Added customizable category badge label parameter to InAppAnnouncementDialog, configurable via FCM payload. ([#862](https://github.com/boxcreate/boxlore/pull/862))
- Added documentation describing the FCM notification fix and migration steps for affected users. ([#863](https://github.com/boxcreate/boxlore/pull/863))
- Implemented Android Auto integration via AutoConnectionManager service, introduced AutoBrowseNode hierarchy for browsing and AutoActionHandler for action handling. ([#865](https://github.com/boxcreate/boxlore/pull/865))
### Changed
- Refactored BoxLoreFcmService.showPushNotification into smaller private methods to reduce cognitive complexity and eliminate duplicate branches. ([#861](https://github.com/boxcreate/boxlore/pull/861))
- Constrained announcement dialog height with headroom and enabled scrolling for overflow content. ([#863](https://github.com/boxcreate/boxlore/pull/863))
### Fixed
- Stabilized artwork image transition in PlayerBottomSheet during drag gestures and updated next‑episode chevron styling and position to use Material 3 accent colors. ([#864](https://github.com/boxcreate/boxlore/pull/864))
- Fixed dark‑mode contrast calculation in Survey using Material surface background fallback, reconciled FCM topic subscriptions on reinstall/device migration by re‑subscribing notification‑enabled podcasts via sentinel file in noBackupFilesDir, and corrected markdown bullet list rendering in announcement dialogs. ([#863](https://github.com/boxcreate/boxlore/pull/863))
- Replaced PostHog survey icon with Campaign megaphone in SurveyIconProvider, fixed rating bar container layout overflow in RatingBarView, and corrected low‑contrast text colors in SurveyPromptView to meet accessibility guidelines. ([#865](https://github.com/boxcreate/boxlore/pull/865))

## [v0.0.7] - 2026-07-12

### Added
- Smart Queue v2: tiered SmartQueueEngine (T0–T4, T3.5) with batch refill, skip memory, region‑aware recommendations, signal‑aware Tier 3 routing, and unified guarded refill path in BoxLorePlaybackService; Queue UX enhancements include drag‑to‑reorder sheet, provenance source labels, undo‑remove snackbar with deferred skip signal, and Lore queue conflict dialog (LORE contextType). ([#853](https://github.com/boxcreate/boxlore/pull/853))
- Responsive Material 3 Expressive full player and polished mini player UI replacing legacy player sheet; playback utilities include inline and fullscreen transcripts, queue previews, show notes, chapters, persisted playback speed, and enhanced loading and motion states. ([#856](https://github.com/boxcreate/boxlore/pull/856))
- Adaptive palette‑driven halo background and layered swipe‑deck UI for Lore with in‑card action row and integrated playback rail; overflow‑aware content layout that hides podcast metadata strip on cards with overflow and presents themed queue‑conflict confirmation; redesigned share sheet offering Copy, Send, and Instagram Story destinations with artwork preview, timestamp support, and generation of square and 9:16 story cards using Material expressive shapes; centralized URL generation in ShareLinkBuilder and surface‑specific routing via ShareTarget with direct Instagram Story intent support and URI permission handling; reusable Lore visual primitives and Android‑canvas rendering support for expressive shapes. ([#858](https://github.com/boxcreate/boxlore/pull/858))
- Implemented loading of reduced‑size curiosity pages with automatic advancement past dismissed cards; introduced a caught‑up state including refresh and history recovery actions. ([#857](https://github.com/boxcreate/boxlore/pull/857))
### Changed
- Integrated SmartQueueEngine with Android Auto via unified refill guard; sleep‑timer now blocks end‑of‑episode refill; AUTO_FILL provenance persisted before append; contextSourceId gates Tier 0 on discovery landings. ([#853](https://github.com/boxcreate/boxlore/pull/853))
- Restored queue handling now deduplicates episodes on restore; click animation handling updated to avoid sticking during rapid input. ([#856](https://github.com/boxcreate/boxlore/pull/856))
- Updated all share entry points (full player, episode details, podcast details) to use new share infrastructure and visual primitives. ([#858](https://github.com/boxcreate/boxlore/pull/858))
- Replaced Lore screen branding assets with refreshed artwork. ([#857](https://github.com/boxcreate/boxlore/pull/857))
### Fixed
- Fixed Tier 0 newest‑sort guard for episodic/news and discovery landing skip; added Tier 3.5 deduplication when Tier 3 already similar; corrected skip‑memory filters and removed auto‑fill skips. ([#853](https://github.com/boxcreate/boxlore/pull/853))
- Fixed marquee overflow‑state feedback loop in expanded player that caused vibrating or double‑rendered metadata. ([#858](https://github.com/boxcreate/boxlore/pull/858))
- Optimized Home tab feed by flattening into lazy staggered‑grid items and pinning hero/Your Shows to reduce recomposition lag; cached skeleton shimmer draw paths and slowed animation to 2.2 s. ([#851](https://github.com/boxcreate/boxlore/pull/851))
### Removed
- Removed QueueManager.refillQueue() and PlaybackRepository.queueRefillCallback in favor of single service‑side refill. ([#853](https://github.com/boxcreate/boxlore/pull/853))
## [v0.0.6] - 2026-07-09
### Added
- Redesigned sleep timer nudge with dynamic-island style SleepTimerPopup component.
- Implemented Firebase App Check token pre-warming and auto-refresh mechanisms.
- Added AI-suggested search redirect chips, anonymous data disclosure, and non-Latin input tips to AI Onboarding.
- Created `SleepTimerConstants` shared module to deduplicate numeric sentinel values.
### Changed
- Isolated the Learn screen playback queue to prevent auto-refill of normal episodes.
### Fixed
- Fixed sleep timer nudge dismissal race condition when hidden mid-confirmation.
- Fixed transition stutters for the Home screen tab navigation.
- Fixed AppCheck telemetry initialization race condition on app startup.
- Redacted sensitive security tokens (App Check and API key) from OkHttp header logcat output.
- Fixed candidate cache type-mixing and delayed news-flag sync issues on podcast import.
- Fixed skip-gate clock skew sensitivity and failure tracking in iTunes charts sync script.
- Fixed Qdrant orphan vectors, multiline CSV description split issues, missing cleanup run costs, and manual embedding budget overrides in the sync workflow.
- Fixed onboarding backup-restore recreation loop and region code alias normalization issues.
- Fixed Qdrant existence checks by chunking requests in batches of 1000 IDs to avoid oversized request payloads.
- Fixed double density scaling in image proxy URLs and stale proxyUrl memoization on density changes.
- Fixed download artwork cleanup race condition and premature deletion of shared podcast artwork.
- Fixed centering of podcast chip in daily curiosity card when title is long.
- Fixed pagination race condition in LearnViewModel when load/refresh is in-flight.
- Fixed unoptimized palette decodes, stale ambient accent colors, and unlocked play button clicks on loading.

## [v0.0.5] - 2026-07-08
### Added
- Migrated backend pipeline to `Xenova/bge-m3` embedding model for superior multilingual search support.
- Configured Qdrant index storage optimizations, including int8 scalar quantization and on-disk indexing configurations.
- Integrated telemetry session tracking and action counters for the Learn screen and bottom navigation.
- Created `toEpisode()` common model mapping helper in `EpisodeMapper.kt` in the `:core:data` module.
- Extracted shared `TrackScreenSession` composable in `LifecycleUtils.kt` to observe screen start/stop lifecycle events across screens without code duplication.
### Changed
- Optimized the Learn screen with centered empty state layouts, swipe history list navigation, and card restoration flow.
- Reduced background artwork blur on the Daily Briefing screen to 8.dp to ensure branding logo remains visible.
- Removed default candidate caps and episode limit counts across background sync and vectorization scripts (`sync-episodes.js`, `vectorize.js`, `vectorize-podcasts.js`).
- De-coupled `medium` column check from sync candidate selection in `sync-episodes.js`.
- Optimized Home screen transitions by deferring heavy below-the-fold content sections during slide navigation.
### Fixed
- Supported region code aliases (`uk`/`ind`) in Settings and Onboarding segmented control country selectors.
- Configured the explore screen semantic search results hero card to display a `"FEATURED RESULT"` badge.
- Recreated the active `MainActivity` immediately upon a successful JSON backup restore to update the active theme, region, and subscriptions reactively.


## [v0.0.4] - 2026-07-05
### Added
- Redesigned explore and curiosity card decks, ambient background color extraction, and pill card controls.
- Reworked downloads screen to feature collapsible sections, single-column lists, multi-select operations, and WorkManager purging.
- Refined mixtape layouts and scoring ranking algorithm.
- Added high-resolution artwork overrides and device pixel density image scaling.
- Added dismissible new episode banners on home feed.
- Added circular wavy play loader component for buffering.
### Fixed
- Fixed navigation backstack-awareness and correct active tab highlights.
- Fixed playback completed status replay bug and scroll stutter optimizations using JankStats.

## [v0.0.3] - 2026-06-27
### Changed
- Bumped version code to 3 (v0.0.3) due to package name changes for Google Play Store release.
- Preserve local show subscription timestamp to avoid resetting DB subscription date to 0 when loading show info details.
- Enable scoring boosts for notification-enabled (+30 pts) and auto-downloads enabled (+60/90 pts) shows.

## [v2.6.7] - 2026-06-23
### Changed
- Rebranded release.

## [v2.6.4] - 2026-06-15
### Added
- Added semantic search support and updated featured show referencing.
- Improved recommendation system and implemented UI optimizations.

## [v2.6.3] - 2026-06-12
### Added
- Material 3 UI refinements and onboarding back navigation improvements.
### Fixed
- Artwork loading fixes.

## [v2.6.2] - 2026-06-03
### Added
- Dynamic skip behavior settings in App Preferences ("Just Skip" vs "Mark Completed & Skip").
- Play All Floating Action Button (FAB) on Subscriptions tab.
- Granular completed episodes filtering across Home feeds, New episodes, and Show details screen.
- Headphone/Bluetooth double-click support mapped to Forward 30s/Back 10s.
- Swipe-away app task cleanup protection during active listening.
### Fixed
- Fixed recommendations caching logic and artwork rendering proxy fallback.

## [v2.6.1] - 2026-05-30
### Added
- Personalized recommendations engine ("For You" tab) based on played episodes, active subscriptions, and genres.
- Expanded recommendations metadata matching matching.
- Center-aligned capsule tab selector navigation.
- France region support with localized trending badges.
- Persistent show list sorting preferences across app launches.
### Fixed
- Resumed position recovery for partially-played audio tracks.

## [v2.5.0] - 2026-05-25
### Added
- Integrated dynamic mixtape queues and library section filters.
- Implemented serialization continuation listening logic.
- Built release-cadence estimator and frequency detail sheet.
- Added fullscreen 16:9 video player, synced live transcripts, clickable chapters, and promotional trailers.
- Implemented edge spelling correction workers and FTS5 SQLite pruning.

## [v2.1.0] - 2026-05-13
### Added
- Massive BoxCast 2.0 interface redesign.
- Subscriptions database backup and restore utility.
- Expanded transparency privacy settings.

## [v1.4.1] - 2026-05-04
### Added
- AI Insights dashboard and interactive telemetry analytics console.
