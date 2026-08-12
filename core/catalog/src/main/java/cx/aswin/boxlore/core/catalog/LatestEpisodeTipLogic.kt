package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode

/**
 * Shared rule for Room `podcasts.latestEpisode` writes: never replace a newer tip
 * with an older PI or feed item. Same published date may replace when the id
 * changes (PI catch-up mapping a feed-only negative id onto the PI episode).
 */
internal object LatestEpisodeTipLogic {
    fun shouldReplace(existing: Episode?, incoming: Episode): Boolean {
        if (existing == null) return true
        if (incoming.publishedDate != existing.publishedDate) {
            return incoming.publishedDate > existing.publishedDate
        }
        return incoming.id != existing.id
    }
}
