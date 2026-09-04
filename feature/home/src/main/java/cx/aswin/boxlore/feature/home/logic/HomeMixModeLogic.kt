package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.Episode

enum class HomeMixMode {
    DAILY,
    OFFLINE,
    ;

    val analyticsValue: String
        get() = name.lowercase()

    companion object {
        fun fromPersistedValue(value: String): HomeMixMode = entries.firstOrNull { it.analyticsValue == value } ?: DAILY
    }
}

object HomeMixModeLogic {
    const val HOME_ITEM_LIMIT = 15

    fun canOfferOffline(
        subscriptionCount: Int,
        completedDownloadCount: Int,
    ): Boolean = subscriptionCount >= 2 && completedDownloadCount >= 1

    fun resolveMode(
        requested: HomeMixMode,
        canOfferOffline: Boolean,
    ): HomeMixMode = if (requested == HomeMixMode.OFFLINE && !canOfferOffline) {
        HomeMixMode.DAILY
    } else {
        requested
    }

    fun visibleOfflineItems(items: List<CompletedDownloadItem>): List<CompletedDownloadItem> = items.take(HOME_ITEM_LIMIT)

    fun eligibleOfflineItems(
        items: List<CompletedDownloadItem>,
        completedEpisodeIds: Set<String>,
    ): List<CompletedDownloadItem> = items.filterNot { it.episode.id in completedEpisodeIds }

    fun queueEpisodes(
        mode: HomeMixMode,
        dailyEpisodes: List<Episode>,
        completedDownloads: List<CompletedDownloadItem>,
    ): List<Episode> = when (mode) {
        HomeMixMode.DAILY -> dailyEpisodes
        HomeMixMode.OFFLINE -> visibleOfflineItems(completedDownloads).map(CompletedDownloadItem::episode)
    }
}
