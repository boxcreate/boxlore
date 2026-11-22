package cx.aswin.boxlore.core.catalog.backup

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import cx.aswin.boxlore.core.catalog.FeedUrlSources
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.LOCAL_CATALOG_WINDOW_BOUND
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.catalog.toPodcast
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.ranking.AdaptiveRankingBackup
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RenumberedShows
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class GlobalPreferencesBackup(
    val region: String? = null,
    val contentLanguages: List<String>? = null,
    /** English switched off (D9, 29 Oct); absent in older backups, which keep English on. */
    val contentEnglishOff: Boolean? = null,
    val themeConfig: String? = null,
    val themeBrand: String? = null,
    val surfaceStyle: String? = null,
    val useDynamicColor: Boolean? = null,
    val fontRoundness: String? = null,
    val navigationStyle: String? = null,
    val openAppTo: String? = null,
    val subscriptionSort: String? = null,
    val subscriptionFolderSort: String? = null,
    val subscriptionIntraFolderSort: String? = null,
    val subscriptionManualOrder: List<String>? = null,
    val subscriptionFolderManualOrder: List<String>? = null,
    val autoOrganizeFolders: Boolean? = null,
    val homePinnedPodcastIds: List<String>? = null,
    /** Shows hidden everywhere; absent in older backups, which leave the hidden shows as they are. */
    val hiddenShows: List<HiddenShowBackup>? = null,
    val latestEpisodesSortUseSmart: Boolean? = null,
    val skipBehavior: String? = null,
    val skipBeginningMs: Long? = null,
    val skipEndingMs: Long? = null,
    val seekBackwardMs: Long? = null,
    val seekForwardMs: Long? = null,
    val hideCompletedInFeeds: Boolean? = null,
    val hideCompletedInShowDetails: Boolean? = null,
    /** Show pages list episodes by title only; absent in older backups, which keep full rows. */
    val episodeTitlesOnly: Boolean? = null,
    val hideCompletedInHome: Boolean? = null,
    val hideCompletedInSubs: Boolean? = null,
    val restartForgottenEpisodes: Boolean? = null,
    val sameShowQueueOnly: Boolean? = null,
    /** Trim silence; absent in older backups, which leave the setting as it is. */
    val trimSilenceEnabled: Boolean? = null,
    val homeShortcutsInLibrary: Boolean? = null,
    val widgetAppearance: String? = null,
    val exploreDefaultTab: String? = null,
    val subscriptionsDefaultTab: String? = null,
    val subscriptionsTabStyle: String? = null,
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
    val version: Int = LibraryBackupDirectFeedLogic.VERSION,
    val subscriptions: List<PodcastEntity>,
    val history: List<ListeningHistoryEntity>,
    val globalPreferences: GlobalPreferencesBackup? = null,
    val adaptiveRanking: AdaptiveRankingBackup? = null,
    val directFeedOptIns: List<DirectFeedOptInBackup>? = null,
    val folders: List<SubscriptionFolderBackup>? = null,
    /** Version 7+: episode aliases, group settings and renumbered shows ([EpisodeIdentityBackup]). */
    val episodeIdentity: EpisodeIdentityBackup? = null,
    /** Version 8+: listening sessions, daily summaries and played shows ([ListeningBackup]). */
    val listening: ListeningBackup? = null,
    /** Version 11+: playlists with their shows and settings ([PlaylistBackup]). */
    val playlists: List<PlaylistBackup>? = null,
)

data class OpmlFeed(val title: String, val xmlUrl: String,)

// The last parameters are test seams that default to the app's shared dependencies.
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList", "kotlin:S107")
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
    private val folderRepository: FolderRepository? = null,
    private val database: BoxLoreDatabase? = null,
    private val renumberedShows: RenumberedShows? = null,
    private val deviceIdentity: DeviceIdentityPort? = null,
    private val legacyListeningOrigin: DeviceIdentityPort? = null,
    private val pendingOpmlImports: PendingOpmlImports =
        SharedAppDependenciesHolder.instance?.pendingOpmlImports ?: MemoryPendingOpmlImports(),
    private val playlistRepository: PlaylistRepository? = SharedAppDependenciesHolder.instance?.playlistRepository,
) {
    private val context = context.applicationContext
    private val rssFeedClient = RssFeedClient()
    private val gson: Gson =
        GsonBuilder()
            .setPrettyPrinting()
            .addSerializationExclusionStrategy(LibraryBackupFieldPolicy)
            .create()

    suspend fun exportLibraryAsJson(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val allHistory = listeningHistory.exportStoredHistory()

        val globalPrefs =
            if (userPrefs != null) {
                GlobalPreferencesBackup(
                    region = userPrefs.regionStream.first(),
                    contentLanguages = userPrefs.contentLanguagesStream.first(),
                    contentEnglishOff = userPrefs.contentEnglishOffStream.first(),
                    themeConfig = userPrefs.themeConfigStream.first(),
                    themeBrand = userPrefs.themeBrandStream.first(),
                    surfaceStyle = userPrefs.surfaceStyleStream.first(),
                    useDynamicColor = userPrefs.useDynamicColorStream.first(),
                    fontRoundness = userPrefs.fontRoundnessStream.first(),
                    navigationStyle = userPrefs.navigationStyleStream.first(),
                    openAppTo = userPrefs.openAppToStream.first(),
                    subscriptionSort = userPrefs.subscriptionSortStream.first(),
                    subscriptionFolderSort = userPrefs.subscriptionFolderSortStream.first(),
                    subscriptionIntraFolderSort = userPrefs.subscriptionIntraFolderSortStream.first(),
                    subscriptionManualOrder = userPrefs.subscriptionManualOrderStream.first(),
                    subscriptionFolderManualOrder = userPrefs.subscriptionFolderManualOrderStream.first(),
                    autoOrganizeFolders = userPrefs.autoOrganizeFoldersStream.first(),
                    homePinnedPodcastIds = userPrefs.homePinnedPodcastIdsStream.first(),
                    hiddenShows = userPrefs.hiddenShows.showsStream.first().toHiddenShowBackups(),
                    latestEpisodesSortUseSmart = userPrefs.latestEpisodesSortUseSmartStream.first(),
                    skipBehavior = userPrefs.skipBehaviorStream.first(),
                    skipBeginningMs = userPrefs.skipBeginningMsStream.first(),
                    skipEndingMs = userPrefs.skipEndingMsStream.first(),
                    seekBackwardMs = userPrefs.seekBackwardMsStream.first(),
                    seekForwardMs = userPrefs.seekForwardMsStream.first(),
                    hideCompletedInFeeds = userPrefs.hideCompletedInFeedsStream.first(),
                    hideCompletedInShowDetails = userPrefs.hideCompletedInShowDetailsStream.first(),
                    episodeTitlesOnly = userPrefs.episodeTitlesOnlyStream.first(),
                    hideCompletedInHome = userPrefs.hideCompletedInHomeStream.first(),
                    hideCompletedInSubs = userPrefs.hideCompletedInSubsStream.first(),
                    restartForgottenEpisodes = userPrefs.restartForgottenEpisodesStream.first(),
                    sameShowQueueOnly = userPrefs.sameShowQueueOnlyStream.first(),
                    trimSilenceEnabled = userPrefs.trimSilenceEnabledStream.first(),
                    homeShortcutsInLibrary = userPrefs.homeShortcutsInLibraryStream.first(),
                    widgetAppearance = userPrefs.widgetAppearanceStream.first(),
                    exploreDefaultTab = userPrefs.exploreDefaultTabStream.first(),
                    subscriptionsDefaultTab = userPrefs.subscriptionsDefaultTabStream.first(),
                    subscriptionsTabStyle = userPrefs.subscriptionsTabStyleStream.first(),
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
        val folderRepo = folderRepository ?: SharedAppDependenciesHolder.instance?.folderRepository
        val exportedFolders = folderRepo?.getFolders()?.map(LibraryBackupFolderLogic::toBackup).orEmpty()
        val episodeIdentity = identity()?.export()
        val listening = listening()?.export()
        val playlists = playlistRepository?.let { LibraryBackupPlaylists.export(it) }
        val backup =
            BoxLoreBackup(
                version = LibraryBackupDirectFeedLogic.VERSION,
                subscriptions = subscriptions,
                history = allHistory,
                globalPreferences = globalPrefs,
                adaptiveRanking = rankingBackup,
                directFeedOptIns = null,
                folders = exportedFolders,
                episodeIdentity = episodeIdentity,
                listening = listening,
                playlists = playlists,
            )
        return gson.toJson(backup)
    }

    suspend fun exportLibraryAsOpml(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>boxlore subscriptions</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        sb.append("    <outline text=\"Subscriptions\" title=\"Subscriptions\">\n")
        for (entity in subscriptions) {
            val title = escapeXml(entity.title)
            val feedUrl = escapeXml(LibraryBackupOpml.publisherFeedUrl(entity) ?: continue)
            sb.append("      <outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"$feedUrl\" />\n")
        }
        sb.append("    </outline>\n")
        sb.append("  </body>\n")
        sb.append("</opml>")
        return sb.toString()
    }

    /** The database passed in, else the app's; null only before the app has set up its dependencies. */
    private fun backupDatabase(): BoxLoreDatabase? = database ?: SharedAppDependenciesHolder.instance?.database

    private fun identity(): LibraryBackupIdentity? {
        val shared = SharedAppDependenciesHolder.instance
        val db = backupDatabase() ?: return null
        return LibraryBackupIdentity(db, renumberedShows ?: shared?.renumberedShows ?: RenumberedShows.NONE)
    }

    private fun listening(): LibraryBackupListening? {
        val shared = SharedAppDependenciesHolder.instance
        val db = backupDatabase() ?: return null
        val deviceId = runCatching { (deviceIdentity ?: shared?.listeningDeviceIdentityPort)?.getDeviceId() }.getOrNull()
        val legacyOrigin = runCatching { (legacyListeningOrigin ?: shared?.listeningLegacyOriginPort)?.getDeviceId() }.getOrNull()
        return LibraryBackupListening(db, deviceId, legacyOrigin)
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

    suspend fun importLibraryFromJson(
        jsonString: String,
        onProgress: suspend (JsonBackupProgress) -> Unit = {},
    ): Pair<Int, Boolean> = try {
        onProgress(JsonBackupProgress(phase = JsonBackupPhase.PREPARING))
        val backup = gson.fromJson(jsonString, BoxLoreBackup::class.java)
        restoreImportedGlobalPreferences(backup.globalPreferences)
        val identity = identity()
        identity?.restoreRenumberedShows(backup.episodeIdentity)
        val titleMap = backup.subscriptions.associate { it.podcastId to it.title }
        val importedIds = mutableListOf<String>()
        // Shows followed without a live feed read; the refresh step skips them, so it never waits on
        // the same feed twice and the normal RSS refresh still sees them as due.
        val unreadRssIds = mutableSetOf<String>()
        for (entity in backup.subscriptions) {
            importBackupSubscription(entity, backup, unreadRssIds)?.let { importedIds += it }
        }
        val folderRepo = folderRepository ?: SharedAppDependenciesHolder.instance?.folderRepository
        if (folderRepo != null) {
            LibraryBackupFolderLogic.restoreFolders(backup.folders, folderRepo)
            folderRepo.syncLinkedGenres()
        }
        // Identity first, so the history restore skips IDs this phone holds for another episode.
        val refusedIds = identity?.restore(backup.episodeIdentity).orEmpty()
        restoreImportedHistory(backup.history, importedIds, refusedIds)
        listening()?.restore(backup.listening, refusedIds, importedIds)
        relinkImportedSwitchedShows(importedIds)
        playlistRepository?.let { LibraryBackupPlaylists.restore(backup.playlists, it) }
        backup.adaptiveRanking?.let { rankingBackup ->
            adaptiveRankingRepository.restoreBackup(rankingBackup)
        }
        refreshImportedLatestEpisodes(
            importedIds = importedIds.filterNot { it in unreadRssIds },
            backupOptIns = backup.directFeedOptIns,
            titleMap = titleMap,
            onProgress = onProgress,
        )
        onProgress(
            JsonBackupProgress(
                phase = JsonBackupPhase.COMPLETED,
                current = importedIds.size,
                total = importedIds.size,
            ),
        )
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
        // One write with the English choice, so a fourth pick saved while English was off isn't cut.
        prefs.contentLanguages.writePref { up.setContentLanguages(it, englishOff = prefs.contentEnglishOff == true) }
        prefs.themeConfig.writePref { up.setThemeConfig(it) }
        prefs.themeBrand.writePref { up.setThemeBrand(it) }
        prefs.surfaceStyle.writePref { up.setSurfaceStyle(it) }
        prefs.useDynamicColor.writePref { up.setUseDynamicColor(it) }
        prefs.fontRoundness.writePref { up.setFontRoundness(it) }
        prefs.navigationStyle.writePref { up.setNavigationStyle(it) }
        prefs.openAppTo.writePref { up.setOpenAppTo(it) }
        prefs.subscriptionSort.writePref { up.setSubscriptionSort(it) }
        prefs.subscriptionFolderSort.writePref { up.setSubscriptionFolderSort(it) }
        prefs.subscriptionIntraFolderSort.writePref { up.setSubscriptionIntraFolderSort(it) }
        prefs.subscriptionManualOrder.writePref { up.setSubscriptionManualOrder(it) }
        prefs.subscriptionFolderManualOrder.writePref { up.setSubscriptionFolderManualOrder(it) }
        prefs.autoOrganizeFolders.writePref { up.setAutoOrganizeFolders(it) }
        prefs.homePinnedPodcastIds.writePref { up.setHomePinnedPodcastIds(it) }
        // Added to the shows already hidden here, never replacing them.
        prefs.hiddenShows.writePref { up.hiddenShows.setAll(it.toHiddenShows() + up.hiddenShows.showsStream.first()) }
        prefs.latestEpisodesSortUseSmart.writePref { up.setLatestEpisodesSortUseSmart(it) }
        prefs.skipBehavior.writePref { up.setSkipBehavior(it) }
        prefs.skipBeginningMs.writePref { up.setSkipBeginningMs(it) }
        prefs.skipEndingMs.writePref { up.setSkipEndingMs(it) }
        prefs.seekBackwardMs.writePref { up.setSeekBackwardMs(it) }
        prefs.seekForwardMs.writePref { up.setSeekForwardMs(it) }
        prefs.hideCompletedInFeeds.writePref { up.setHideCompletedInFeeds(it) }
        prefs.hideCompletedInShowDetails.writePref { up.setHideCompletedInShowDetails(it) }
        prefs.episodeTitlesOnly.writePref { up.setEpisodeTitlesOnly(it) }
        prefs.hideCompletedInHome.writePref { up.setHideCompletedInHome(it) }
        prefs.hideCompletedInSubs.writePref { up.setHideCompletedInSubs(it) }
        prefs.restartForgottenEpisodes.writePref { up.setRestartForgottenEpisodes(it) }
        prefs.sameShowQueueOnly.writePref { up.setSameShowQueueOnly(it) }
        prefs.trimSilenceEnabled.writePref { up.setTrimSilenceEnabled(it) }
        prefs.homeShortcutsInLibrary.writePref { up.setHomeShortcutsInLibrary(it) }
        prefs.widgetAppearance.writePref { up.setWidgetAppearance(it) }
        prefs.exploreDefaultTab.writePref { up.setExploreDefaultTab(it) }
        prefs.subscriptionsDefaultTab.writePref { up.setSubscriptionsDefaultTab(it) }
        prefs.subscriptionsTabStyle.writePref { up.setSubscriptionsTabStyle(it) }
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
        unreadRssIds: MutableSet<String>,
    ): String? {
        val podcast =
            if (entity.sourceType == PodcastEntity.SOURCE_RSS) {
                importRssBackupSubscription(entity, unreadRssIds) ?: return null
            } else {
                importPiBackupSubscription(entity)
            }
        applyImportedShowSettings(podcast, entity, backup)
        return podcast.id
    }

    /**
     * Follows an `rss:` show again under its backed-up ID, reading its feed first so the show gets the
     * feed's current address, details and episodes. When the feed can't be read now, the show is still
     * followed from the backup's saved row ([LibraryBackupShowRestore.unreadRssPodcast]), so it and its
     * history stay, and its ID goes into [unreadRssIds].
     */
    private suspend fun importRssBackupSubscription(
        entity: PodcastEntity,
        unreadRssIds: MutableSet<String>,
    ): cx.aswin.boxlore.core.model.Podcast? {
        val feedUrl = entity.feedUrl
        if (feedUrl.isNullOrBlank()) return null
        val rssPodcast =
            LibraryBackupImportLogic.runRestore(
                block = {
                    rssPodcastRepository
                        .restoreSubscription(feedUrl, entity.podcastId)
                        .podcast
                },
                onFailure = { error ->
                    Log.e("JSON_IMPORT", "RSS feed unreadable for ${entity.title}; restored from the backup", error)
                },
            ) ?: return importUnreadRssBackupSubscription(entity)?.also { unreadRssIds += it.id }
        val subscribedRssPodcast =
            rssPodcast.copy(
                preferredSort = entity.preferredSort,
                linkedPodcastIndexId = entity.linkedPodcastIndexId,
                skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
                skipEndingOverrideMs = entity.skipEndingOverrideMs,
                playbackSpeedOverride = entity.playbackSpeedOverride,
                skipSilenceOverride = entity.skipSilenceOverride,
                customGenre = entity.customGenre,
                customGenreIcon = entity.customGenreIcon,
            )
        subscriptionRepository.restoreSubscription(
            podcast = subscribedRssPodcast,
            subscribedAt = entity.subscribedAt,
        )
        return subscribedRssPodcast
    }

    private suspend fun importUnreadRssBackupSubscription(entity: PodcastEntity): cx.aswin.boxlore.core.model.Podcast? {
        val podcast = LibraryBackupShowRestore.unreadRssPodcast(entity) ?: return null
        subscriptionRepository.restoreSubscription(podcast = podcast, subscribedAt = entity.subscribedAt)
        return podcast
    }

    private suspend fun importPiBackupSubscription(entity: PodcastEntity): cx.aswin.boxlore.core.model.Podcast {
        val podcast = LibraryBackupShowRestore.podcastFromBackup(entity)
        subscriptionRepository.restoreSubscription(
            podcast = podcast,
            subscribedAt = entity.subscribedAt,
        )
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
        if (backup.version >= LibraryBackupDirectFeedLogic.SHOW_PLAYBACK_SINCE_VERSION) {
            subscriptionRepository.setPlaybackSpeedOverride(podcast.id, entity.playbackSpeedOverride)
            subscriptionRepository.setSkipSilenceOverride(podcast.id, entity.skipSilenceOverride)
        }
        if (entity.customGenre != null || entity.customGenreIcon != null) {
            subscriptionRepository.updateCustomGenre(
                podcast.id,
                entity.customGenre,
                entity.customGenreIcon,
            )
        }
    }

    private suspend fun restoreImportedHistory(history: List<ListeningHistoryEntity>, importedIds: List<String>, refusedIds: Set<String>,) {
        val restorable =
            history.filter { entity ->
                val unimportedRss = entity.podcastId.startsWith("rss:") && entity.podcastId !in importedIds
                // Gson can write JSON null into the non-null ID; such a row is sanitized below.
                !unimportedRss && (entity.episodeId as String?) !in refusedIds
            }
        for (entity in restorable) {
            listeningHistory.upsertHistoryEntity(LibraryBackupHistoryRestore.sanitize(entity))
        }
    }

    /**
     * Links each imported RSS show that replaced a Podcast Index show with the episodes the restored
     * history holds under that show (`RssPodcastRepository.relinkSwitchedShow`), once the history is
     * in. A version 7 backup also brings the `rss_source_link` aliases and the old IDs' keys; older
     * backups carry only the show's link, so the aliases are written again here.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
     */
    private suspend fun relinkImportedSwitchedShows(importedIds: List<String>) {
        for (id in importedIds.filter { it.startsWith("rss:") }) {
            rssPodcastRepository.relinkSwitchedShow(id)
        }
    }

    private suspend fun refreshImportedLatestEpisodes(
        importedIds: List<String>,
        backupOptIns: List<DirectFeedOptInBackup>?,
        titleMap: Map<String, String> = emptyMap(),
        onProgress: suspend (JsonBackupProgress) -> Unit = {},
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
        val total = refreshPlan.directFeedTargets.size + refreshPlan.piSyncIds.size + refreshPlan.rssIds.size
        if (total == 0) return

        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        suspend fun resolveTitle(id: String): String =
            titleMap[id]?.ifBlank { null } ?: subscriptionRepository.getPodcastEntity(id)?.title.orEmpty()

        val initialTargetId = refreshPlan.directFeedTargets.firstOrNull()?.podcastId.orEmpty().ifBlank {
            refreshPlan.rssIds.firstOrNull() ?: refreshPlan.piSyncIds.firstOrNull().orEmpty()
        }
        val initialTitle = if (initialTargetId.isNotBlank()) resolveTitle(initialTargetId) else ""

        onProgress(
            JsonBackupProgress(
                phase = JsonBackupPhase.REFRESHING_FEEDS,
                current = 0,
                total = total,
                currentTitle = initialTitle,
            ),
        )

        LibraryBackupDirectFeedLogic.runPostSubscribeRefresh(
            plan = refreshPlan,
            restoreDirectFeeds = { targets ->
                restoreImportedDirectFeeds(
                    targets = targets,
                    onTargetStarted = { id ->
                        val title = resolveTitle(id)
                        onProgress(
                            JsonBackupProgress(
                                phase = JsonBackupPhase.REFRESHING_FEEDS,
                                current = completedCount.get(),
                                total = total,
                                currentTitle = title,
                            ),
                        )
                    },
                    onTargetCompleted = { id ->
                        val done = completedCount.incrementAndGet()
                        val title = resolveTitle(id)
                        onProgress(
                            JsonBackupProgress(
                                phase = JsonBackupPhase.REFRESHING_FEEDS,
                                current = done,
                                total = total,
                                currentTitle = title,
                            ),
                        )
                    },
                )
            },
            syncPi = { ids ->
                syncPiSubscriptionsWithProgress(ids, total, completedCount, ::resolveTitle, onProgress)
            },
            refreshRss = { ids ->
                refreshRssCatalogsWithProgress(ids, total, completedCount, ::resolveTitle, onProgress)
            },
        )
    }

    private suspend fun syncPiSubscriptionsWithProgress(
        ids: List<String>,
        total: Int,
        completedCount: java.util.concurrent.atomic.AtomicInteger,
        resolveTitle: suspend (String) -> String,
        onProgress: suspend (JsonBackupProgress) -> Unit,
    ) {
        for (id in ids) {
            val title = resolveTitle(id)
            onProgress(
                JsonBackupProgress(
                    phase = JsonBackupPhase.REFRESHING_FEEDS,
                    current = completedCount.get(),
                    total = total,
                    currentTitle = title,
                ),
            )
        }
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
        val done = completedCount.addAndGet(ids.size)
        onProgress(
            JsonBackupProgress(
                phase = JsonBackupPhase.REFRESHING_FEEDS,
                current = done,
                total = total,
                currentTitle = "",
            ),
        )
    }

    private suspend fun refreshRssCatalogsWithProgress(
        rssIds: List<String>,
        total: Int,
        completedCount: java.util.concurrent.atomic.AtomicInteger,
        resolveTitle: suspend (String) -> String,
        onProgress: suspend (JsonBackupProgress) -> Unit,
    ) {
        for (id in rssIds) {
            val title = resolveTitle(id)
            onProgress(
                JsonBackupProgress(
                    phase = JsonBackupPhase.REFRESHING_FEEDS,
                    current = completedCount.get(),
                    total = total,
                    currentTitle = title,
                ),
            )
            try {
                rssPodcastRepository.refreshCatalogIfNeeded(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("JSON_IMPORT", "RSS catalog refresh failed for $id", e)
            }
            val done = completedCount.incrementAndGet()
            onProgress(
                JsonBackupProgress(
                    phase = JsonBackupPhase.REFRESHING_FEEDS,
                    current = done,
                    total = total,
                    currentTitle = title,
                ),
            )
        }
    }

    suspend fun importFromOpml(inputStream: InputStream): Int = LibraryBackupImportLogic.opmlImportCount(
        onFailure = { error -> Log.e("OPML_IMPORT", "Failed to import OPML", error) },
    ) {
        importOpmlFeeds(parseOpmlFeeds(inputStream)).imported.size
    }

    /**
     * Imports [feeds] in order, waiting for room in the route budget before each request, so a large
     * file isn't cut short by the API's rate limits ([OpmlImportRun]). The screen sends requests for
     * [OpmlScreenPace.SCREEN_BUDGET_MS] at most: outlines it didn't get to, and those the API still
     * couldn't answer for, are returned as [OpmlImportReport.pending] and finished later by
     * [finishPendingOpmlImports]; an import that followed nothing keeps none pending. One OPML run
     * at a time in the process ([opmlRuns]); a catch-up running gives way to it.
     */
    suspend fun importOpmlFeeds(
        feeds: List<OpmlFeed>,
        onProgress: suspend (index: Int, feed: OpmlFeed, imported: List<cx.aswin.boxlore.core.model.Podcast>) -> Unit = { _, _, _ -> },
    ): OpmlImportReport {
        // Shown while a catch-up finishes the outline it is on.
        feeds.firstOrNull()?.let { onProgress(0, it, emptyList()) }
        return opmlRuns.screen {
            val pace = OpmlScreenPace(podcastRepository.routeBudget)
            val resolver = OpmlCatalogResolver(podcastRepository, rssFeedClient, pace::pace)
            OpmlImportRun.run(
                feeds,
                pendingOpmlImports,
                forgetWhenNothingImported = true,
                importOne = { feed -> if (pace.expired()) OpmlFeedImport.Pending else importOpmlFeed(feed, resolver) },
                onProgress = onProgress,
            )
        }
    }

    /**
     * Finishes outlines an earlier import left pending (the API couldn't answer, the screen's time ran
     * out, or the app closed mid-import), paced like the import itself. Run by the foreground
     * catch-up; what it can't finish stays pending for a later one, which waits longer after each
     * miss ([OpmlImportRun.retryWaitMs]), and an outline the API never answers for is given up on in
     * the end ([OpmlImportRun.GIVE_UP_AFTER_MISSES]). Does nothing while an import runs, and stops
     * after its current outline when the listener starts one.
     */
    suspend fun finishPendingOpmlImports(): OpmlImportReport {
        val nothing = OpmlImportReport(emptyList(), emptyList())
        if (pendingOpmlImports.all().isEmpty()) return nothing
        return opmlRuns.catchUp { yieldToScreen ->
            val pace = OpmlCatchUpPace(podcastRepository.routeBudget, yieldToScreen)
            val resolver = OpmlCatalogResolver(podcastRepository, rssFeedClient, pace::pace)
            OpmlImportRun
                .run(
                    pendingOpmlImports.all(),
                    pendingOpmlImports,
                    forgetWhenNothingImported = false,
                    importOne = { importOpmlFeed(it, resolver) },
                    countMisses = true,
                    shouldStop = yieldToScreen,
                ).also { report ->
                    report.gaveUp.forEach { Log.w("OPML_IMPORT", "Gave up on ${it.title}: Podcast Index never answered for it") }
                }
        } ?: nothing
    }

    /** The file's outlines with a feed address, each address once (first outline kept), so screens count what is imported. */
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
                    val text = parser.getAttributeValue(null, "text")
                    // An empty `text` falls back to `title`, so the outline's title search has a name.
                    val title = text?.takeIf { it.isNotBlank() } ?: parser.getAttributeValue(null, "title") ?: text

                    if (xmlUrl != null && title != null) {
                        feeds.add(OpmlFeed(title, xmlUrl))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to parse OPML feeds", e)
        }
        return feeds.distinctBy(OpmlFeed::xmlUrl)
    }

    /** Imports one outline; a show the catalog couldn't answer for yet is [OpmlFeedImport.Pending], not dropped. */
    private fun OpmlCatalogDecision.follows(): Boolean = this is OpmlCatalogDecision.Found || this == OpmlCatalogDecision.ConfirmedAbsent

    private suspend fun importOpmlFeed(feed: OpmlFeed, resolver: OpmlCatalogResolver): OpmlFeedImport {
        try {
            val resolution = resolver.resolve(feed)
            // An account switch clears the store: an outline it held is never followed into the new account.
            if (resolution.follows() && !pendingOpmlImports.contains(feed)) return OpmlFeedImport.Pending
            return when (resolution) {
                is OpmlCatalogDecision.Found -> {
                    subscriptionRepository.subscribe(resolution.podcast)
                    refreshImportedLatestEpisodes(
                        importedIds = listOf(resolution.podcast.id),
                        backupOptIns = null,
                    )
                    OpmlFeedImport.Imported(resolution.podcast)
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
                    )?.let { OpmlFeedImport.Imported(it) } ?: OpmlFeedImport.Skipped
                OpmlCatalogDecision.Deferred -> {
                    Log.w("OPML_IMPORT", "Deferring ${feed.title}; Podcast Index lookup did not settle")
                    OpmlFeedImport.Pending
                }
                OpmlCatalogDecision.Limited -> OpmlFeedImport.Limited
                OpmlCatalogDecision.Unresolvable -> {
                    Log.w("OPML_IMPORT", "Skipping ${feed.title}; its feed can't be read or names another show")
                    OpmlFeedImport.Skipped
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to import single feed: ${feed.title}", e)
        }
        return OpmlFeedImport.Skipped
    }

    private fun supplementPort(): EpisodeSupplementPort? = episodeSupplementPort ?: podcastRepository.episodeSupplementRepository

    private suspend fun restoreImportedLocalCatalogs(
        targets: List<DirectFeedOptInBackup>,
        catalog: LocalEpisodeCatalogPort,
        onTargetStarted: (suspend (podcastId: String) -> Unit)? = null,
        onTargetCompleted: (suspend (podcastId: String) -> Unit)? = null,
    ) {
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets = targets,
            actions =
            DirectFeedRestoreActions(
                restoreStub = { _, _ -> },
                ensureFeedUrl = { id, url -> subscriptionRepository.ensureHttpsFeedUrl(id, url, FeedUrlSources.BACKUP_RESTORE) },
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
            onTargetStarted = onTargetStarted,
            onTargetCompleted = onTargetCompleted,
        )
    }

    private suspend fun restoreImportedDirectFeeds(
        targets: List<DirectFeedOptInBackup>,
        onTargetStarted: (suspend (podcastId: String) -> Unit)? = null,
        onTargetCompleted: (suspend (podcastId: String) -> Unit)? = null,
    ) {
        val catalog = podcastRepository.localEpisodeCatalog
        if (catalog != null) {
            restoreImportedLocalCatalogs(
                targets = targets,
                catalog = catalog,
                onTargetStarted = onTargetStarted,
                onTargetCompleted = onTargetCompleted,
            )
            return
        }
        val port = supplementPort() ?: return
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets = targets,
            actions =
            DirectFeedRestoreActions(
                restoreStub = { _, _ -> },
                ensureFeedUrl = { id, url -> subscriptionRepository.ensureHttpsFeedUrl(id, url, FeedUrlSources.BACKUP_RESTORE) },
                invalidateCache = { id -> podcastRepository.invalidateEpisodesCache(id) },
                refreshFeed = { id, url ->
                    val entity = subscriptionRepository.getPodcastEntity(id)
                    port.refreshFromFeed(
                        EpisodeSupplementPort.RefreshFromFeedRequest(
                            podcastIndexId = id,
                            feedUrl = url,
                            loadBaseline = { podcastRepository.loadPiEpisodesForSupplement(id) },
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
            onTargetStarted = onTargetStarted,
            onTargetCompleted = onTargetCompleted,
        )
    }

    /**
     * Marks every episode of [podcast] played. Its pages are read paced under the route's rate limit
     * in one ID scheme ([WholeShowRead]); a page that still can't be read leaves the episodes read so
     * far marked and the rest as they were (logged). False when the show couldn't be read in full.
     */
    suspend fun markAllEpisodesCompleted(podcast: cx.aswin.boxlore.core.model.Podcast): Boolean {
        try {
            val read =
                WholeShowRead.read { offset, scheme ->
                    podcastRepository.getEpisodesPaginated(
                        feedId = podcast.id,
                        limit = LOCAL_CATALOG_WINDOW_BOUND,
                        offset = offset,
                        sort = "newest",
                        strict = true,
                        scheme = scheme,
                    )
                }
            val episodes = read.episodes
            if (!read.complete) Log.w("OPML_IMPORT", "Marked only the newest ${episodes.size} episodes of ${podcast.title}: a page couldn't be read")
            if (episodes.isNotEmpty()) {
                listeningHistory.markAllEpisodesCompleted(
                    episodes = episodes,
                    podcastId = podcast.id,
                    podcastTitle = podcast.title,
                    podcastImageUrl = podcast.imageUrl,
                )
            }
            return read.complete
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to mark all episodes completed for: ${podcast.title}", e)
            return false
        }
    }
}

/** The import screen's OPML run and the catch-up's share pending outlines, so only one runs at a time. */
private val opmlRuns = OpmlRunGate()
