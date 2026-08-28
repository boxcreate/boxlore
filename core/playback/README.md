# `:core:playback`

## Purpose

Owns playback session control, queue orchestration, smart queue logic, Media3 playback services, the offline download foreground service, and Android Auto browse/collage support. It does not own Room schemas, preference storage, RSS parsing, ranking persistence, smart-download workers, or feature UI.

## Public API

- `PlaybackRepository` exposes player/session operations to app and feature UI (history ports via
  class delegation to `PlaybackHistoryStore`; queue / transport / sleep / history helpers via
  same-package extension API files). `isTransportReady()` reports whether a Media3 controller is connected; the transport API also exposes previous/next and seek (skip forward/back) operations used by the home-screen widget adapter.
- `QueueRepository` and `QueueManager` persist and orchestrate explicit queue operations. `QueueManager` emits `queue_modified` add only after `PlaybackRepository.addToQueue` returns success.
- `PlaybackQueueCoordinator` emits `queue_modified` remove on remove; `undoQueueRemoval` emits a compensating `add` (`source=undo`) so undone removals do not permanently skew analytics.
- `QueueMath`, `QueueSkipMemory`, `SmartQueueEngine`, `SmartQueueSources`, and `MixtapeEngine` implement queue and mixtape logic. `DefaultSmartQueueSources` reads a bounded `getEpisodeWindow` (~200, around the current episode for same-show continuation) — never `getAllNewest` of a mega-feed. Ready local catalogs serve Room pages; not-ready PI shows still union cached extras. Settings → Playback → **Smart queue** (default on) is today’s mixed refill. Turning it off makes refill return same-show continuation only and skip resume/subscription/rec/trending fill. Turning it back on from the queue sheet (empty-state **Turn on**) writes the pref and the playback service refills if the queue is already low.
- `PlaybackMediaIdPolicy`, `PlaybackArtworkResolver`, and `PlaybackSkipPolicy` define session IDs, artwork, and skip behavior.
- `PlaybackMediaIdPolicy.customCacheKey` (via `:core:model` `EpisodeMediaCacheKey`) appends briefing audio `v=` so Media3 does not keep playing a same-day regenerated brief from a stale SimpleCache entry.
- `PlaybackSkipPolicy` also owns intent-aware stale resume: when Settings → Playback → **Restart forgotten episodes** is on (default), implicit plays (queue / mixtape / Smart Queue / casual) soft-expire mid-episode seek after 7 days without `lastPlayedAt`; Jump Back In (`home_hero_resume*`) and History (`library_history`) always seek. Progress is never wiped — seek policy only. Mixtape/SQ still *select* unfinished episodes within the 30-day suggestion band; chrome follows soft-expire (mixtape hides progress / “Xm left”; Smart Queue stamps `resume_stale` → queue label “Starting over”).
- `PlaybackControlSync` keeps UI playback speed / seek sizes aligned with Media3 when a session is cleared or a new queue starts, and sanitizes user-requested speeds before apply/persist.
- `BoxLorePlaybackService` owns one Media3 `CastPlayer` over the existing local `ExoPlayer`. Queue, position, speed, sleep timer, intro/outro policy, completion persistence, smart refill, notification, widgets, and Android Auto therefore follow the active local or Cast route without constructing a second playback graph. Smart Queue submits each refill as one playlist mutation so asynchronous Cast queue sequence numbers cannot race; reconciliation preserves the complete app queue during a brief partial transfer snapshot, then accepts a stable receiver subset after unsupported local-only items are sanitized. Metadata-less remote transitions retry when matching Cast metadata arrives instead of inventing placeholder or “Unknown Episode” records.
- `PlaybackRouteState` mirrors remote-device name/volume into `PlayerState`; `PlaybackRepository.setOutputVolume` controls the active route. Cast-framework lifecycle sync treats asynchronous process restore as an undecided route, refreshes Media3 after reconnect, and rejects stale remote `DeviceInfo` only once an ended or failed session remains disconnected past the recovery grace period. `PlaybackRepository.stopCasting` clears that route immediately while stopping remote playback before terminating the receiver session, preventing hidden TV audio even while Media3's route flag is briefly stale. `BoxLoreCastTransferCallback` removes receiver placeholders without playback URIs before a remote-to-local transfer, preventing ExoPlayer crashes during disconnect. `CastMediaEligibility` admits public HTTP(S) sources and rejects local/private IP literals; `BoxLoreCastMediaItemConverter` validates every effective receiver URL, replaces device-local download URIs with their original stream URL, and keeps that internal URL out of analytics context.
- `HistoryRecommendationLogic`, `AutoVoiceSearchLogic`, `SmartQueueRefillPolicy`, `MixtapeResumePolicy`, `PlaybackEntryPointResolve`, `NightWindowLogic`, and `ListeningHistoryUpsertLogic` are JVM-testable playback helpers. `PlaybackEntryPointResolve` maps fine-grained source-context `entry_point` strings (`home_mixtape`, `learn`/`learn_history`, `briefing`) to coarse `PlaybackEntryPoint` for queue/mixtape policy while the raw string still attributes `playback_*`.
- `AutoArtworkFetchLogic` and `AutoCollageFreshnessLogic` encode Android Auto artwork fetch / collage cache policy for hermetic tests.
- `AutoCollagePrewarmPolicy` and `AutoCollageFolderLogic` encode prewarm throttle and aligned image/key folder inputs for hermetic tests.
- `PlaybackIntroOutroController` manages intro-skip and outro-trim playback lifecycle. Natural completion remains event-driven; ending-trim polling runs only during active playback with a real trim and uses a 500 ms cadence.
- `service.BoxLorePlaybackService`, `service.MediaDownloadService`, and `service.AutoCollageProvider` are manifest-facing services. The download foreground notification refreshes at a five-second cadence to avoid per-second notification wakeups while retaining useful progress.
- `service.SmartQueueRefillCoordinator`, `service.CoilBitmapLoader`, and `service.auto.*` support service internals and Android Auto.
- Android Auto browse artwork:
  - `AutoArtworkRepository` + `AutoArtworkSourceStore` register remote/local sources into an in-memory map immediately and `commit` prefs on a background thread before/while returning `content://…/art|local|collage/…` URIs.
  - `AutoCollageProvider` lazily fetches remote covers with validated HTTPS redirects, lenient image content-types, magic-byte checks, and one retry; folder collage URIs include a `v=` cache-buster so Auto hosts reload when resume/history content changes.
  - `AutoCollagePrewarmer` / `AutoCollageGenerator` / `AutoCollageLayouts` rebuild section collages from content keys (resume episode IDs, queue IDs, subscriptions, …), use a shorter TTL for partial/fallback tiles, and refresh on mark-complete / queue changes via `AutoBrowseLibraryHost.requestAutoCollageRefresh`. MIX/RESUME badge labels prefer app-merged Google Sans Flex with lettering roundness from `boxlore_theme_fast_cache` via `:core:prefs` `FontRoundnessAxis` (no `:core:designsystem` dependency).
  - `AutoArtworkDownloader` is the shared HTTPS fetch path (validated redirects + public-host checks) used by both collage generation and the ContentProvider.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/playback/
  PlaybackRepository.kt              # session core; delegates ListeningHistory* ports
  PlaybackRouteState.kt              # active local/Cast route UI state
  CastMediaItemConverter.kt           # receiver-safe stream mapping + eligibility
  PlaybackHistoryStore.kt            # history ports only; implements history ports
  PlaybackHistoryStoreApi.kt         # non-port history helpers (extensions)
  PlaybackHistoryMappings.kt         # history entity ↔ model mappers
  PlaybackHistoryDeps.kt             # player + data deps for HistoryStore ctors
  PlaybackPowerPolicy.kt             # UI polling, offload, outro, paused-idle policy
  PlaybackSleepController.kt         # sleep timer + late-night nudge
  PlaybackRepositoryQueueApi.kt      # queue extension API
  PlaybackRepositoryTransportApi.kt  # transport / seek / speed extension API (+ [isTransportReady])
  PlaybackRepositoryCastApi.kt       # Cast session / stop extension API and policies
  PlaybackRepositoryHistoryApi.kt    # non-port history extension API
  PlaybackRepositorySleepApi.kt      # sleep / nudge extension API
  PlaybackRepositoryChaptersApi.kt   # chapters / transcript extension API
  QueueManager.kt
  QueueRepository.kt
  ...
  service/
    BoxLorePlaybackService.kt
    MediaDownloadService.kt
    AutoCollageProvider.kt
    SmartQueueRefillCoordinator.kt
    auto/
src/main/java/cx/aswin/boxlore/core/data/service/
  AutoCollageProvider.kt
  BoxLorePlaybackService.kt
  MediaDownloadService.kt
```

`PlaybackRepository` implements `ListeningHistoryPort` / `ListeningHistoryBackupPort` via Kotlin
class delegation to `PlaybackHistoryStore`. Same-package extension files expose the remaining
public one-liner API (`playQueue`, `toggleLike`, `setSleepTimer`, …) so the repository class
stays under detekt LargeClass / TooManyFunctions limits.

Files under `core/data/service` are compatibility stubs for old service class names.

## Dependencies

- Project dependencies: `:core:model`, `:core:network`, `:core:database`, `:core:catalog`, `:core:downloads`, `:core:ranking`, `:core:analytics`, and `:core:prefs`.
- Libraries: Media3 ExoPlayer/Session/UI/Cast, Google Cast framework, Coil, Palette, Gson, OkHttp, coroutines, and AndroidX core.
- Reverse-edge rule: catalog and downloads must not depend back on playback. Downloads launch `MediaDownloadService` through the app-installed launcher port.

## Threading / lifecycle

- `PlaybackRepository`, `QueueRepository`, and `QueueManager` are application-scoped through `AppContainer`.
- `PlaybackRepository` polls MediaController position only while app UI is foregrounded; the playback service remains the sole owner of periodic persistence and telemetry, avoiding duplicate background Room writes while refreshing the exact controller position when UI returns.
- `BoxLorePlaybackService` is a Media3 `MediaLibraryService`; it resolves shared dependencies lazily after application startup and keeps its local and remote players service-scoped so transfers continue while the Activity is absent. A local session that stays paused and backgrounded for 15 minutes saves progress, rechecks eligibility after persistence, and stops the service; resumed playback, cancellation, buffering intent, foreground UI, and Cast sessions bypass that teardown.
- The local ExoPlayer opportunistically enables platform audio offload only when the device can preserve playback-speed behavior. `WAKE_MODE_NETWORK` remains enabled for reliable screen-off streaming.
- Player callbacks run on the main thread; database, artwork, and recommendation work use coroutine scopes and background dispatchers.

## Persistence & identity

- Manifest-facing service class names are system identities.
- Media ID prefixes such as `episode:`, `queue:`, and `learn:` are session and Android Auto contracts.
- SharedPreferences file `boxcast_player` stores playback session flags.
- SharedPreferences file `android_auto_artwork_sources` maps Android Auto artwork content keys to remote HTTPS URLs or sandboxed local paths (identity for Auto collage ContentProvider; do not rename lightly).
- Preference key `device_uuid` is a stable install identifier and must not be logged raw.
- Queue, history, and download rows are persisted by `:core:database` and `:core:downloads`.

## Testing notes

- Unit tests live under `core/playback/src/test`.
- Existing coverage includes Cast eligibility and route-preserving session clear, skip policy (including stale-resume intent × flag × freshness), media ID policy, artwork resolution, control sync, history recommendation filtering, voice search, smart-queue refill policy, mixtape resume policy, night-window logic, listening-history upsert logic, queue math, skip memory, smart queue, playback session mapping, Auto artwork fetch/content-type policy, collage freshness signatures, and Auto artwork source-store durability.
- Service-level tests must install shared dependency holders before exercising service code.

```bash
./gradlew :core:playback:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs playback JVM tests.
- Service and Android Auto behavior are primarily validated by app assembly, emulator/device smoke, and manual checks.
- Dependency guard tracks release runtime dependencies for this module.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:app` README](../../app/README.md)
