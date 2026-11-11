# `:core:playback`

## Purpose

Owns playback: the UI-facing `PlaybackRepository`, the saved queue and Smart Queue, history writes and listening records, the Media3 playback service and its notification, the offline download foreground service, Android Auto browsing and artwork, and Cast.

It does not own the Room schema ([`:core:database`](../database/README.md)), preference storage ([`:core:prefs`](../prefs/README.md)), catalog and cloud sync ([`:core:catalog`](../catalog/README.md)), download workers ([`:core:downloads`](../downloads/README.md)), ranking storage or any Compose UI.

## Public API

### PlaybackRepository and player state

- `PlaybackRepository`: the one UI-scoped playback entry point, built by `AppContainer`. Routes, view models and workers use that instance and never construct a second one.
- `PlayerState`, `PlaybackSession`, `SleepTimerHolder` and `PlaybackLifecycleSignals`: state that screens, widgets and the app shell observe.
- The rest of the API lives in same-package extension files: `PlaybackRepositoryQueueApi.kt`, `…TransportApi.kt` (with `isTransportReady()`), `…CastApi.kt`, `…HistoryApi.kt` and `…SleepApi.kt`.
- `PlaybackRepository` implements `ListeningHistoryPort` and `ListeningHistoryBackupPort` by delegation to `PlaybackHistoryStore`.
- It implements `ActivePlaybackSyncPort`: cloud sync never overwrites the playing episode, and an account switch stops playback and clears the in-memory queue.
- `PlaybackActivationRequest` carries a one-shot start position; `PlaybackIntroOutroController` applies resume, intro skip and outro trim to automatic starts, while explicit positions and route transfers keep theirs.
- `PlaybackSkipPolicy`: with "Restart forgotten episodes" on, implicit plays restart an episode not played for `STALE_RESUME_MS`; Jump Back In and History always resume. Progress is never wiped.
- `PlaybackChaptersTranscriptController`: chapters and transcripts with offline fallbacks; a briefing missing its links gets them from `:core:catalog`'s `BriefingLinks`.
- `PlaybackRouteState` mirrors the active route's device name and volume into `PlayerState`; `PlaybackControlSync` keeps speed and skip lengths aligned with Media3.
- `PlaybackMediaIdPolicy` sets session IDs and cache keys; `PlaybackArtworkMetadata` picks the artwork to save.

### Queue and Smart Queue

- `QueueRepository`: the saved queue and the `QueueSyncPort` for cloud sync. Each mutation is one Room transaction that calls `QueueDao.bumpQueueVersion` (version, device, dirty mark, removal tombstones).
- `QueueManager` orchestrates queues for screens, such as `playContextEpisodes` for a visible list like New Episodes.
- The queue holds at most `PlaybackRepository.QUEUE_LIMIT` (50) episodes. `addToQueue` returns false past the limit or for an episode already queued; screens read the constant to say which.
- "Play next" on a queued episode moves it after the current item (`QueueMath.placeNext`).
- `QueueShowPolicy`: every entry plays under its own show; another show's fields only fill gaps. An entry with no show plays under "Unknown Podcast".
- `PlaybackQueueCoordinator`, `PlaybackTransportHelper` and `PlaybackMediaControllerBridge` build media items, follow transitions and save the current entry's show.
- `SmartQueueEngine`, `SmartQueueSources`, `SmartQueueRefillPolicy`, `QueueSkipMemory`, `MixtapeEngine` and `MixtapeResumePolicy` choose refill and mixtape episodes.
- Same-show continuation reads a bounded window around the current episode, never a whole feed; `SmartQueueEngine.SHOW_BINGE_SOURCES` lists sources allowed deep same-show runs. Trailers (`Episode.isTrailer`) and episodes without audio are never refilled.
- With the Smart Queue setting off, refill offers same-show continuation only; turning it on again refills at once when the queue is low.
- `SameShowContinuation*`: the optional "more from this show" offer, capped at `MAX_CONTINUATION_OFFER`.
- Smart Queue refill is owned by `BoxLorePlaybackService` (`service.SmartQueueRefillCoordinator`, `service.ContextQueueContinuationCoordinator`); screens and workers never refill.
- A context queue refills only after its final episode completes and is saved, and ignores a late result once the session is paused, sleeping or replaced.

### History and listening records

- `PlaybackHistoryStore` (mappers in `PlaybackHistoryMappings.kt`): history reads and writes, likes, completion, and removal with Undo.
- `PlaybackProgressCoordinator` and `PlaybackProgressPersistencePolicy`: sequence-stamped saves, so an older write never replaces a later seek or restart.
- `PlaybackSessionRestoreHelper`, `PlaybackControllerStatePolicy` and `logic.PlaybackHistorySeedPolicy`: restores never replace Room's progress with an empty controller's, and missing rows are seeded from service metadata.
- `IdleSessionHandoff`: a newer cloud session takes the idle mini-player only when it is empty or the session is a different, more recently played episode.
- `observeInsights`, `observeFullHistoryTimeline`, `observeListeningByDay` and `observePlayedShows` read every stored row, not the screens' latest 300, at most once a second (`throttleLatest`).
- `PlaybackFinishes`: when this phone last played an episode to the end; the app persists `onRecorded` for Home's survey and review prompts.

#### Listening records

- `PlaybackTelemetrySession` records one listen from sound starting until it pauses, finishes, switches episode, stops or the service closes; `ListenTally` counts heard time and `service.ListenEndRule` decides the end.
- `ListeningRecordWriter` saves each timed play with its listening time, play source, output (`service.ListeningOutputRoute`), release date and listening device ID. Sessions stay whole for 400 days (`ListeningSessionRecordLogic.RETENTION_DAYS`), then fold into daily rollups.
- Listen, error and interruption events (`ListenNoteKeeper`, `ListenInterruptions`, `PlaybackErrorOnce`, `FirstPlayClaim`) are described in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

### Playback service and notification

- `service.BoxLorePlaybackService`: the Media3 `MediaLibraryService` owning the players, progress saves, telemetry, completions, Smart Queue refill, the sleep timer and the notification.
- Session buttons come from one list, `playbackButtonPreferences` (`service.PlaybackServicePlayerFactory`): rewind in `SLOT_BACK`, forward in `SLOT_FORWARD`, then Mark played and Next episode in `SLOT_OVERFLOW`.
- Next episode (`AutoBrowseContract.COMMAND_SKIP_NEXT`) runs `seekToNext` and is listed only while a next item exists.
- Headset and car next/previous keys seek forward/back (`AutoBrowseLibraryCallback.onMediaButtonEvent`); they never change episode.
- `service.ForegroundSafeNotificationProvider` wraps Media3's `DefaultMediaNotificationProvider`: when Android refuses a late foreground start, it posts an ordinary notification instead of crashing.
- The service guards `PendingIntent` creation against `SecurityException` and stops itself when the player cannot be created.
- `PlaybackErrorSkipGuard` skips a failed episode only while the listener wants playback; after `DEFAULT_MAX_CONSECUTIVE_FAILURES` in a row it pauses on that episode and keeps the queue.
- `service.MediaDownloadService`: the Media3 `DownloadService` for offline downloads. It schedules only below API 31, and a failed start still calls `startForeground` before stopping.
- `:core:downloads` starts it through the `DownloadServiceLauncher` port that `AppContainer` installs.

### Android Auto

- `service.auto.AutoBrowseLibraryCallback`, `AutoBrowseTreeBuilder`, `AutoMediaItemFactory` and `AutoBrowseContract`: browse tree, media IDs and custom commands.
- `AutoMediaResolver`, `AutoPlaybackResumptionHandler` and `AutoVoiceSearchHandler`, each with a pure policy or logic object: playable items, `onPlaybackResumption` and voice search.
- `AutoBrowseControllerMatcher` tells car controllers from Bluetooth AVRCP and system UI; connection events count only in car mode or on Android Automotive (`CarConnectionState`).
- `labelCarPlay` names a play `android_auto_*` only when Auto's controller asked for it.
- `service.AutoCollageProvider` serves `content://` artwork and collages (`AutoCollagePrewarmer`, `AutoCollageGenerator`, `AutoCollageLayouts`), fetched through `AutoArtworkDownloader` with validated HTTPS redirects.
- Collage links are display-only; saved artwork always comes from `PlaybackArtworkMetadata.savedArtworkUrl`.
- The browse root title is `:core:catalog`'s `auto_app_name`; the generic tile spells `boxlore` in lowercase.

### Cast

- `BoxLorePlaybackService` keeps one Media3 `CastPlayer` over the local `ExoPlayer`, so queue, sleep, refill, notification, widgets and Auto follow the active route without a second playback graph.
- `CastMediaEligibility` admits public HTTP(S) sources only; `BoxLoreCastMediaItemConverter` swaps local download URIs for the stream URL.
- `CastQueueSnapshotPolicy` keeps the app queue through a brief partial receiver snapshot; `service.BoxLoreCastTransferCallback` drops URI-less placeholders before a transfer back.
- `stopCasting` clears the route at once and stops remote playback before ending the receiver session.

### Artwork decoding

- `service.CoilBitmapLoader` decodes and loads on IO in the service scope, returning a cancellable future at once; it tries each item's alternative sources with a per-fetch timeout.
- `service.RecoveringBitmapLoader` keeps one successful or in-flight request, expires failures after a short cooldown and retries cancelled work immediately.

### Episodes under several IDs

An episode can be stored under several IDs; the rules are in [`docs/EPISODE_IDS.md` §5](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone). Playback goes through `:core:database`'s `EpisodeListenerStore`:

- The media ID stays the one playback started with and is never translated mid-playback.
- Progress goes to the owner row; likes and completion write an absolute target to every trusted ID.
- Start positions, lists, Smart Queue sources and Android Auto read the projected state, so an episode counts once.
- The queue holds an episode once across its IDs (`QueueAliasPolicy`); removals record every ID.
- Media items play the bytes of the ID holding the download (`EpisodeListenerStore.downloadHolders`).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/playback/
  PlaybackRepository*.kt          # repository and its extension API files
  Playback*.kt                    # history, progress, restore, telemetry, policies, controllers
  Listen*.kt, Listening*.kt       # listens and listening records
  Queue*.kt, SmartQueue*.kt, Mixtape*.kt, SameShowContinuation*.kt
  Cast*.kt, Auto*Logic.kt, Auto*Policy.kt, IdleSessionHandoff.kt, FirstPlayClaim.kt, …
  logic/                          # history seed policy and sources
  service/                        # services, provider, refill, notification, bitmap loaders
    auto/                         # Android Auto browse, resolution, resumption, voice search
src/main/java/cx/aswin/boxlore/core/data/service/   # stubs for legacy service names
```

## Dependencies

- `api`: `:core:model`, `:core:network`, `:core:database`, `:core:catalog`, `:core:domain`, and Media3 ExoPlayer (`:app` references `MediaDownloadService`, a `DownloadService`).
- `implementation`: `:core:downloads`, `:core:ranking`, `:core:analytics`, `:core:prefs`.
- Libraries: Media3 Session, UI and Cast, Google Cast framework, Coil, Palette, Gson, OkHttp, coroutines with Guava interop, AndroidX core.
- Forbidden: catalog and downloads never depend on playback. This module depends on no feature module and not on `:core:designsystem`.

## Threading / lifecycle

- `AppContainer` builds the graph once: DB → `PodcastRepository` → `QueueRepository` → `PlaybackRepository` → `QueueManager`.
- Player and controller callbacks run on the main thread. `restoreLastSession` and transport calls hop to `PlaybackThreadPolicy.mainDispatcher`; database, artwork and recommendation work runs on background dispatchers.
- `PlaybackRepository` polls the controller position only while app UI is in the foreground; `refreshProgressOnce()` publishes it once on request, and only while the player still holds the episode the state names, which home-screen widgets use while an episode plays in the background. The service alone saves active-session progress and telemetry.
- `PlaybackMediaControllerHandle.awaitController()` returns null on a rejected connection and reconnects with growing delays, at most `MediaControllerReconnectPolicy.MAX_ATTEMPTS` times.
- `BoxLorePlaybackService` resolves shared dependencies lazily after app start; its players are service-scoped, so playback and transfers continue without an Activity.
- Idle handoff: a local session paused in the background for `PlaybackPowerPolicy.PAUSED_IDLE_TIMEOUT_MS` saves progress, rechecks eligibility and stops the service. Playback, buffering intent, foreground UI and Cast skip that teardown.
- The local player keeps `WAKE_MODE_NETWORK` for screen-off streaming and enables audio offload only where playback speed still works.
- Natural completion is event-driven; outro polling runs only during active playback with a real trim.

## Persistence & identity

Never rename these:

- `BoxLorePlaybackService`, `MediaDownloadService` and `AutoCollageProvider` in `cx.aswin.boxlore.core.playback.service`, and their stubs in `cx.aswin.boxlore.core.data.service` ([`ARCHITECTURE.md`](../../ARCHITECTURE.md#services--providers)).
- Media ID prefixes `episode:`, `queue:` and `learn:` (`QueueMath`, `AutoBrowseContract`), and Auto browse prefixes `subscription:`, `discover_genre:` and `discover_curated_`.
- Media item extras `boxlore_podcast_id` and `boxlore_artwork_sources`.
- `customCacheKey` is the episode ID of the download holder (`EpisodeMediaCacheKey`); briefing audio adds its `v=` version. Briefing episode IDs stay `briefing_*`.
- SharedPreferences `boxlore_player` (copied from legacy `boxcast_player` by `PrefsFileMigrator`): `player_dismissed`, `last_sleep_prompt_window_id` and `device_uuid`, which is never logged raw.
- `boxlore_listen_note` (the listen in progress) and `boxlore_install_milestones` (`first_play_claimed`, `updated_from_older_build`) are excluded from backups and device transfers.
- `queue_skip_memory` (`entries`) for `QueueSkipMemory`, and `android_auto_artwork_sources` for Auto artwork content keys.
- Notification channel `download_channel` for downloads; the playback notification uses Media3's default channel.
- Queue, history, listening and download rows belong to `:core:database` and `:core:downloads`; other identities are in [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage).

## Testing notes

Tests live in `core/playback/src/test` (JUnit 5, Robolectric for Room and SharedPreferences, Mockito, no MockK); `EpisodeAliasFixtures` builds episodes under several IDs. Install the shared dependency holders before exercising service code.

| Area | Test classes |
| :--- | :--- |
| Queue | `QueueRepositoryTest`, `QueueManagerPlaybackTest`, `PlaybackQueueCoordinatorTest`, `QueueShowPolicyTest`, `QueueEntryShowTest` |
| Smart Queue, mixtapes | `SmartQueueEngineTest`, `service/SmartQueueRefillCoordinatorTest`, `service/ContextQueueContinuationCoordinatorTest`, `SameShowContinuation*Test`, `MixtapeEngine*Test` |
| Start position, skips | `PlaybackIntroOutroControllerTest`, `PlaybackSkipPolicyTest`, `PlaybackControllerStatePolicyTest`, `PlaybackProgressPersistencePolicyTest` |
| History, insights | `HistoryBeyondScreenLimitTest`, `ListeningInsightsLogicTest`, `IdleSessionHandoffTest`, `PlaybackSessionRestoreHelperTest` |
| Several IDs | `EpisodeAlias*Test`, `QueueAliasPolicyTest`, `SwitchedShowPlaybackWritesTest`, `service/auto/AutoEpisodeAlias*Test` |
| Listens | `ListenTallyTest`, `PlaybackTelemetrySessionListenTest`, `ListenInterruptionsTest`, `ListeningSessionDetailsTest`, `FirstPlayClaimTest` |
| Service, notification | `service/PlaybackButtonSlotsTest`, `service/ForegroundSafeNotificationProviderTest`, `service/BoxLorePlaybackServiceFallbackTest`, `service/MediaDownloadServiceTest`, `PlaybackErrorSkipGuardTest` |
| Android Auto | `service/auto/*Test`, `AutoCollage*Test`, `service/AutoArtwork*Test` |
| Cast | `CastMediaEligibilityTest`, `CastMediaItemConverterTest`, `CastPlaybackPolicyTest`, `service/CastTransferPolicyTest` |
| Artwork | `service/CoilBitmapLoaderTest`, `service/RecoveringBitmapLoaderTest`, `PlaybackArtworkMetadataTest`, `PlaybackArtworkResolverTest` |

Robolectric can't read the system session's actions, so Media3's slot handling needs a phone check.

```bash
./gradlew :core:playback:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests, merges their Kover coverage and checks `:core:playback:dependencyGuard` against `core/playback/dependencies/`.
- `:core:testing` architecture guards check this README and the dependency rules.
- Service, notification, Cast and Android Auto behaviour beyond these tests needs a device check.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:database` README](../database/README.md)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:app` README](../../app/README.md)
