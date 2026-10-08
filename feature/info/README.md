# `:feature:info`

## Purpose

Owns podcast and episode detail presentation: subscribe actions, RSS refresh actions, related and similar content, cross-promotion cards, offline/progress display, and detail-screen layout. It does not own catalog persistence, RSS parsing, download cache behavior, playback services, or app navigation registration.

## Public API

Disclosure recognizes canonical RSS IDs even with stale source metadata and uses localizable string resources. ViewModel notification actions are tested through their JVM logic helpers, including refused activation, URL-bound disclosure, and independent download settings. Deferred disclosure and system-permission actions retain the confirmed feed URL; persistence reports its actual activation result.

- RSS-only shows expose the same notification and auto-download toolbar controls as catalog shows. Both the bell and the auto-download notice’s **Turn on notifications** action open `RssNotificationDisclosureDialog` before Android permission handling: users confirm that turning notifications on will make the feed link public. The dialog advises keeping notifications off for private or premium feeds and explains that listening, refresh and downloads remain available. Cancelling leaves notifications off; auto-download works independently. Existing imported shows use the same disclosure.

- `PodcastInfoScreen` and `PodcastInfoViewModel`. Long-pressing an episode enters multi-selection; the floating toolbar can download, mark completed (or mark unplayed when every selected episode is already complete), play, or append selected episodes to the queue. Its overflow can select only cards currently visible on screen, or fetch up to 100 show episodes for Select all / Select older / Select newer; fetched episode metadata is retained only while selection is active. Episode sorting remains available. Subscribed shows expose **Pin to Home screen** / **Unpin from Home screen** and **Change tag / genre** in the overflow menu (or by tapping the genre chip directly) to open `PodcastGenreEditSheet` with IME keyboard support, real-time genre search pairing names with their icons, cross-suggestions from existing subscription folder names, and a 31-icon Material Rounded palette. Unsubscribing clears custom genre tags and icons back to default. Tapping the download action on an already-downloaded episode prompts with a Material 3 confirmation dialog (`RemoveDownloadConfirmationDialog`) before removing the download. Episode play taps and multi-selection play pass `podcast_detail` as the playback entry point context (allowing same-show continuation in Smart Queue) while preserving any spotlight route for video telemetry. Show notification toggles feature system permission awareness: when show notifications are active but device notifications are disabled, `EpisodeToolbar` renders the active bell icon in an error-container red accent with a red badge dot; tapping the icon reveals `ToolbarWarning.SYSTEM_PERMISSION_BLOCKED` explaining the status; the "Turn On" action prompts for `POST_NOTIFICATIONS` runtime permission when available, and otherwise opens system settings.
- `EpisodeInfoScreen` and `EpisodeInfoViewModel` (similar episodes use prefs `content_languages` + region). The artwork-first Material 3 layout keeps a visible blurred cover backdrop within shorter clipped bounds, uses theme-based tonal surfaces and plain Back/Share icons with 48dp touch targets, and limits episode titles to three lines with an overflow-only arrow inside a small translucent circle at the trailing end of the third line. The first two title lines retain the full width; expanded titles keep all text visible and place the collapse arrow at the end. The generic Episode toolbar label is omitted; the episode title still appears when scrolled. The hero podcast name uses one line with ellipsis; Read more / Show less use visible filled-tonal pills. Like, Download and Queue use connected tonal toggle buttons with rounded outer ends, small gaps, and rounded selected states; a wider filled Play button completes the same 56dp-high row; listening progress and its time summary sit below the action group; Bonus/Trailer/Video tags sit inside the artwork at the top-start corner as compact opaque tonal pills with a 12dp inset; paired tags wrap for larger text or longer translations. Date, duration and season/episode numbers use middle-dot separators below the title in a single-line row. The completion pill stays fixed at the logical end; metadata takes the remaining width and marquees only when it overflows, with a 2.5-second initial pause, 2-second pauses between passes, and a 24dp/s scroll speed. Short metadata stays still, missing values do not leave stray separators, and the complete metadata remains available to accessibility. The pill label stays on one line, with ellipsis only when an unusually long translation or enlarged text exceeds the available width. The pill shows Mark played before completion and Played after completion; tapping it toggles the existing completion action, with Mark unplayed exposed to accessibility. Its guidance appears next to the metadata when requested. Chapters appear above About, use remote Podcast 2.0 chapters when valid, and fall back to parsed description chapters. Expanded About preserves publisher formatting and timestamp seeking. Resource pills size to their labels and pack independently into two horizontally scrolling rows; one link uses one row and two links stack one per row. Links with recognized brand icons come first, followed by generic links, retaining publisher order within each group. Priority fills top then bottom before moving across (first/second in the first pair, third/fourth in the next). Recognition uses the same domain matching as the icon and tint, so lookalike hosts do not gain priority. Recognized social profiles with a parsed username show only that username beside the platform icon; accessibility still names the profile and platform. Root pages, posts without a parsed username, unknown platforms, and video/podcast/support links retain descriptive destination or publisher-context labels. Bubbles use solid platform-tinted surfaces with contrast-adjusted logos for recognized social, video, podcast and support domains (with Material purpose icons as the fallback). TikTok uses a cyan seed with a softly tinted bubble and a contrast-adjusted cyan icon; X and Threads retain monochrome theme-foreground icons rather than inventing a brand hue. Cross-promotion cards appear only after the shared detector and resolver identify another show. Try these episodes reuses the Home `FeedMediaCard` expressive poster treatment with episode-title-only text and duration on the artwork. More from keeps the compact artwork/title/date/duration row cards and lists the five latest distinct episodes vertically, excluding the current episode. Its heading is capped at two lines with ellipsis and has an arrow on a Material 3 Cookie4Sided shape; this and the Explore podcast button below the list both open the same show. The section shares the page scroll and retains related-content scroll and click analytics. Both retain show identity for accessibility. Playback, download, queue, navigation, recommendation ranking and analytics callbacks remain owned by their existing layers. Tapping download on a downloaded episode prompts for confirmation before removing local media.
- `InfoViewModelAssembler` for podcast and episode ViewModel factories.
- `InfoListeningProgressItem` and supporting components/sections for detail UI. Recommendation rails (`EpisodeRecommendationSection`) and episode search overlay (`PodcastInfoSearchOverlay`) protect item lists against duplicate keys using `LazyListKeyPolicy`.
- Logic helpers under `logic/` and component-level formatters used by tests. `EpisodeArtworkLogic` is the Podcast Info row/sheet artwork URL (episode art, else `podcastImageUrl`, else the show image) so Missing episodes? extras without item itunes:image still show cover art.
- Detail UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.
- `EpisodeInfoSeekLogic` builds the progress-save payload when seeking an episode that is not the current player item.
- For PI-owned shows whose local catalog is **not ready**, unsubscribed Podcast Info may still show **Missing episodes?** (opt-in extras path). The confirm dialog explains that publisher-feed access is allowed once and refreshes on future visits, while subscribing automatically keeps the latest episodes up to date. Subscribe kicks `SubscriptionForegroundSync.requestCatalogIngest` on the application scope so leaving Podcast Info does not cancel the publisher-feed persist (no PI-vs-feed extras compare). The screen only waits on `catalogIngestFinished` to remount if it is still open. Unsubscribe stamps a 14-day Room TTL; that catalog is **not** ready, so the page uses Podcast Index again (a new PI episode still appears). Resubscribe clears the TTL. Once `LocalEpisodeCatalogPort.isReady` is true, the pill is hidden and the episode list comes from Room only. Successful publisher-feed refreshes treat that feed's newest row as authoritative, including same-date/different-id and older-than-PI cross-promo cases. Pull-to-refresh: true RSS uses `refreshCatalog`; subscribed non-pure RSS shows route to a direct-feed catalog refresh via `LocalEpisodeCatalogPort.refresh` (preserving notification, auto-download, skip overrides, artwork fallback, and sort preferences); not-ready opted-in unsubscribed PI still runs the extras refresh; other unsubscribed PI shows reload from `PodcastRepository` (Room or PI). NEW badges still follow newer `publishedDate` only.

- Show auto-download can be enabled with notifications off or system notification permission denied. Turning the bell off preserves auto-download; the cloud-dirty preference and activation boundary are persisted by the catalog repository. Pure RSS auto-download eligibility remains unchanged.

- Explicit subscribed-show pull-to-refresh uses `MANUAL` to bypass the automatic six-hour cooldown. Show auto-download remains independent of notification permission and never grants background polling consent; without show push notifications or explicit polling consent, discovery occurs during normal foreground refreshes.

- Enabling show auto-download while both show notifications and background checks are off displays a neutral, optional notice explaining app-open discovery. Auto-download is enabled immediately; **Turn on notifications** follows the existing system-permission flow and enables only show notifications, preserving any later auto-download choice. Background checks remain opt-in. JVM toggle tests cover all combinations of the two discovery paths and disabling auto-download.

- Auto-download toggles preserve an existing system notification-permission warning instead of clearing or replacing it. `enableShowNotifications` delegates its persistence/state/analytics action to the hermetic `logic/PodcastNotificationActions` helper; after a suspended save it applies the repository’s actual activation result to the latest matching show, preserving newer auto-download choices. Both toggle actions update only their own preference on the latest state so a concurrent save cannot restore the other toggle’s older value. JVM tests cover consent persistence, event order, download independence, changes during saves, and failed/no-op actions. The warning banner's header and action are separate composables to keep rendering complexity bounded.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/info/
  EpisodeInfoScreen.kt
  EpisodeInfoViewModel.kt
  InfoListeningProgressItem.kt
  InfoViewModelAssembler.kt
  PodcastInfoScreen.kt
  PodcastInfoViewModel.kt
  PodcastInfoSupplementSupport.kt
  components/
  logic/
    EpisodeInfoSeekLogic.kt
    EpisodeInfoNotesLoader.kt
    …
  sections/
```

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:rss`, and `:core:prefs`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, Palette, smooth corner rect, coroutines, Kotlin serialization, Turbine, and Compose Material.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must use ports rather than direct `BoxLoreDatabase` access.

## Threading / lifecycle

- ViewModels are scoped by app navigation. Post-subscribe local-catalog ingest is owned by application-scoped `SubscriptionForegroundSync`, not the Podcast Info ViewModel. Late page-load, metadata, and direct-feed responses preserve the current subscription state, so work started while subscribed cannot undo a completed unsubscribe.
- Catalog, local catalog, offline lookup, RSS, download, playback, and analytics dependencies are supplied by app wiring.
- Podcast/episode info emits glossary analytics via `:core:analytics` façades (no PostHog direct).
- UI runs on the main thread; refresh, subscribe, lookup, and related-content work use suspend APIs.
- `EpisodeInfoNotesLoader` parses publisher HTML on a background dispatcher, fetches chapters and promotion matches independently, and cancels superseded requests. A generation guard also rejects late responses during metadata refreshes for the same episode. Invalid/empty remote chapters retain the description fallback; promotion failures do not block description or chapters.

## Persistence & identity

- This module owns no storage files or stable keys.
- Pin reads and writes `home_pinned_podcast_ids` through `:core:prefs` (`toggleHomePinnedPodcastId`). At-capacity copy comes from `HomePinnedShows.capacityUserMessage()`. Notification permission prompt history is tracked via `has_requested_notification_permission` in `BoxcastPrefs`.
- Podcast, episode, RSS, download, and listening-progress identities come from core modules.
- App navigation owns route patterns and deep links.

## Testing notes

- Unit tests live under `feature/info/src/test`.
- Existing coverage includes assembler behavior, catalog port behavior and errors, offline merge logic, listening-progress mapping, duration formatting, metadata chip logic, feed grouping, selection range/order logic, toolbar logic, HTML stripping, podcast info ViewModel logic, pull-to-refresh target (RSS vs subscribed direct feed vs opted-in direct feed), subscription property/toggle preservation across pull-to-refresh and late API enrichment, episode-supplement merge/eligibility, episode list artwork fallback, and `PodcastInfoSupplementSupport` refresh / PI-only baseline / auto-opt-in / search union. Home pin persistence, capacity, and unsubscribe cleanup are covered in `:core:prefs` (`HomePinnedShowsTest`, `UserPreferencesRepositoryTest`) rather than constructing `PodcastInfoViewModel`.
- `MoreFromEpisodeSelectionTest` covers newest-first ordering, current-episode exclusion before the five-item limit, duplicate IDs, stable ordering for equal dates, and short/empty catalogs.
- Catalog HTTP paths are covered in `:core:catalog` tests, including shared show-notes parsing and promotion matching.
- Link labels use the shared destination-title filter before applying publisher action text or translated labels. Generic platform names fall back to a real profile handle or an action naming the platform, rather than repeating the platform as a username. `EpisodeLinkLabelTest` covers platform aliases, generic action labels, profile/root fallbacks, and preservation of specific publisher wording.
- Cross-promotion uses a compact horizontal card on an opaque `tertiaryContainer` with matching `onTertiaryContainer` text: 96dp artwork, a confidence-aware featured-show label, a bold two-line show title, one-line publisher, and an Explore podcast pill using `tertiary`/`onTertiary`. Cached Material 3 Puffy and Cookie4Sided shapes sit behind the content at 4% and 2.5% opacity, clipped at opposite card edges and mirrored for RTL. These theme roles distinguish the featured show from neutral episode content and stay consistent in light and dark modes. The entire card is a single accessible button opening the resolved show; the pill belongs to that same action. Content height can grow for larger text and translated labels, and the full title remains available to accessibility. The show name is not repeated in a second explanatory sentence.
- `EpisodeInfoNotesLoaderTest` covers description fallback, independent failures, remote sorting/validation, cancellation, stale episode results, and same-episode metadata refreshes. `EpisodeChapterTimeTest` covers minute/hour boundaries. `EpisodeLinkRowsTest` covers single-link, two-link and uneven multi-link row placement, stable branded-first top/bottom priority, domain aliases, and lookalike-host/email fallbacks. `EpisodeLinkLabelTest` covers compact parsed profile usernames, descriptive fallback labels, publisher action text, and unknown/lookalike platform guards. `EpisodeLinkBrandIconTest` covers recognized platform domains, aliases and subdomains, and prevents lookalike hosts from receiving a platform logo. `EpisodeLinkVectorTest` protects Discord’s arc geometry from compact SVG flags that Android misreads. `EpisodeLinkPaletteTest` checks opaque containers and readable icon/text contrast in both themes, TikTok’s cyan treatment, and the monochrome X/Threads fallback. Brand-vector provenance and CC0 terms are recorded under `licenses/`.

```bash
./gradlew :feature:info:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Info JVM tests with the project suite.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database usage in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../../core/catalog/README.md)
- [`:app` README](../../app/README.md)
