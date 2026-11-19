package cx.aswin.boxlore.core.catalog

/**
 * The foreground pass's lookup of followed Podcast Index shows that have no https address
 * (`GET /podcast`, one per show). Shows are taken least recently asked first, at most
 * [MAX_PER_PASS] a pass, and each is asked only when the route budget has room now
 * ([PacedPodcastLookups.whenRoom]), so the lookups never hold up the feed reads behind them nor run
 * into the API's rate limit. A show is stamped only once the server answered ([FeedUrlLookup]): a
 * 429, a server error, no connection or a full budget leaves it due for the next pass. An answer
 * with only an http address is stamped to wait [FeedUrlLookup.HTTP_ONLY_BACKOFF_MS]. A show whose
 * lookup was tried in this process goes behind the due shows not tried since, so shows that keep
 * failing can't hold the later ones back (the try is kept in memory only; the stamp is unchanged).
 */
internal object MissingFeedUrlRecovery {
    private val triedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    internal fun clearTriesForTests() = triedAt.clear()

    /** Below the route's per-minute budget, so the pass leaves room for show pages and other lookups. */
    const val MAX_PER_PASS = 20

    /** [ids]' Podcast Index shows without an https address whose lookup is due at [now], least recently asked first, at most [max]. */
    suspend fun dueTargets(
        ids: Set<String>,
        now: Long,
        feedUrlOf: suspend (String) -> String?,
        lastLookupAt: suspend (String) -> Long,
        max: Int = MAX_PER_PASS,
        lastTriedAt: (String) -> Long = { triedAt[it] ?: 0L },
    ): List<String> = ids
        .filterNot { it.startsWith("rss:") }
        .filter { id -> TrackedPodcastRtdbLogic.httpsFeedUrl(feedUrlOf(id)) == null }
        .map { id -> id to lastLookupAt(id) }
        .filter { (_, last) -> FeedUrlLookup.isDue(last, now) }
        .sortedWith(compareBy<Pair<String, Long>> { (id, last) -> maxOf(last, lastTriedAt(id)) }.thenBy { it.first })
        .take(max.coerceAtLeast(0))
        .map { it.first }

    /** Looks [id] up once; saves an https address it finds and stamps the lookup only when the server answered. */
    suspend fun recoverOne(
        id: String,
        now: Long,
        lookup: suspend (String) -> FeedUrlLookup,
        stamp: suspend (id: String, atMillis: Long) -> Unit,
        saveHttps: suspend (id: String, url: String) -> Unit,
    ) {
        triedAt[id] = now
        val answer = lookup(id) as? FeedUrlLookup.Answered ?: return
        answer.httpsFeedUrl?.let { saveHttps(id, it) }
        stamp(id, answer.stampAt(now))
    }
}
