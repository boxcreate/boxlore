package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.rss.RenumberedShows
import cx.aswin.boxlore.core.rss.RssPodcastRepository

/**
 * Process-wide façade of Application-scoped instances that workers and Media3 services
 * may consume without rebuilding a parallel repository graph.
 *
 * Installed once from [AppContainer] via [SharedAppDependenciesHolder] in Application.onCreate.
 * Do not construct ranking/RSS repositories ad hoc — use these instances.
 *
 * Download-owned types (DownloadRepository, SmartDownloadManager) live in
 * [cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder] to avoid a
 * `:core:data` ↔ `:core:downloads` cycle.
 */
interface SharedAppDependencies {
    val database: BoxLoreDatabase
    val podcastRepository: PodcastRepository
    val subscriptionRepository: SubscriptionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val rssPodcastRepository: RssPodcastRepository
    val adaptiveCandidateScorer: AdaptiveCandidateScorer
    val rankingFeedbackRepository: RankingFeedbackRepository
    val adaptiveRankingRepository: AdaptiveRankingRepository
    val rankingRuntimeControls: RankingRuntimeControls
    val historyRecommendationSource: HistoryRecommendationSource
    val subscriptionForegroundSync: SubscriptionForegroundSync
    val folderRepository: FolderRepository
    val deviceIdentityPort: cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort? get() = null
    val userSyncCoordinator: cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator? get() = null

    /**
     * The one-time repair of saved feed addresses after the update, run by the app's
     * `UpgradeRepairWorker`; null when the composition root doesn't provide it (tests).
     * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
     */
    val upgradeAddressRepair: UpgradeAddressRepair? get() = null

    /**
     * Shows Podcast Index renumbered (`boxlore_renumbered_shows`), which the JSON library backup
     * exports and restores; [RenumberedShows.NONE] when the composition root doesn't provide it (tests).
     * Added in P4 PR 17 of the own-episode-IDs plan (plan section 8, JSON backup v7).
     */
    val renumberedShows: RenumberedShows get() = RenumberedShows.NONE
}

/**
 * Holder for the single [SharedAppDependencies] installed by the Application composition root.
 *
 * Workers and [cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService] call [require]
 * instead of constructing PodcastRepository / ranking / RSS graphs themselves.
 */
object SharedAppDependenciesHolder {
    @Volatile
    var instance: SharedAppDependencies? = null

    fun require(): SharedAppDependencies = instance
        ?: error(
            "SharedAppDependencies not installed. " +
                "Set SharedAppDependenciesHolder.instance from Application after creating AppContainer.",
        )
}
