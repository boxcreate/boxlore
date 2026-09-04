package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort

/** JSON field for PI shows opted into Missing episodes? (backup version 6+). */
data class DirectFeedOptInBackup(val podcastId: String? = null, val feedUrl: String? = null,)

/** Post-subscribe catalog refresh: direct-feed restore, then PI `/sync`, then RSS. */
internal data class LibraryBackupRefreshPlan(
    val directFeedTargets: List<DirectFeedOptInBackup>,
    val piSyncIds: List<String>,
    val rssIds: List<String>,
)

/** Pure helpers for exporting and targeting direct-feed opt-ins in library JSON. */
internal object LibraryBackupDirectFeedLogic {
    const val VERSION = 6

    fun mergeExport(
        portOptIns: List<EpisodeSupplementPort.DirectFeedOptIn>,
        subscriptionFeedUrls: Map<String, String?>,
    ): List<DirectFeedOptInBackup> = portOptIns
        .mapNotNull { optIn ->
            val id = optIn.podcastIndexId.trim()
            if (id.isEmpty() || id.startsWith("rss:")) return@mapNotNull null
            val url =
                TrackedPodcastRtdbLogic.httpsFeedUrl(optIn.feedUrl)
                    ?: TrackedPodcastRtdbLogic.httpsFeedUrl(subscriptionFeedUrls[id])
                    ?: return@mapNotNull null
            DirectFeedOptInBackup(podcastId = id, feedUrl = url)
        }
        .distinctBy { it.podcastId }
        .sortedBy { it.podcastId }

    fun restoreTargets(backupOptIns: List<DirectFeedOptInBackup>?, importedIds: Collection<String>,): List<DirectFeedOptInBackup> {
        val imported = importedIds.toSet()
        return backupOptIns
            .orEmpty()
            .mapNotNull { optIn ->
                // Gson can write JSON null into Kotlin String fields.
                val id = optIn.podcastId?.trim().orEmpty()
                if (id.isEmpty() || id.startsWith("rss:") || id !in imported) {
                    return@mapNotNull null
                }
                val url =
                    TrackedPodcastRtdbLogic.httpsFeedUrl(optIn.feedUrl) ?: return@mapNotNull null
                DirectFeedOptInBackup(podcastId = id, feedUrl = url)
            }
            .distinctBy { it.podcastId }
    }

    fun subscriptionTargets(importedIds: Collection<String>, subscriptionFeedUrls: Map<String, String?>,): List<DirectFeedOptInBackup> = importedIds
        .mapNotNull { id ->
            if (id.isBlank() || id.startsWith("rss:")) return@mapNotNull null
            val url =
                TrackedPodcastRtdbLogic.httpsFeedUrl(subscriptionFeedUrls[id])
                    ?: return@mapNotNull null
            DirectFeedOptInBackup(podcastId = id, feedUrl = url)
        }.distinctBy { it.podcastId }

    fun piSyncIds(importedIds: Collection<String>, restoredOptInIds: Set<String>,): List<String> = importedIds.filter { id ->
        id.isNotBlank() && !id.startsWith("rss:") && id !in restoredOptInIds
    }

    fun rssRefreshIds(importedIds: Collection<String>): List<String> = importedIds.filter(::isRssId)

    private fun isRssId(id: String): Boolean = id.startsWith("rss:") && id.isNotBlank()

    fun refreshPlan(
        importedIds: Collection<String>,
        backupOptIns: List<DirectFeedOptInBackup>?,
        subscriptionFeedUrls: Map<String, String?> = emptyMap(),
    ): LibraryBackupRefreshPlan {
        val targets =
            (
                restoreTargets(backupOptIns, importedIds) +
                    subscriptionTargets(importedIds, subscriptionFeedUrls)
                ).distinctBy { it.podcastId }
        val restoredIds = targets.mapNotNull { it.podcastId }.toSet()
        return LibraryBackupRefreshPlan(
            directFeedTargets = targets,
            piSyncIds = piSyncIds(importedIds, restoredIds),
            rssIds = rssRefreshIds(importedIds),
        )
    }

    suspend fun runPostSubscribeRefresh(
        plan: LibraryBackupRefreshPlan,
        restoreDirectFeeds: suspend (List<DirectFeedOptInBackup>) -> Unit,
        syncPi: suspend (List<String>) -> Unit,
        refreshRss: suspend (List<String>) -> Unit,
    ) {
        restoreDirectFeeds(plan.directFeedTargets)
        if (plan.piSyncIds.isNotEmpty()) {
            syncPi(plan.piSyncIds)
        }
        if (plan.rssIds.isNotEmpty()) {
            refreshRss(plan.rssIds)
        }
    }
}
