package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.database.UnfollowedCatalogCopy
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope

/**
 * The rules of a show page's stored feed copy, kept apart from I/O so they can be tested alone.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page; decision D10).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
object ShowPageFeedRules {
    /** How many of a feed's newest items a first read takes. */
    const val NEWEST_ITEMS = 100

    /** A stored copy is shown at once and read again with its validators once it is this old. */
    const val RECHECK_AFTER_MS = 60L * 60L * 1000L

    /** An unfollowed show's copy not opened for this long is removed. */
    const val UNUSED_AFTER_MS = 30L * 24L * 60L * 60L * 1000L

    /** At most this many unfollowed shows keep a copy… */
    const val MAX_UNFOLLOWED_SHOWS = 40

    /** …and at most this many episodes between them. */
    const val MAX_UNFOLLOWED_EPISODES = 20_000

    /** True when a read of the start of the feed can't give its newest items (oldest-first feeds). */
    fun readsWholeFeed(scope: Scope, cached: LocalEpisodeFeedEntity?): Boolean = scope == Scope.ARCHIVE || cached?.feedOrder == LocalFeedOrder.OLDEST_FIRST

    /**
     * The validators to send with a read, or null to read without them. A copy that holds only the
     * newest items has validators for the whole document, so a 304 to an archive read would only
     * say the start is current: archive reads of such a copy go out without validators, and a 304
     * to them is never taken as the archive ([acceptsNotModified]).
     */
    fun validators(wholeFeed: Boolean, cached: LocalEpisodeFeedEntity?): Pair<String?, String?>? {
        if (cached == null || cached.itemCount <= 0) return null
        if (wholeFeed && !cached.archiveComplete) return null
        if (cached.feedEtag.isNullOrBlank() && cached.feedLastModified.isNullOrBlank()) return null
        return cached.feedEtag to cached.feedLastModified
    }

    /** Whether a 304 answers this read: always for the newest items, for the archive only when the copy is already complete. */
    fun acceptsNotModified(wholeFeed: Boolean, cachedArchiveComplete: Boolean): Boolean = !wholeFeed || cachedArchiveComplete

    /** Copies to remove: not opened in [UNUSED_AFTER_MS], then the least recently opened over the caps. Never [keep]. */
    fun cleanupVictims(copies: List<UnfollowedCatalogCopy>, nowMillis: Long, keep: String?): List<String> {
        val candidates = copies.filter { it.podcastId != keep }
        val (stale, fresh) = candidates.partition { nowMillis - lastUse(it) > UNUSED_AFTER_MS }
        val keptEpisodes = copies.filter { it.podcastId == keep }.sumOf { it.episodeCount }
        var shows = copies.size - candidates.size
        var episodes = keptEpisodes
        val overCap = mutableListOf<String>()
        for (copy in fresh.sortedByDescending(::lastUse)) {
            if (shows + 1 > MAX_UNFOLLOWED_SHOWS || episodes + copy.episodeCount > MAX_UNFOLLOWED_EPISODES) {
                overCap += copy.podcastId
            } else {
                shows++
                episodes += copy.episodeCount
            }
        }
        return stale.map { it.podcastId } + overCap
    }

    private fun lastUse(copy: UnfollowedCatalogCopy): Long = maxOf(copy.lastOpenedAt, copy.fetchedAt)
}
