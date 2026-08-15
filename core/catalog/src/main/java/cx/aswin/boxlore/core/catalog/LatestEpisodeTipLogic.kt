package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode

/**
 * Shared rule for Room `podcasts.latestEpisode` writes: never replace a newer tip
 * with an older PI or feed item. Same published date is not new — id changes
 * must not badge or rewrite the tip (sticky identity).
 */
internal object LatestEpisodeTipLogic {
    fun shouldReplace(existing: Episode?, incoming: Episode): Boolean {
        if (existing == null) return true
        return incoming.publishedDate > existing.publishedDate
    }

    fun isNewerPublish(existing: Episode?, incoming: Episode): Boolean =
        existing != null && incoming.publishedDate > existing.publishedDate
}
