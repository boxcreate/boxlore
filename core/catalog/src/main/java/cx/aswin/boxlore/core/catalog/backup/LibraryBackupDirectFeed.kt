package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort

/** JSON field for PI shows opted into Missing episodes? (backup version 6+). */
data class DirectFeedOptInBackup(
    val podcastId: String,
    val feedUrl: String,
)

/** Pure helpers for exporting and targeting direct-feed opt-ins in library JSON. */
internal object LibraryBackupDirectFeedLogic {
    const val VERSION = 6

    fun mergeExport(
        portOptIns: List<EpisodeSupplementPort.DirectFeedOptIn>,
        subscriptionFeedUrls: Map<String, String?>,
    ): List<DirectFeedOptInBackup> =
        portOptIns
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

    fun restoreTargets(
        backupOptIns: List<DirectFeedOptInBackup>?,
        importedIds: Collection<String>,
    ): List<DirectFeedOptInBackup> {
        val imported = importedIds.toSet()
        return backupOptIns
            .orEmpty()
            .mapNotNull { optIn ->
                val id = optIn.podcastId.trim()
                if (id.isEmpty() || id.startsWith("rss:") || id !in imported) {
                    return@mapNotNull null
                }
                val url =
                    TrackedPodcastRtdbLogic.httpsFeedUrl(optIn.feedUrl) ?: return@mapNotNull null
                DirectFeedOptInBackup(podcastId = id, feedUrl = url)
            }
            .distinctBy { it.podcastId }
    }

    fun piSyncIds(
        importedIds: Collection<String>,
        restoredOptInIds: Set<String>,
    ): List<String> =
        importedIds.filter { id ->
            id.isNotBlank() && !id.startsWith("rss:") && id !in restoredOptInIds
        }
}
