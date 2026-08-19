package cx.aswin.boxlore.core.catalog.backup

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.catalog.ExactPodcastLookupKey
import cx.aswin.boxlore.core.catalog.ExactPodcastLookupResult
import cx.aswin.boxlore.core.catalog.ExactPodcastLookupType
import cx.aswin.boxlore.core.catalog.LOCAL_CATALOG_WINDOW_BOUND
import cx.aswin.boxlore.core.catalog.PodcastIndexSearchResult
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.catalog.toPodcast
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.ranking.AdaptiveRankingBackup
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class GlobalPreferencesBackup(
    val region: String? = null,
    val contentLanguages: List<String>? = null,
    val themeConfig: String? = null,
    val themeBrand: String? = null,
    val surfaceStyle: String? = null,
    val useDynamicColor: Boolean? = null,
    val openAppTo: String? = null,
    val subscriptionSort: String? = null,
    val subscriptionManualOrder: List<String>? = null,
    val homePinnedPodcastIds: List<String>? = null,
    val latestEpisodesSortUseSmart: Boolean? = null,
    val skipBehavior: String? = null,
    val skipBeginningMs: Long? = null,
    val skipEndingMs: Long? = null,
    val seekBackwardMs: Long? = null,
    val seekForwardMs: Long? = null,
    val hideCompletedInFeeds: Boolean? = null,
    val hideCompletedInShowDetails: Boolean? = null,
    val hideCompletedInHome: Boolean? = null,
    val hideCompletedInSubs: Boolean? = null,
    val restartForgottenEpisodes: Boolean? = null,
    val smartDownloadsEnabled: Boolean? = null,
    val smartDownloadsMaxEpisodes: Int? = null,
    val smartDownloadsStorageBudget: Long? = null,
    val smartDownloadsWifiOnly: Boolean? = null,
    val smartDownloadsChargingOnly: Boolean? = null,
    val smartDownloadsCleanupRule: String? = null,
    val autoDownloadWifiOnly: Boolean? = null,
    val autoDownloadMaxEpisodes: Int? = null,
    val autoDownloadDeleteCompleted: Boolean? = null,
)

data class BoxLoreBackup(
    val version: Int = 5,
    val subscriptions: List<PodcastEntity>,
    val history: List<ListeningHistoryEntity>,
    val globalPreferences: GlobalPreferencesBackup? = null,
    val adaptiveRanking: AdaptiveRankingBackup? = null,
    val directFeedOptIns: List<DirectFeedOptInBackup>? = null,
)

data class OpmlFeed(
    val title: String,
    val xmlUrl: String,
)

@Suppress("TooManyFunctions", "LargeClass")
class LibraryBackupManager(
    private val subscriptionRepository: SubscriptionRepository,
    private val listeningHistory: ListeningHistoryBackupPort,
    private val podcastRepository: PodcastRepository,
    private val userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository? = null,
    context: android.content.Context,
    private val adaptiveRankingRepository: AdaptiveRankingRepository =
        SharedAppDependenciesHolder.require().adaptiveRankingRepository,
    private val rssPodcastRepository: RssPodcastRepository =
        SharedAppDependenciesHolder.require().rssPodcastRepository,
    private val episodeSupplementPort: EpisodeSupplementPort? = null,
) {
    private val context = context.applicationContext
    private val rssFeedClient = RssFeedClient()
    private val gson: Gson =
        GsonBuilder()
            .setPrettyPrinting()
            .create()

    suspend fun exportLibraryAsJson(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val allHistory = listeningHistory.getAllHistory().first()

        val globalPrefs =
            if (userPrefs != null) {
                GlobalPreferencesBackup(
                    region = userPrefs.regionStream.first(),
                    contentLanguages = userPrefs.contentLanguagesStream.first(),
                    themeConfig = userPrefs.themeConfigStream.first(),
                    themeBrand = userPrefs.themeBrandStream.first(),
                    surfaceStyle = userPrefs.surfaceStyleStream.first(),
                    useDynamicColor = userPrefs.useDynamicColorStream.first(),
                    openAppTo = userPrefs.openAppToStream.first(),
                    subscriptionSort = userPrefs.subscriptionSortStream.first(),
                    subscriptionManualOrder = userPrefs.subscriptionManualOrderStream.first(),
                    homePinnedPodcastIds = userPrefs.homePinnedPodcastIdsStream.first(),
                    latestEpisodesSortUseSmart = userPrefs.latestEpisodesSortUseSmartStream.first(),
                    skipBehavior = userPrefs.skipBehaviorStream.first(),
                    skipBeginningMs = userPrefs.skipBeginningMsStream.first(),
                    skipEndingMs = userPrefs.skipEndingMsStream.first(),
                    seekBackwardMs = userPrefs.seekBackwardMsStream.first(),
                    seekForwardMs = userPrefs.seekForwardMsStream.first(),
                    hideCompletedInFeeds = userPrefs.hideCompletedInFeedsStream.first(),
                    hideCompletedInShowDetails = userPrefs.hideCompletedInShowDetailsStream.first(),
                    hideCompletedInHome = userPrefs.hideCompletedInHomeStream.first(),
                    hideCompletedInSubs = userPrefs.hideCompletedInSubsStream.first(),
                    restartForgottenEpisodes = userPrefs.restartForgottenEpisodesStream.first(),
                    smartDownloadsEnabled = userPrefs.smartDownloadsEnabledStream.first(),
                    smartDownloadsMaxEpisodes = userPrefs.smartDownloadsMaxEpisodesStream.first(),
                    smartDownloadsStorageBudget = userPrefs.smartDownloadsStorageBudgetStream.first(),
                    smartDownloadsWifiOnly = userPrefs.smartDownloadsWifiOnlyStream.first(),
                    smartDownloadsChargingOnly = userPrefs.smartDownloadsChargingOnlyStream.first(),
                    smartDownloadsCleanupRule = userPrefs.smartDownloadsCleanupRuleStream.first(),
                    autoDownloadWifiOnly = userPrefs.autoDownloadWifiOnlyStream.first(),
                    autoDownloadMaxEpisodes = userPrefs.autoDownloadMaxEpisodesStream.first(),
                    autoDownloadDeleteCompleted = userPrefs.autoDownloadDeleteCompletedStream.first(),
                )
            } else {
                null
            }

        val rankingBackup = adaptiveRankingRepository.exportBackup()
        val backup =
            BoxLoreBackup(
                version = LibraryBackupDirectFeedLogic.VERSION,
                subscriptions = subscriptions,
                history = allHistory,
                globalPreferences = globalPrefs,
                adaptiveRanking = rankingBackup,
                directFeedOptIns = null,
            )
        return gson.toJson(backup)
    }

    suspend fun exportLibraryAsOpml(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>Boxlore Subscriptions</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        sb.append("    <outline text=\"Subscriptions\" title=\"Subscriptions\">\n")
        for (entity in subscriptions) {
            val title = escapeXml(entity.title)
            val feedUrl =
                escapeXml(
                    entity.feedUrl ?: "${BuildConfig.BOXLORE_API_BASE_URL}/episodes?id=${entity.podcastId}",
                )
            sb.append("      <outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"$feedUrl\" />\n")
        }
        sb.append("    </outline>\n")
        sb.append("  </body>\n")
        sb.append("</opml>")
        return sb.toString()
    }

    private fun escapeXml(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    suspend fun importLibraryFromJson(jsonString: String): Pair<Int, Boolean> =
        try {
            val backup = gson.fromJson(jsonString, BoxLoreBackup::class.java)
            restoreImportedGlobalPreferences(backup.globalPreferences)
            val importedIds = mutableListOf<String>()
            for (entity in backup.subscriptions) {
                importBackupSubscription(entity, backup)?.let { importedIds += it }
            }
            restoreImportedHistory(backup.history, importedIds)
            backup.adaptiveRanking?.let { rankingBackup ->
                adaptiveRankingRepository.restoreBackup(rankingBackup)
            }
            refreshImportedLatestEpisodes(importedIds, backup.directFeedOptIns)
            Pair(
                importedIds.size,
                backup.subscriptions.any { it.notificationsEnabled || it.autoDownloadEnabled },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(-1, false)
        }

    private suspend fun restoreImportedGlobalPreferences(prefs: GlobalPreferencesBackup?) {
        val up = userPrefs ?: return
        if (prefs == null) return
        prefs.region.writePref { up.setRegion(it) }
        // Restore languages after region so setRegion's recommended reset does not wipe them.
        prefs.contentLanguages.writePref { up.setContentLanguages(it) }
        prefs.themeConfig.writePref { up.setThemeConfig(it) }
        prefs.themeBrand.writePref { up.setThemeBrand(it) }
        prefs.surfaceStyle.writePref { up.setSurfaceStyle(it) }
        prefs.useDynamicColor.writePref { up.setUseDynamicColor(it) }
        prefs.openAppTo.writePref { up.setOpenAppTo(it) }
        prefs.subscriptionSort.writePref { up.setSubscriptionSort(it) }
        prefs.subscriptionManualOrder.writePref { up.setSubscriptionManualOrder(it) }
        prefs.homePinnedPodcastIds.writePref { up.setHomePinnedPodcastIds(it) }
        prefs.latestEpisodesSortUseSmart.writePref { up.setLatestEpisodesSortUseSmart(it) }
        prefs.skipBehavior.writePref { up.setSkipBehavior(it) }
        prefs.skipBeginningMs.writePref { up.setSkipBeginningMs(it) }
        prefs.skipEndingMs.writePref { up.setSkipEndingMs(it) }
        prefs.seekBackwardMs.writePref { up.setSeekBackwardMs(it) }
        prefs.seekForwardMs.writePref { up.setSeekForwardMs(it) }
        prefs.hideCompletedInFeeds.writePref { up.setHideCompletedInFeeds(it) }
        prefs.hideCompletedInShowDetails.writePref { up.setHideCompletedInShowDetails(it) }
        prefs.hideCompletedInHome.writePref { up.setHideCompletedInHome(it) }
        prefs.hideCompletedInSubs.writePref { up.setHideCompletedInSubs(it) }
        prefs.restartForgottenEpisodes.writePref { up.setRestartForgottenEpisodes(it) }
        restoreImportedSmartDownloadPrefs(prefs, up)
        prefs.autoDownloadWifiOnly.writePref { up.setAutoDownloadWifiOnly(it) }
        prefs.autoDownloadMaxEpisodes.writePref { up.setAutoDownloadMaxEpisodes(it) }
        prefs.autoDownloadDeleteCompleted.writePref { up.setAutoDownloadDeleteCompleted(it) }
    }

    private suspend fun <T> T?.writePref(set: suspend (T) -> Unit) {
        if (this != null) set(this)
    }

    private suspend fun restoreImportedSmartDownloadPrefs(
        prefs: GlobalPreferencesBackup,
        up: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    ) {
        prefs.smartDownloadsEnabled?.let { enabled ->
            up.setSmartDownloadsEnabled(enabled)
            if (enabled) {
                val wifiOnly = prefs.smartDownloadsWifiOnly ?: true
                val chargingOnly = prefs.smartDownloadsChargingOnly ?: false
                cx.aswin.boxlore.core.catalog.ports.SmartDownloadSyncPort.schedulePeriodicSync
                    ?.invoke(wifiOnly, chargingOnly)
            } else {
                cx.aswin.boxlore.core.catalog.ports.SmartDownloadSyncPort.cancelPeriodicSync
                    ?.invoke()
            }
        }
        prefs.smartDownloadsMaxEpisodes?.let { up.setSmartDownloadsMaxEpisodes(it) }
        prefs.smartDownloadsStorageBudget?.let { up.setSmartDownloadsStorageBudget(it) }
        prefs.smartDownloadsWifiOnly?.let { up.setSmartDownloadsWifiOnly(it) }
        prefs.smartDownloadsChargingOnly?.let { up.setSmartDownloadsChargingOnly(it) }
        prefs.smartDownloadsCleanupRule?.let { up.setSmartDownloadsCleanupRule(it) }
    }

    private suspend fun importBackupSubscription(
        entity: PodcastEntity,
        backup: BoxLoreBackup,
    ): String? {
        val podcast =
            if (entity.sourceType == PodcastEntity.SOURCE_RSS) {
                importRssBackupSubscription(entity) ?: return null
            } else {
                importPiBackupSubscription(entity)
            }
        applyImportedShowSettings(podcast, entity, backup)
        return podcast.id
    }

    private suspend fun importRssBackupSubscription(entity: PodcastEntity): cx.aswin.boxlore.core.model.Podcast? {
        val feedUrl = entity.feedUrl
        if (feedUrl.isNullOrBlank()) return null
        val rssPodcast =
            LibraryBackupImportLogic.runRestore(
                block = {
                    rssPodcastRepository
                        .addSubscription(feedUrl)
                        .podcast
                },
                onFailure = { error ->
                    Log.e(
                        "JSON_IMPORT",
                        "RSS restore failed for ${entity.title}; feed must be re-added",
                        error,
                    )
                },
            ) ?: return null
        val subscribedRssPodcast =
            rssPodcast.copy(
                preferredSort = entity.preferredSort,
                linkedPodcastIndexId = entity.linkedPodcastIndexId,
                skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
                skipEndingOverrideMs = entity.skipEndingOverrideMs,
            )
        subscriptionRepository.subscribe(subscribedRssPodcast)
        return subscribedRssPodcast
    }

    private suspend fun importPiBackupSubscription(entity: PodcastEntity): cx.aswin.boxlore.core.model.Podcast {
        val podcast =
            cx.aswin.boxlore.core.model.Podcast(
                id = (entity.podcastId as String?) ?: "",
                title = (entity.title as String?) ?: "Unknown",
                artist = (entity.author as String?) ?: "Unknown",
                imageUrl = (entity.imageUrl as String?) ?: "",
                description = entity.description,
                genre = entity.genre ?: "Podcast",
                type = (entity.type as String?) ?: "episodic",
                latestEpisode = entity.latestEpisode,
                subscribedAt = entity.subscribedAt,
                podcastGuid = entity.podcastGuid,
                fundingUrl = entity.fundingUrl,
                fundingMessage = entity.fundingMessage,
                medium = entity.medium,
                hasValue = entity.hasValue,
                updateFrequency = entity.updateFrequency,
                location = entity.location,
                license = entity.license,
                isLocked = entity.isLocked,
                preferredSort = entity.preferredSort,
                skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
                skipEndingOverrideMs = entity.skipEndingOverrideMs,
                sourceType =
                    (entity.sourceType as String?)
                        ?: PodcastEntity.SOURCE_PODCAST_INDEX,
                feedUrl = entity.feedUrl,
                rssRefreshCapability =
                    (entity.rssRefreshCapability as String?)
                        ?: PodcastEntity.RSS_REFRESH_MANUAL,
                rssCatalogStale = entity.rssCatalogStale,
                rssHasNewEpisodes = entity.rssHasNewEpisodes,
                linkedPodcastIndexId = entity.linkedPodcastIndexId,
            )
        subscriptionRepository.subscribe(podcast)
        return podcast
    }

    private suspend fun applyImportedShowSettings(
        podcast: cx.aswin.boxlore.core.model.Podcast,
        entity: PodcastEntity,
        backup: BoxLoreBackup,
    ) {
        if (entity.notificationsEnabled) {
            subscriptionRepository.setNotificationsEnabled(podcast, true)
        }
        if (entity.autoDownloadEnabled) {
            subscriptionRepository.setAutoDownloadEnabled(podcast.id, true)
        }
        if (backup.version >= 4) {
            subscriptionRepository.setPlaybackSkipOverrides(
                podcast.id,
                entity.skipBeginningOverrideMs,
                entity.skipEndingOverrideMs,
            )
        }
    }

    private suspend fun restoreImportedHistory(
        history: List<ListeningHistoryEntity>,
        importedIds: List<String>,
    ) {
        for (entity in history) {
            if (entity.podcastId.startsWith("rss:") && entity.podcastId !in importedIds) {
                continue
            }
            val safeEntity = LibraryBackupHistoryRestore.sanitize(entity)
            listeningHistory.upsertHistoryEntity(safeEntity)
        }
    }

    private suspend fun refreshImportedLatestEpisodes(
        importedIds: List<String>,
        backupOptIns: List<DirectFeedOptInBackup>?,
    ) {
        val subscriptionFeedUrls =
            importedIds.associateWith { id ->
                subscriptionRepository.getPodcastEntity(id)?.feedUrl
            }
        val refreshPlan =
            LibraryBackupDirectFeedLogic.refreshPlan(
                importedIds = importedIds,
                backupOptIns = backupOptIns,
                subscriptionFeedUrls = subscriptionFeedUrls,
            )
        LibraryBackupDirectFeedLogic.runPostSubscribeRefresh(
            plan = refreshPlan,
            restoreDirectFeeds = { restoreImportedDirectFeeds(it) },
            syncPi = { ids ->
                try {
                    val syncedMap = podcastRepository.syncSubscriptions(ids)
                    for ((id, ep) in syncedMap) {
                        subscriptionRepository.updateLatestEpisode(id, ep)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("JSON_IMPORT", "Failed to sync episodes", e)
                }
            },
            refreshRss = { refreshImportedRssCatalogs(it) },
        )
    }

    suspend fun importFromOpml(inputStream: InputStream): Int =
        LibraryBackupImportLogic.opmlImportCount(
            onFailure = { error -> Log.e("OPML_IMPORT", "Failed to import OPML", error) },
        ) {
            parseOpmlFeeds(inputStream).count { feed ->
                importSingleOpmlFeed(feed) != null
            }
        }

    fun parseOpmlFeeds(inputStream: InputStream): List<OpmlFeed> {
        val feeds = mutableListOf<OpmlFeed>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    val title = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title")

                    if (xmlUrl != null && title != null) {
                        feeds.add(OpmlFeed(title, xmlUrl))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to parse OPML feeds", e)
        }
        return feeds
    }

    suspend fun importSingleOpmlFeed(feed: OpmlFeed): cx.aswin.boxlore.core.model.Podcast? {
        try {
            return when (val resolution = resolveOpmlCatalogPodcast(feed)) {
                is OpmlCatalogDecision.Found -> {
                    subscriptionRepository.subscribe(resolution.podcast)
                    refreshImportedLatestEpisodes(
                        importedIds = listOf(resolution.podcast.id),
                        backupOptIns = null,
                    )
                    resolution.podcast
                }
                OpmlCatalogDecision.ConfirmedAbsent ->
                    LibraryBackupImportLogic.runRestore(
                        block = {
                            rssPodcastRepository
                                .addSubscription(feed.xmlUrl)
                                .podcast
                        },
                        onFailure = { error ->
                            Log.w(
                                "OPML_IMPORT",
                                "Catalog miss and RSS import failed for ${feed.title}",
                                error,
                            )
                        },
                    )
                OpmlCatalogDecision.Deferred -> {
                    Log.w("OPML_IMPORT", "Deferring ${feed.title}; Podcast Index lookup did not settle")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to import single feed: ${feed.title}", e)
        }
        return null
    }

    private suspend fun resolveOpmlCatalogPodcast(feed: OpmlFeed): OpmlCatalogDecision {
        val initialUrlLookup = lookupOpmlFeedByUrl(feed.xmlUrl)
        if (initialUrlLookup is ExactPodcastLookupResult.Found) {
            return OpmlCatalogDecision.Found(initialUrlLookup.podcast)
        }
        val titleSearch =
            podcastRepository.searchPodcastIndexForOpml(feed.title)
        if (titleSearch is PodcastIndexSearchResult.Success) {
            OpmlImportLogic
                .catalogMatch(
                    opmlTitle = feed.title,
                    opmlXmlUrl = feed.xmlUrl,
                    urlLookup = null,
                    titleSearch = titleSearch.podcasts,
                )?.let { return OpmlCatalogDecision.Found(it) }
        }

        val peeked = peekOpmlFeed(feed.xmlUrl) ?: return OpmlCatalogDecision.Deferred
        val redirectedUrlLookup = lookupOpmlFeedByUrl(peeked.finalUrl)
        val urlLookup = OpmlImportLogic.preferLookup(initialUrlLookup, redirectedUrlLookup)
        val guidLookup =
            peeked.guid
                ?.let { lookupOpmlFeedByGuid(it) }
                ?: ExactPodcastLookupResult.NotFound
        return OpmlImportLogic.finalCatalogDecision(
            opmlTitle = peeked.title.ifBlank { feed.title },
            opmlXmlUrl = peeked.finalUrl,
            urlLookup = urlLookup,
            guidLookup = guidLookup,
            titleSearch = titleSearch,
        )
    }

    private suspend fun lookupOpmlFeedByUrl(xmlUrl: String): ExactPodcastLookupResult =
        OpmlImportLogic.firstExactLookup(OpmlImportLogic.urlLookupCandidates(xmlUrl)) { candidate ->
            podcastRepository.lookupExactPodcastIndex(
                ExactPodcastLookupKey(
                    type = ExactPodcastLookupType.FEED_URL,
                    value = candidate,
                ),
            )
        }

    private suspend fun lookupOpmlFeedByGuid(guid: String): ExactPodcastLookupResult {
        val value = guid.trim()
        if (value.isEmpty()) return ExactPodcastLookupResult.NotFound
        return podcastRepository.lookupExactPodcastIndex(
            ExactPodcastLookupKey(
                type = ExactPodcastLookupType.PODCAST_GUID,
                value = value,
            ),
        )
    }

    private suspend fun peekOpmlFeed(xmlUrl: String): PeekedOpmlFeed? {
        val httpsUrl = OpmlImportLogic.httpsFeedUrl(xmlUrl) ?: return null
        return runCatching {
            val fetched = rssFeedClient.fetch(httpsUrl)
            val parsed = rssFeedClient.parse(fetched.finalUrl, fetched.body)
            PeekedOpmlFeed(
                finalUrl = fetched.finalUrl,
                title = parsed.title,
                guid = parsed.podcastGuid,
            )
        }.onFailure { error ->
            Log.w("OPML_IMPORT", "Feed peek failed for $xmlUrl", error)
        }.getOrNull()
    }

    private data class PeekedOpmlFeed(
        val finalUrl: String,
        val title: String,
        val guid: String?,
    )

    private fun supplementPort(): EpisodeSupplementPort? = episodeSupplementPort ?: podcastRepository.episodeSupplementRepository

    private suspend fun restoreImportedLocalCatalogs(
        targets: List<DirectFeedOptInBackup>,
        catalog: LocalEpisodeCatalogPort,
    ) {
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets = targets,
            actions =
                DirectFeedRestoreActions(
                    restoreStub = { _, _ -> },
                    ensureFeedUrl = { id, url -> subscriptionRepository.ensureHttpsFeedUrl(id, url) },
                    invalidateCache = { id -> podcastRepository.invalidateEpisodesCache(id) },
                    refreshFeed = { id, url ->
                        val entity = subscriptionRepository.getPodcastEntity(id)
                        val meta =
                            LocalEpisodeCatalogPort.PodcastMeta(
                                title = entity?.title,
                                imageUrl = entity?.imageUrl,
                                genre = entity?.genre,
                                artist = entity?.author,
                            )
                        when (
                            val outcome =
                                catalog.refresh(
                                    LocalEpisodeCatalogPort.RefreshRequest(
                                        podcastIndexId = id,
                                        feedUrl = url,
                                        meta = meta,
                                        loadPiBaseline = {
                                            podcastRepository.loadPiEpisodesForBaseline(
                                                feedId = id,
                                                limit = SubscriptionForegroundSync.DIRECT_FEED_BASELINE_LIMIT,
                                            )
                                        },
                                    ),
                                )
                        ) {
                            is LocalEpisodeCatalogPort.RefreshOutcome.Success ->
                                EpisodeSupplementOutcome.Success(
                                    addedCount = outcome.itemCount,
                                    totalSupplementCount = outcome.itemCount,
                                    newestFeedEpisode = outcome.newest,
                                )
                            is LocalEpisodeCatalogPort.RefreshOutcome.Unchanged ->
                                EpisodeSupplementOutcome.Success(
                                    addedCount = 0,
                                    totalSupplementCount = 0,
                                    newestFeedEpisode = outcome.newest,
                                )
                            is LocalEpisodeCatalogPort.RefreshOutcome.Failure ->
                                EpisodeSupplementOutcome.Failure(outcome.message)
                        }
                    },
                    saveTip = { id, episode ->
                        subscriptionRepository.updateLatestEpisode(
                            podcastId = id,
                            episode = episode,
                            markAsNew = false,
                            publisherFeedAuthoritative = true,
                        )
                    },
                    syncTrackedUrl = { id ->
                        subscriptionRepository.getPodcastEntity(id)?.toPodcast()?.let { podcast ->
                            subscriptionRepository.syncTrackedPodcastFeedUrl(podcast)
                        }
                    },
                    onError = { id, error ->
                        Log.e("JSON_IMPORT", "Local catalog restore failed for $id", error)
                    },
                ),
        )
    }

    private suspend fun restoreImportedDirectFeeds(targets: List<DirectFeedOptInBackup>) {
        val catalog = podcastRepository.localEpisodeCatalog
        if (catalog != null) {
            restoreImportedLocalCatalogs(targets, catalog)
            return
        }
        val port = supplementPort() ?: return
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets = targets,
            actions =
                DirectFeedRestoreActions(
                    restoreStub = { _, _ -> },
                    ensureFeedUrl = { id, url -> subscriptionRepository.ensureHttpsFeedUrl(id, url) },
                    invalidateCache = { id -> podcastRepository.invalidateEpisodesCache(id) },
                    refreshFeed = { id, url ->
                        val entity = subscriptionRepository.getPodcastEntity(id)
                        port.refreshFromFeed(
                            EpisodeSupplementPort.RefreshFromFeedRequest(
                                podcastIndexId = id,
                                feedUrl = url,
                                loadBaseline = {
                                    podcastRepository.loadPiEpisodesForBaseline(
                                        feedId = id,
                                        limit = SubscriptionForegroundSync.DIRECT_FEED_BASELINE_LIMIT,
                                    )
                                },
                                podcastTitle = entity?.title,
                                podcastImageUrl = entity?.imageUrl,
                                podcastGenre = entity?.genre,
                                podcastArtist = entity?.author,
                            ),
                        )
                    },
                    saveTip = { id, episode ->
                        subscriptionRepository.updateLatestEpisode(
                            podcastId = id,
                            episode = episode,
                            markAsNew = false,
                            publisherFeedAuthoritative = true,
                        )
                    },
                    syncTrackedUrl = { id ->
                        subscriptionRepository.getPodcastEntity(id)?.toPodcast()?.let { podcast ->
                            subscriptionRepository.syncTrackedPodcastFeedUrl(podcast)
                        }
                    },
                    onError = { id, error ->
                        Log.e("JSON_IMPORT", "Direct-feed restore failed for $id", error)
                    },
                ),
        )
    }

    private suspend fun refreshImportedRssCatalogs(rssIds: Collection<String>) {
        for (id in rssIds) {
            try {
                rssPodcastRepository.refreshCatalogIfNeeded(id)
            } catch (e: Exception) {
                Log.e("JSON_IMPORT", "RSS catalog refresh failed for $id", e)
            }
        }
    }

    suspend fun markAllEpisodesCompleted(podcast: cx.aswin.boxlore.core.model.Podcast) {
        try {
            val episodes = mutableListOf<cx.aswin.boxlore.core.model.Episode>()
            var offset = 0
            val pageSize = LOCAL_CATALOG_WINDOW_BOUND
            do {
                val page =
                    podcastRepository.getEpisodesPaginated(
                        feedId = podcast.id,
                        limit = pageSize,
                        offset = offset,
                        sort = "newest",
                    )
                episodes += page.episodes
                offset += page.sourceCount
                if (!page.hasMore || page.episodes.isEmpty()) break
            } while (true)
            if (episodes.isNotEmpty()) {
                listeningHistory.markAllEpisodesCompleted(
                    episodes = episodes,
                    podcastId = podcast.id,
                    podcastTitle = podcast.title,
                    podcastImageUrl = podcast.imageUrl,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to mark all episodes completed for: ${podcast.title}", e)
        }
    }
}
