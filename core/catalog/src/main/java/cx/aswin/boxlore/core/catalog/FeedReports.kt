package cx.aswin.boxlore.core.catalog

import android.content.SharedPreferences
import android.util.Log
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2FeedReadTarget
import cx.aswin.boxlore.core.network.model.V2FeedReportRequest
import cx.aswin.boxlore.core.network.model.V2FeedReportResponse
import cx.aswin.boxlore.core.network.model.V2FeedReportStatus
import cx.aswin.boxlore.core.network.model.V2FetchVia
import kotlinx.coroutines.CancellationException
import retrofit2.awaitResponse

/**
 * When each show's broken feed was last reported (epoch ms), kept across launches so a show is
 * reported at most once a day however often the app starts.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
 */
interface FeedReportStamps {
    fun all(): Map<String, Long>

    fun mark(podcastId: String, atMillis: Long, keepSince: Long)
}

/** [FeedReportStamps] for one process only (tests, and callers built without the app's wiring). */
class MemoryFeedReportStamps : FeedReportStamps {
    private val stamps = mutableMapOf<String, Long>()

    @Synchronized
    override fun all(): Map<String, Long> = stamps.toMap()

    @Synchronized
    override fun mark(podcastId: String, atMillis: Long, keepSince: Long) {
        stamps.entries.removeAll { it.value < keepSince }
        stamps[podcastId] = atMillis
    }
}

/**
 * [FeedReportStamps] in SharedPreferences file [FILE_NAME]: one key per show ID, its last report
 * time as a Long. Entries older than a day are dropped on each write. Stable storage name: never rename.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
 */
class SharedPreferencesFeedReportStamps(private val prefs: SharedPreferences) : FeedReportStamps {
    override fun all(): Map<String, Long> = prefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()

    override fun mark(podcastId: String, atMillis: Long, keepSince: Long) {
        val editor = prefs.edit()
        for ((key, at) in all()) {
            if (at < keepSince) editor.remove(key)
        }
        editor.putLong(podcastId, atMillis).apply()
    }

    companion object {
        const val FILE_NAME = "boxlore_feed_reports"
    }
}

/**
 * Limits broken-feed reports: one per show per [PER_SHOW_INTERVAL_MS], and at most [DAILY_CAP] a
 * day in all, the API's own limit. Followed shows may use the whole day; the show page stops
 * [FOLLOWED_SHOWS_RESERVE] short, so followed shows always have room. Only a report the API
 * answered counts ([record]): a failed or refused send uses up nothing. Two sends at the same moment
 * can pass [allows] together; the API then answers the extra one with a 429, which counts nothing.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
 */
class FeedReportLimiter(private val stamps: FeedReportStamps) {
    /**
     * True when a report of [podcastId] may be sent at [nowMillis]: the show wasn't reported in the
     * last day, and the day's reports leave [keepFree] of [DAILY_CAP] for other callers (the show
     * page keeps [FOLLOWED_SHOWS_RESERVE] free for followed shows, P4 PR 16). Limiters sharing one
     * store lock on it, so each one counts the reports the others recorded.
     */
    fun allows(podcastId: String, nowMillis: Long, keepFree: Int = 0): Boolean = synchronized(stamps) {
        val recent = recent(nowMillis)
        podcastId !in recent && recent.size < DAILY_CAP - keepFree.coerceIn(0, DAILY_CAP)
    }

    /** Counts a report of [podcastId] the API answered at [nowMillis]. */
    fun record(podcastId: String, nowMillis: Long) = synchronized(stamps) {
        stamps.mark(podcastId, nowMillis, keepSince = nowMillis - PER_SHOW_INTERVAL_MS)
    }

    private fun recent(nowMillis: Long): Map<String, Long> {
        val since = nowMillis - PER_SHOW_INTERVAL_MS
        return stamps.all().filterValues { it > since && it <= nowMillis }
    }

    companion object {
        const val PER_SHOW_INTERVAL_MS = 24L * 60L * 60L * 1000L

        /** The API's limit: 20 reports a day from one phone. */
        const val DAILY_CAP = 20

        /** Reports of the day the show page leaves to followed shows (P4 PR 16). */
        const val FOLLOWED_SHOWS_RESERVE = 5
    }
}

/**
 * Tells the API that a followed show's feed couldn't be read (`POST /v2/feed-report`), which
 * queues a re-check of the show's address there, and returns the address the API has now when it
 * is a different HTTPS address the phone can read itself. The caller reads that address and saves
 * it only when the read works. Sends nothing while the phone is offline, for an `rss:` show, when a
 * read never happened, or past [FeedReportLimiter]'s limits. Never sends an address.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
 * COMPAT(own-ids): broken followed feeds reach the registry. Permanent: never remove (MIGRATION.md §5).
 */
class FeedReporter(
    private val send: suspend (V2FeedReportRequest) -> V2FeedReportResponse?,
    private val limiter: FeedReportLimiter,
    private val isOnline: () -> Boolean,
    private val enabled: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val keepFree: Int = 0,
) {
    /**
     * @param status what the failed read saw, as the route takes it; null when no read happened
     * @return the API's HTTPS address when it differs from [savedFeedUrl], else null
     */
    suspend fun report(podcastId: String, status: String?, savedFeedUrl: String?): String? =
        (reportTarget(podcastId, status, savedFeedUrl) as? V2FeedReadTarget.Direct)?.url

    /**
     * [report] for a caller that can also read through boxlore's transport: the answer's address
     * as a read target, [V2FeedReadTarget.ViaApi] when it is http-only (`fetchVia: proxy`,
     * `V2-ROUTES.md` `/v2/feed-report`), or a different HTTPS address. Null when nothing was sent or
     * the answer names the address already read.
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, the show page's failure ladder).
     */
    suspend fun reportTarget(podcastId: String, status: String?, savedFeedUrl: String?): V2FeedReadTarget? {
        val showId = V2EpisodeIds.parseShowId(podcastId) ?: return null
        val answer = sendReport(podcastId, showId, status) ?: return null
        if (answer.fetchVia == V2FetchVia.PROXY) return answer.readTarget(showId)
        if (answer.fetchVia != null && answer.fetchVia != V2FetchVia.DIRECT) return null
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(answer.fetchUrl) ?: return null
        return https.takeIf { it != savedFeedUrl?.trim() }?.let(V2FeedReadTarget::Direct)
    }

    /** One report within the limits; null when none was sent or it got no answer. */
    private suspend fun sendReport(podcastId: String, showId: Long, status: String?): V2FeedReportResponse? {
        if (status == null || !V2FeedReportStatus.isValid(status) || !enabled()) return null
        if (!isOnline() || !limiter.allows(podcastId, nowMillis(), keepFree)) return null
        val answer =
            try {
                send(V2FeedReportRequest(id = showId, status = status))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Feed report for $podcastId failed", e)
                null
            } ?: return null
        limiter.record(podcastId, nowMillis())
        return answer
    }

    companion object {
        private const val TAG = "FeedReporter"

        /** The production [send]: one `POST /v2/feed-report`; null for any answer but 2xx (a 429 included), which counts nothing. */
        suspend fun sendWith(api: BoxLoreApiV2, request: V2FeedReportRequest): V2FeedReportResponse? {
            val response = api.reportFeed(request).awaitResponse()
            return if (response.isSuccessful) response.body() else null
        }
    }
}
