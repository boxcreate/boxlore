# `:core:downloads`

## Purpose

Owns offline downloads: Media3 offline cache access, Room download state, manual downloads, Smart Downloads, per-show auto-downloads, bandwidth throttling, offline chapters and transcripts, and the WorkManager workers behind them.

It does not own the playback or download services, preference storage, catalog retrieval, ranking storage or feature UI.

## Public API

Downloads:

- `DownloadRepository`: application-scoped coordinator of Media3 `DownloadManager`, the cache and Room download rows.
- `DownloadRepository.addDownload`, `addAutoDownload`, `removeDownload`: start and remove downloads; background starts enqueue straight on `DownloadManager`.
- `DownloadRepository.completedDownloadItems`: completed downloads as `CompletedDownloadItem` (`Episode`, `Podcast`, download time), newest release first, with local playback paths only.
- `DownloadRepository.completedDownloadIds`, `downloadingEpisodeIds`, `isDownloaded`, `isDownloading`: count a download held under any trusted ID of the episode.
- `DownloadRepository.awaitDownloadCompletion`: lets a worker suspend until a download finishes or times out.
- `DownloadRepository.reconcileDownloadStatus`, `reconcileStaleDownloads`: reset or clear rows stuck in downloading.
- `DownloadRepository.relinkDownloadCache`: moves cached spans to a replacement episode ID and reports success; only the orphan rematch calls it.
- `DownloadTogglePolicy`, `DownloadToggleAction`: the toggle action on every surface (`CONFIRM_REMOVAL`, `CANCEL_DOWNLOAD`, `START_DOWNLOAD`); removing a finished download always asks first.
- `DownloadAnalyticsMapping`: download event properties; unknown size or source stays null and failures map to allowlisted codes.
- `DownloadSpeedLimiter`, `ThrottlingDataSource`: bandwidth throttling for download I/O.

Smart Downloads and auto-downloads:

- `SmartDownloadManager`: picks and schedules Smart Downloads within cumulative and per-episode time budgets, and keeps the periodic job in line with the stored toggle (`reconcileScheduleWithPreferences`).
- `AutoDownloadCoordinator`: per-show auto-download. It syncs subscriptions, discovers releases from publisher RSS and Room, claims releases and enqueues transfers.
- `AutoDownloadScheduling`: unique work names, tags and constraints for discovery and transfers; `enqueueEpisode` and `reconcile` are the only entry points.
- `SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker`, `AutoDownloadDiscoveryWorker`: the background workers.

Wiring:

- `DownloadsDependencies`, `DownloadsDependenciesHolder`: application-scoped dependencies for workers (`require()`).
- `ports.DownloadServiceLauncher`, `DownloadServiceLauncherHolder`: `:app` supplies the Media3 download service class, so downloads never depends on playback.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/
  downloads/
    DownloadRepository.kt              # repository, completion observer, direct enqueue
    CompletedDownloadItem.kt
    DownloadTogglePolicy.kt
    DownloadAnalyticsMapping.kt
    DownloadArtworkUrls.kt             # drops missing local artwork, falls back to the show's image
    DownloadAudioUri.kt                # episodes without an audio URL are never downloaded
    DownloadChaptersTranscriptsHelper.kt
    ThrottlingDataSource.kt            # ThrottlingDataSource, DownloadSpeedLimiter
    SmartDownloadManager.kt
    SmartDownloadCandidateLogic.kt
    SmartDownloadScheduleLogic.kt      # cadence and unique work name
    SmartDownloadSyncGate.kt           # serializes automatic and manual runs
    SmartDownloadWorker.kt
    PurgeSmartDownloadsWorker.kt
    AutoDownloadCoordinator.kt
    AutoDownloadScheduling.kt
    AutoDownloadBackgroundGate.kt      # consent, network and battery admission
    AutoDownloadDiscoveryWorker.kt
    AutoDownloadTransfer.kt            # per-episode transfer locks, DownloadManager thread helpers
    AutoDownloadWorker.kt              # worker and AutoDownloadRetention
    DownloadsDependencies.kt
    ports/DownloadServiceLauncher.kt
  data/                                # permanent stubs for older worker class names
    SmartDownloadWorker.kt
    AutoDownloadWorker.kt
    PurgeSmartDownloadsWorker.kt
```

## Dependencies

- `api`: `:core:catalog`, which brings the repositories, `UserPreferencesRepository` and the database.
- `implementation`: `:core:database`, `:core:domain`, `:core:model`, `:core:ranking`, `:core:analytics`, Media3 ExoPlayer (offline and cache), WorkManager, coroutines (with Guava interop), AndroidX core.
- Forbidden: `:core:playback` and feature modules; the service class comes through `DownloadServiceLauncherHolder`.

## Threading / lifecycle

- `DownloadRepository` and `SmartDownloadManager` are application-scoped through `AppContainer`; workers reach them through `DownloadsDependenciesHolder.require()`.
- Media3 download work and cache I/O run off the main thread.
- Background work never starts the download service: starts and removals go straight to `DownloadManager`, because a background service start throws `ForegroundServiceStartNotAllowedException`.
- `SmartDownloadSyncGate` serializes cadence admission and reconciliation, so a launch catch-up and a WorkManager run never both recycle downloads.
- Automatic Smart Downloads run at most once per 24-hour cadence from the stored last-success time; a timestamp in the future fails open. A manual refresh skips the cadence but stays serialized.
- Smart Downloads never run while disabled, except from an explicit manual refresh.
- `AutoDownloadScheduling.enqueueEpisode` serializes enqueue decisions under its own lock, separate from the transfer lock.
- Auto-download transfers run in resumable slices of `AutoDownloadWorker.WORK_SLICE_MS` (eight minutes) to stay inside WorkManager's limit.
- Push-triggered and foreground-discovered auto-downloads need no background consent.
- Background discovery is opt-in through `AutoDownloadBackgroundSettings`, off by default, and runs about every six hours; per-show auto-download flags never enable it.
- `AutoDownloadBackgroundGate` fails closed on missing consent, unknown connectivity, low or unknown battery, a metered network when restricted, or no charger when required. It is rechecked before fetches, writes and enqueues, and a live watcher cancels work when the policy stops permitting it.
- Turning background checks off cancels discovery and every background-tagged transfer.
- Foreground and push admission replace an unfinished background or legacy transfer for the same episode; repeated admissions of the same kind keep the current request.

## Persistence & identity

Worker identities ([`ARCHITECTURE.md`](../../ARCHITECTURE.md#workers)) are persisted by WorkManager and never renamed:

- Worker classes: `cx.aswin.boxlore.core.downloads.SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker`, `AutoDownloadDiscoveryWorker`; the `cx.aswin.boxlore.core.data.*` stubs and older `boxcast` names stay loadable through `LegacyWorkerFactory`.
- Unique work: `SmartDownloadSync` (periodic Smart Downloads), `PurgeSmartDownloads`, `auto-download-discovery` (periodic background discovery), `auto-download-episode-<episodeId>` (one transfer per episode).
- Tags: `auto-download-transfer`, `auto-download-policy-v2`, `auto-download-background-transfer`, `auto-download-show-<podcastId>`. A transfer without the policy tag is legacy and is superseded.
- Worker input keys: `episode_id`, `podcast_id`, `background_check`.

Download data:

- Download rows (`downloaded_episodes`, `auto_download_shows`, `auto_download_releases`) belong to [`:core:database`](../database/README.md); cache locations and rows are user data.
- An episode stored under several IDs keeps its download under the ID that holds it; playback reads it through that ID. Rules: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone).
- `addDownload` and `addAutoDownload` start nothing while another ID of the episode holds a completed, running or queued download.
- `removeDownload` also removes the downloads other IDs of the episode hold.
- Automatic clean-ups (Smart Downloads recycling, failed attempts, the per-show limit, the purge) pass `acrossAliases = false`, so they never remove another ID's download, such as a manual one.
- `relinkDownloadCache` changes the Room row only after the cache move succeeds; a failed or conflicting move keeps the old completed row and bytes. It moves only phone-minted IDs with no alias or an unresolved one.
- Switching a show to its RSS feed moves no downloads; the RSS episode plays the old ID's download through its alias.
- Smart Downloads read history through `EpisodeListenerStore`, so an episode counts once across its IDs; per-show reads and the auto-download limit cover every show in `listenerShowIds`.
- Each auto-download show stores its activation time; only releases published after it are downloaded, so turning auto-download on never fetches the archive.
- A recovered feed address is saved only through `saveFeedUrl`, the app's one feed address writer.
- An unfollowed show's stored copy never schedules, claims or enqueues auto-downloads.
- Brief downloads use `EpisodeMediaCacheKey`, so offline keys match playback.
- The download service identity is owned by [`:core:playback`](../playback/README.md) and supplied by `:app`.

## Testing notes

Tests live in `core/downloads/src/test` (JUnit 5, Robolectric, WorkManager testing); `DownloadTestLooper` is a shared helper.

| Area | Test classes |
| :--- | :--- |
| Repository and toggles | `DownloadRepositoryTest`, `CompletedDownloadItemsTest`, `DownloadTogglePolicyTest`, `DownloadAudioUriTest` |
| Offline extras and artwork | `DownloadChaptersTranscriptsHelperTest`, `DownloadArtworkUrlsTest` |
| Analytics | `DownloadAnalyticsMappingTest` |
| Smart Downloads | `SmartDownloadManagerTest`, `SmartDownloadCandidateLogicTest`, `SmartDownloadScheduleLogicTest`, `SmartDownloadSyncGateTest`, `SmartDownloadWorkerTest` |
| Auto-downloads | `AutoDownloadCoordinatorTest`, `AutoDownloadSchedulingTest`, `AutoDownloadBackgroundGateTest`, `AutoDownloadWorkerTest`, `AutoDownloadRetentionTest` |
| Wiring | `DownloadsDependenciesHolderTest` |

```bash
./gradlew :core:downloads:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests and includes the module in the coverage gate.
- App assembly checks the download service launcher wiring through the production graph.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:database` README](../database/README.md)
- [`:core:playback` README](../playback/README.md)
- [`:app` README](../../app/README.md)
