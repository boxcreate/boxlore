package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.data.content.ContentSection
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.SmartHeroItem

internal fun adaptiveHistoryMaturityBucket(historyCount: Int): Int =
    when {
        historyCount <= 0 -> 0
        historyCount < 5 -> 1
        historyCount < 15 -> 2
        historyCount < 30 -> 3
        else -> 4
    }

internal fun discoverPodcastsExcluding(
    trending: List<Podcast>,
    heroItems: List<SmartHeroItem>,
    adaptiveSections: List<ContentSection>,
): List<Podcast>? {
    if (trending.isEmpty()) return null
    return trending.filter { podcast ->
        heroItems.none { it.podcast.id == podcast.id } &&
            adaptiveSections.none { section ->
                section.items.any { candidate -> candidate.podcast.id == podcast.id }
            }
    }
}
