package cx.aswin.boxlore.core.catalog

import android.content.SharedPreferences
import android.util.Log
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
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
 * day in all (the API takes 20 a day from one phone).
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
 */
class FeedReportLimiter(private val stamps: FeedReportStamps) {
    /** Claims a report of [podcastId] at [nowMillis]; false when the show or the day is used up. */
    @Synchronized
    fun tryClaim(podcastId: String, nowMillis: Long): Boolean {
        val since = nowMillis - PER_SHOW_INTERVAL_MS
        val recent = stamps.all().filterValues { it > since && it <= nowMillis }
        if (podcastId in recent || recent.size >= DAILY_CAP) return false
        stamps.mark(podcastId, nowMillis, keepSince = since)
        return true
    }

    companion object {
        const val PER_SHOW_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val DAILY_CAP = 20
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
) {
    /**
     * @param status what the failed read saw, as the route takes it; null when no read happened
     * @return the API's HTTPS address when it differs from [savedFeedUrl], else null
     */
    suspend fun report(podcastId: String, status: String?, savedFeedUrl: String?): String? {
        if (status == null || !V2FeedReportStatus.isValid(status) || !enabled()) return null
        val showId = V2EpisodeIds.parseShowId(podcastId) ?: return null
        if (!isOnline() || !limiter.tryClaim(podcastId, nowMillis())) return null
        val answer =
            try {
                send(V2FeedReportRequest(id = showId, status = status))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Feed report for $podcastId failed", e)
                null
            } ?: return null
        if (answer.fetchVia != null && answer.fetchVia != V2FetchVia.DIRECT) return null
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(answer.fetchUrl) ?: return null
        return https.takeIf { it != savedFeedUrl?.trim() }
    }

    companion object {
        private const val TAG = "FeedReporter"

        /** The production [send]: one `POST /v2/feed-report`; null for any answer but 200. */
        suspend fun sendWith(api: BoxLoreApiV2, request: V2FeedReportRequest): V2FeedReportResponse? {
            val response = api.reportFeed(request).awaitResponse()
            return if (response.isSuccessful) response.body() else null
        }
    }
}
