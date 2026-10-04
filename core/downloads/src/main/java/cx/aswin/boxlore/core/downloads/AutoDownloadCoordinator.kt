package cx.aswin.boxlore.core.downloads

import android.content.Context
import cx.aswin.boxlore.core.database.AutoDownloadReleaseEntity
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Publisher RSS + Room own release discovery. Push is only an early wake-up hint. */
class AutoDownloadCoordinator(
    private val database: BoxLoreDatabase,
    private val catalog: LocalEpisodeCatalogPort,
    private val preferences: UserPreferencesRepository,
    private val enqueue: suspend (podcastId: String, episodeId: String, wifiOnly: Boolean, background: Boolean) -> Unit,
    private val recoverFeedUrl: suspend (String) -> String? = { null },
    private val loadInitialBaseline: suspend (String) -> List<Episode> = { emptyList() },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val gate = Mutex()
    private val ledger = database.autoDownloadDao()

    suspend fun synchronizeSubscriptions(): Set<String> = gate.withLock {
        val shows = database.podcastDao().getSubscribedPodcastsList().filter(::eligible)
        val active = shows.map { it.podcastId }.toSet()
        for (old in ledger.getShows()) {
            if (old.podcastId !in active) ledger.deactivate(old.podcastId)
        }
        for (show in shows) ledger.activate(show.podcastId, nowSeconds())
        active
    }

    /** Failure of one feed does not prevent discovery or replay for other shows. */
    suspend fun discover(canProceed: suspend () -> Boolean = { false }): Boolean {
        if (!canProceed()) return true
        synchronizeSubscriptions()
        return kotlinx.coroutines.coroutineScope {
            val limit = kotlinx.coroutines.sync.Semaphore(2)
            database.podcastDao().getSubscribedPodcastsList().filter(::eligible).map { show ->
                async {
                    limit.acquire()
                    try {
                        !canProceed() || discoverShow(show, canProceed)
                    } finally {
                        limit.release()
                    }
                }
            }.map { it.await() }.all { it }
        }
    }

    private suspend fun discoverShow(show: PodcastEntity, canProceed: suspend () -> Boolean): Boolean = try {
        val feed = show.feedUrl?.takeIf { it.startsWith("https://") } ?: recoverMissingFeed(show.podcastId)
        var succeeded = feed != null
        if (feed != null) {
            if (feed != show.feedUrl) database.podcastDao().setFeedUrl(show.podcastId, feed)
            val ready = catalog.isReady(show.podcastId)
            val outcome = catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                podcastIndexId = show.podcastId,
                    feedUrl = feed,
                    meta = meta(show),
                loadPiBaseline = if (!ready) ({ loadInitialBaseline(show.podcastId) }) else null,
                reason = LocalEpisodeCatalogPort.RefreshReason.AUTO_DOWNLOAD,
                canProceed = canProceed,
            )
            )
            succeeded = outcome !is LocalEpisodeCatalogPort.RefreshOutcome.Failure
        }
        scanCached(show.podcastId, background = true, canProceed = canProceed)
        succeeded
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private suspend fun recoverMissingFeed(podcastId: String): String? {
        val now = nowSeconds() * 1000L
        val last = catalog.lastFeedUrlLookupAt(podcastId)
        if (last > 0 && now - last in 0 until 24 * 60 * 60 * 1000L) return null
        catalog.markFeedUrlLookup(podcastId, now)
        return recoverFeedUrl(podcastId)
    }

    /** Called after foreground feed persistence too; pending claims are replayable after a crash. */
    suspend fun scanCached(podcastId: String, background: Boolean = false, canProceed: suspend () -> Boolean = { !background }) = gate.withLock {
        if (!canProceed()) return@withLock
        val show = database.podcastDao().getPodcast(podcastId) ?: return@withLock
        if (!eligible(show)) return@withLock
        val activation = ledger.getShow(podcastId) ?: return@withLock
        val maximum = preferences.autoDownloadMaxEpisodesStream.first()
        val bound = if (maximum > 0) maximum.coerceAtMost(100) else 100
        val episodes = catalog.getPage(podcastId, bound, 0, "newest", meta(show))
        for (episode in episodes) {
            if (episode.publishedDate >= activation.enabledAt && episode.audioUrl.isNotBlank()) {
                ledger.insertRelease(AutoDownloadReleaseEntity(episode.id, podcastId))
            }
        }
        enqueuePending(podcastId, background, canProceed)
    }

    suspend fun acceptRelease(podcastId: String, episode: Episode) = gate.withLock {
        val show = database.podcastDao().getPodcast(podcastId) ?: return@withLock
        if (!eligible(show)) return@withLock
        // Usually established by migration/settings/startup. Never bootstrap from a pushed archive item.
        val activation = ledger.getShow(podcastId) ?: return@withLock
        if (episode.publishedDate < activation.enabledAt || episode.audioUrl.isBlank()) return@withLock
        ledger.insertRelease(AutoDownloadReleaseEntity(episode.id, podcastId))
        enqueuePending(podcastId, background = false, canProceed = { true })
    }

    private suspend fun enqueuePending(podcastId: String, background: Boolean, canProceed: suspend () -> Boolean) {
        val wifiOnly = preferences.autoDownloadWifiOnlyStream.first()
        for (release in ledger.pending(podcastId)) {
            if (!canProceed()) return
            enqueue(podcastId, release.episodeId, wifiOnly, background)
        }
    }

    companion object {
        fun eligible(show: PodcastEntity): Boolean = show.isSubscribed && show.autoDownloadEnabled && !show.isRss
        fun meta(show: PodcastEntity) = LocalEpisodeCatalogPort.PodcastMeta(show.title, show.imageUrl, show.genre, show.author)

        fun create(
            context: Context,
            database: BoxLoreDatabase,
            catalog: LocalEpisodeCatalogPort,
            preferences: UserPreferencesRepository,
            recoverFeedUrl: suspend (String) -> String?,
            loadInitialBaseline: suspend (String) -> List<Episode>
        ): AutoDownloadCoordinator = AutoDownloadCoordinator(
                database,
            catalog,
            preferences,
                enqueue = { podcastId, episodeId, wifiOnly, background ->
                    val policy = preferences.autoDownloadBackgroundSettingsStream.first()
                    if (!background || AutoDownloadBackgroundGate.create(context, preferences).allowed()) {
                        AutoDownloadScheduling.enqueueEpisode(context, podcastId, episodeId, wifiOnly, if (background) policy else null)
                    }
                },
                recoverFeedUrl = recoverFeedUrl,
            loadInitialBaseline = loadInitialBaseline,
            )
    }
}
