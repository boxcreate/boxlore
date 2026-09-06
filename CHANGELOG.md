# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **AI-generated summaries:** May contain mistakes. Verify details against the linked pull requests.

## [Unreleased]

### Added
- Added interactive same-show continuation banner with expandable episode preview and quick-refill action in the player queue sheet. ([#1019](https://github.com/boxcreate/boxlore/pull/1019)) <!-- impact:user-impact-critical --> <!-- copy:locked -->
### Changed
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
- Preserved notification and auto-download toggles on Podcast Info pull-to-refresh and routed subscribed shows to direct publisher feed refresh. ([#1016](https://github.com/boxcreate/boxlore/issues/1016)) ([#1022](https://github.com/boxcreate/boxlore/pull/1022)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Fixed latent `notificationsEnabled` and `autoDownloadEnabled` mapping in RSS repository entity conversion. ([#1022](https://github.com/boxcreate/boxlore/pull/1022)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Prevent SecurityException crashes caused by system PendingIntent UID quota exhaustion in FCM notifications and Media3 playback session setup (#1025). ([#1034](https://github.com/boxcreate/boxlore/pull/1034)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Preserved show name metadata during Android Auto playback and resolved session restore reversions when replaying completed episodes (#1035). ([#1036](https://github.com/boxcreate/boxlore/pull/1036)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Fixed Android Auto reconnection failure by registering `MediaButtonReceiver` and implementing `onPlaybackResumption` with queue hydration and repeat session recovery (#1038). ([#1045](https://github.com/boxcreate/boxlore/pull/1045)) <!-- impact:user-impact-high --> <!-- copy:locked -->
- Adapted the Cast button in the expanded player sheet to respect dark and light themes and match the Share button theming (#1037). ([#1039](https://github.com/boxcreate/boxlore/pull/1039)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Handled broken pipe and network disconnect exceptions safely during Android Auto artwork streaming (#1027). ([#1041](https://github.com/boxcreate/boxlore/pull/1041)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Enforce `MediaController` main-thread affinity in `PlaybackRepository` to prevent `IllegalStateException` on background session restoration. ([#1042](https://github.com/boxcreate/boxlore/pull/1042)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Protect `LazyColumn` and `LazyRow` lists against duplicate keys to prevent `IllegalArgumentException` in recommendation and explore feeds. ([#1043](https://github.com/boxcreate/boxlore/pull/1043)) <!-- impact:user-impact-medium --> <!-- copy:locked -->
- Fixed Video Spotlight card tap animation in `FeaturedVideoPodcastsShowcase` by placing `expressiveClickable` before shadow, border, and surface background (#1031). ([#1046](https://github.com/boxcreate/boxlore/pull/1046)) <!-- impact:user-impact-medium --> <!-- copy:locked -->

<!-- readme-copy:start pr=1019
### Critical
- Fixed an issue where the queue would skip upcoming episodes of the show you were listening to.
- Added a convenient queue banner to preview and add upcoming episodes of a show when played from recommendations.
readme-copy:end pr=1019 -->

<!-- readme-copy:start pr=1022
### Fixes
- Pulling down to refresh on a podcast's page now checks for newly released episodes without resetting your notification or download settings.
readme-copy:end pr=1022 -->

<!-- readme-copy:start pr=1030
### Critical
- Fixed an issue where tapping recommended episodes from the Home screen could cause boxlore to unexpectedly close.
readme-copy:end pr=1030 -->

<!-- readme-copy:start pr=1033
### Critical
- Fixed an issue on Android 14 and newer where background auto-downloads could crash or fail to start when new episode notifications arrived.
- Fixed storage cleanup so deleting or replacing downloaded episodes properly reclaims device disk space.
- Improved background auto-download reliability so new episodes are ready for offline listening as soon as notifications arrive.
readme-copy:end pr=1033 -->

<!-- readme-copy:start pr=1034
### Fixes
- Resolved an issue where receiving many episode notifications over time could cause audio playback to fail to start.
readme-copy:end pr=1034 -->

<!-- readme-copy:start pr=1036
### Fixes
- Fixed an issue where episodes played from Android Auto showed missing show names and could cause playback to resume an older session on restart.
readme-copy:end pr=1036 -->

<!-- readme-copy:start pr=1039
### Fixes
- Fixed the Cast icon in the full-screen player so it adapts to light and dark themes and matches the Share button.
readme-copy:end pr=1039 -->

<!-- readme-copy:start pr=1041
### Fixes
- Fixed an intermittent crash that could occur when disconnecting from Android Auto during artwork loading.
readme-copy:end pr=1041 -->

<!-- readme-copy:start pr=1042
### Improvements
- Fixed background crashes when restoring playback sessions or updating widgets.
readme-copy:end pr=1042 -->

<!-- readme-copy:start pr=1043
### Fixes
- Fixed crashes when viewing search results and recommendation shelves with duplicate items.
readme-copy:end pr=1043 -->

<!-- readme-copy:start pr=1044
### Improvements
- Resolved out-of-memory errors during large podcast pagination and feed parsing by streaming responses and bounding in-memory cache sizes.
readme-copy:end pr=1044 -->

<!-- readme-copy:start pr=1045
### Fixes
- Android Auto now seamlessly resumes your last played podcast and queue when reconnecting in your vehicle instead of showing an error screen.
readme-copy:end pr=1045 -->

<!-- readme-copy:start pr=1046
### Fixes
- Fixed Video Spotlight cards on the Home screen so their dropshadow and border scale smoothly with the artwork during tap animation instead of revealing a static outer outline.
readme-copy:end pr=1046 -->
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
