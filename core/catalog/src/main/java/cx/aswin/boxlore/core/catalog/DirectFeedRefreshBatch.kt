package cx.aswin.boxlore.core.catalog

import java.util.concurrent.ConcurrentHashMap

/**
 * Picks the followed shows whose own feeds a [SubscriptionForegroundSync] pass reads: the open show
 * first, then the shows read longest ago, so every show gets its turn however large the library is
 * and however often the app restarts.
 *
 * An automatic pass reads only feeds past their wait, at most half the library rounded up, with that
 * cap kept between [AUTOMATIC_MIN] and [AUTOMATIC_MAX]. Pull to refresh ([isManualRefreshPass]) reads
 * every followed feed up to [MANUAL_MAX]; a larger library carries on with the next pull. The open
 * show takes a slot only when it is due.
 *
 * A feed's last read is the later of the catalog's saved read ([select]'s `lastReadAt`) and the
 * time its last read started in this process ([markTried]); a time later than now (a wrong device
 * clock) counts as never. So within one process a feed that keeps failing goes behind every due feed
 * not tried since: when a pass has fewer slots than due feeds it waits until those have been read,
 * and when a pass has slots to spare it is tried again. Tries are not
 * saved: after a restart a failed Podcast Index read is tried again at its saved read time, while an
 * `rss:` show's saved time ([cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.lastFeedReadAt],
 * `lastRssSyncAt`) already includes failed attempts, except a failed pull of a show not yet due.
 */
internal class DirectFeedRefreshBatch(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val automaticMin: Int = AUTOMATIC_MIN,
    private val automaticMax: Int = AUTOMATIC_MAX,
    private val manualMax: Int = MANUAL_MAX,
) {
    private val triedAt = ConcurrentHashMap<String, Long>()

    /** The shows to read this pass, in order. Records nothing: a read records its own try ([markTried]). */
    suspend fun select(
        ids: List<String>,
        preferred: String?,
        lastReadAt: suspend (String) -> Long,
        due: suspend (String) -> Boolean,
    ): List<String> {
        val limit = limitFor(ids.size, isManualRefreshPass())
        val now = nowMs()
        val first = preferred?.takeIf { it in ids && due(it) }
        val rest =
            ids
                .filter { it != preferred && due(it) }
                .map { id -> id to maxOf(pastOrNever(lastReadAt(id), now), pastOrNever(triedAt[id] ?: 0L, now)) }
                .sortedBy { (_, readAt) -> readAt }
                .map { (id, _) -> id }
        return (listOfNotNull(first) + rest).take(limit)
    }

    /** Records that a read of [id]'s feed started now. */
    fun markTried(id: String) {
        triedAt[id] = nowMs()
    }

    /** How many feeds a pass over a library of [libraryShows] followed feeds may read. */
    fun limitFor(libraryShows: Int, manual: Boolean): Int =
        if (manual) {
            manualMax
        } else {
            ((libraryShows + 1) / 2).coerceIn(automaticMin, automaticMax.coerceAtLeast(automaticMin))
        }

    private fun pastOrNever(at: Long, now: Long): Long = if (at > now) 0L else at

    companion object {
        /** An automatic pass may always read this many due feeds, so small libraries read in one pass. */
        const val AUTOMATIC_MIN = 40

        /** An automatic pass never reads more than this many feeds. */
        const val AUTOMATIC_MAX = 100

        /** Pull to refresh reads at most this many feeds; the largest libraries finish on the next pull. */
        const val MANUAL_MAX = 200
    }
}
