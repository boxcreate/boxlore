package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadOutcome
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadRequest
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.FeedFetchCompleteness
import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2FeedReadTarget
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.core.rss.ShowPageFeedRules
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/** Where a show page's episodes come from. */
enum class ShowPageSource {
    /** A show added by its feed address: the RSS table. */
    RSS,

    /** A followed show whose stored catalog is complete: Room only. */
    FOLLOWED_CATALOG,

    /** The show's own feed, through the phone's stored copy (`feed_first` on). */
    FEED,

    /** The server's list of the show's episodes (`/v2/episodes`). */
    SERVER_LIST,
}

/**
 * What a feed-first show page holds when it opens: whether a stored copy exists and is the whole
 * archive, whether it is old enough to read again ([ShowPageFeedRules.RECHECK_AFTER_MS]), and
 * whether the feed lists its oldest items first.
 */
data class ShowPageFeedState(
    val cached: Boolean,
    val archiveComplete: Boolean,
    val recheckDue: Boolean,
    val oldestFirst: Boolean,
)

/** How a show page's feed read ended. */
enum class ShowPageFeedResult {
    /** The feed was read and stored. */
    UPDATED,

    /** The feed answered that the stored copy is current. */
    UNCHANGED,

    /** The phone is offline: the stored copy is shown as it is. */
    OFFLINE,

    /** The feed couldn't be read after a retry and a report: the page shows the server's list with a notice. */
    FALLBACK,
}

/** [result] of a read, and whether the stored copy now holds the whole archive. */
data class ShowPageFeedRead(val result: ShowPageFeedResult, val archiveComplete: Boolean)

/**
 * The feed-first show page of a Podcast Index show: it reads the show's own feed into the phone's
 * stored copy ([ShowPageFeedPort]) and decides when the page falls back to the server's list.
 *
 * The failure ladder of one read ([read]):
 * 1. Offline: nothing is read and nothing is reported; the stored copy is shown as it is.
 * 2. The address comes from `/v2/podcast` (the registry), else the saved one. An http-only address
 *    is read through boxlore's transport (`/v2/feed/<id>`).
 * 3. A failed read is tried once more, except after a 404 or 410.
 * 4. Then the failure is reported (`/v2/feed-report`, rate-limited, never offline); when the answer
 *    names a different HTTPS address, that one is read.
 * 5. Then the page uses `/v2/episodes` with a notice ([ShowPageFeedResult.FALLBACK]) until a later
 *    read of the feed works.
 *
 * Each read sends one `feed_fetch_result` (decision D11). Once a day it also removes the stored
 * copies of unfollowed shows not opened for 30 days, under a size cap (decision D10).
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
@Suppress("LongParameterList", "kotlin:S107") // Each dependency is one rung of the ladder or a test seam.
class FeedFirstShowPages(
    private val copies: ShowPageFeedPort?,
    private val registry: suspend (Long) -> V2PodcastResponse?,
    private val transport: suspend (Long) -> ShowPageFeedPort.Document?,
    private val reporter: FeedReporter?,
    private val isOnline: () -> Boolean,
    private val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val fallbackShows = ConcurrentHashMap.newKeySet<String>()
    private val metas = ConcurrentHashMap<String, PodcastMeta>()

    @Volatile
    private var lastCleanUpAt: Long? = null

    /** True while [podcastId]'s page uses the server's list because its feed couldn't be read. */
    fun isInFallback(podcastId: String): Boolean = podcastId in fallbackShows

    /** The show details last given to [open] or [read], for episodes listed from the stored copy. */
    fun metaFor(podcastId: String): PodcastMeta? = metas[podcastId]

    suspend fun cached(podcastId: String): ShowPageFeedPort.CachedShow? = safely(null) { copies?.cached(podcastId) }

    /**
     * What the page holds as it opens; notes the opening and runs the daily cleanup. A page that
     * opens tries the feed again, even after an earlier page fell back to the server's list.
     */
    suspend fun open(podcastId: String, meta: PodcastMeta): ShowPageFeedState {
        remember(podcastId, meta)
        fallbackShows.remove(podcastId)
        val cached = cached(podcastId)
        val now = nowMillis()
        if (cached != null) safely(Unit) { copies?.markOpened(podcastId, now) }
        cleanUpDaily(now, podcastId)
        return ShowPageFeedState(
            cached = cached != null,
            archiveComplete = cached?.archiveComplete == true,
            recheckDue = cached == null || now - cached.fetchedAt !in 0 until ShowPageFeedRules.RECHECK_AFTER_MS,
            oldestFirst = cached?.oldestFirst == true,
        )
    }

    /** One read of the show's feed, [scope] deep, down the failure ladder. [savedFeedUrl]: the address the phone has, if any. */
    suspend fun read(podcastId: String, meta: PodcastMeta, scope: Scope, savedFeedUrl: String?): ShowPageFeedRead {
        remember(podcastId, meta)
        val showId = V2EpisodeIds.parseShowId(podcastId)
        if (copies == null || showId == null) return finish(podcastId, ShowPageFeedResult.FALLBACK)
        if (!isOnline()) return finish(podcastId, ShowPageFeedResult.OFFLINE)
        val answer = safely(null) { registry(showId) }
        val target =
            answer?.readTarget(showId)
                ?: savedFeedUrl?.takeIf(String::isNotBlank)?.let(V2FeedReadTarget::Direct)
                ?: return finish(podcastId, ShowPageFeedResult.FALLBACK)
        val request = ReadRequest(podcastId, "", scope, meta, answer?.validEpNs)
        val attempt = afterReport(podcastId, request, withRetry(request, target), savedFeedUrl) ?: return finish(podcastId, ShowPageFeedResult.OFFLINE)
        report(podcastId, attempt)
        return when (attempt.outcome) {
            is ReadOutcome.Failed -> finish(podcastId, ShowPageFeedResult.FALLBACK)
            is ReadOutcome.Stored -> finish(podcastId, ShowPageFeedResult.UPDATED)
            ReadOutcome.NotModified -> finish(podcastId, ShowPageFeedResult.UNCHANGED)
        }
    }

    /** The read, and once more when it failed in a way a second try may not (not a 404 or 410). */
    private suspend fun withRetry(request: ReadRequest, target: V2FeedReadTarget): Attempt {
        val first = Attempt(target, readOnce(request, target))
        return if (first.outcome is ReadOutcome.Failed && first.retryable) first.copy(outcome = readOnce(request, target)) else first
    }

    /**
     * After a failed read, the report and a read of the address it names, when it names a
     * different one. Null when the phone went offline meanwhile: nothing is reported then.
     */
    private suspend fun afterReport(podcastId: String, request: ReadRequest, attempt: Attempt, savedFeedUrl: String?): Attempt? {
        val failed = attempt.outcome as? ReadOutcome.Failed ?: return attempt
        if (!isOnline()) return null
        val used = (attempt.target as? V2FeedReadTarget.Direct)?.url ?: savedFeedUrl
        val reported = safely(null) { reporter?.report(podcastId, failed.reportStatus, used) } ?: return attempt
        val next = V2FeedReadTarget.Direct(reported)
        return Attempt(next, readOnce(request, next))
    }

    private data class Attempt(val target: V2FeedReadTarget, val outcome: ReadOutcome) {
        /** A 404 or 410 says the address is gone: a second try would see the same. */
        val retryable: Boolean get() = (outcome as? ReadOutcome.Failed)?.reportStatus !in GONE_STATUSES
    }

    private suspend fun readOnce(request: ReadRequest, target: V2FeedReadTarget): ReadOutcome {
        val port = copies ?: return ReadOutcome.Failed(OTHER)
        return safely(ReadOutcome.Failed(OTHER)) {
            when (target) {
                is V2FeedReadTarget.Direct -> port.read(request.copy(feedUrl = target.url))
                is V2FeedReadTarget.ViaApi -> {
                    val document = transport(target.showId) ?: return@safely ReadOutcome.Failed(OTHER)
                    port.store(request.copy(feedUrl = document.finalUrl), document)
                }
            }
        }
    }

    /**
     * The read's result, with whether the copy is now the whole archive. A fallback puts the page on
     * the server's list; a read that worked takes it back to the feed; offline changes neither.
     */
    private suspend fun finish(podcastId: String, result: ShowPageFeedResult): ShowPageFeedRead {
        when (result) {
            ShowPageFeedResult.FALLBACK -> fallbackShows.add(podcastId)
            ShowPageFeedResult.UPDATED, ShowPageFeedResult.UNCHANGED -> fallbackShows.remove(podcastId)
            ShowPageFeedResult.OFFLINE -> Unit
        }
        return ShowPageFeedRead(result, archiveComplete(podcastId))
    }

    private suspend fun archiveComplete(podcastId: String): Boolean = cached(podcastId)?.archiveComplete == true

    private fun remember(podcastId: String, meta: PodcastMeta) {
        if (meta.title != null || meta.imageUrl != null) metas[podcastId] = meta
    }

    private suspend fun cleanUpDaily(now: Long, keep: String) {
        val last = lastCleanUpAt
        if (last != null && now - last in 0 until CLEANUP_INTERVAL_MS) return
        lastCleanUpAt = now
        safely(0) { copies?.cleanUp(now, keep) ?: 0 }
    }

    /** The one `feed_fetch_result` of this read (decision D11). Never carries an address. */
    private fun report(podcastId: String, attempt: Attempt) {
        try {
            telemetry.feedFetched(showPageFetchReport(podcastId, attempt.target, attempt.outcome))
        } catch (error: IllegalStateException) {
            android.util.Log.w(TAG, "feed_fetch_result not sent", error)
        }
    }

    companion object {
        private const val OTHER = "other"
        private val GONE_STATUSES = setOf("http_404", "http_410")
        private const val CLEANUP_INTERVAL_MS = 24L * 60L * 60L * 1000L

        /** The `feed_fetch_result` outcome of a failed read, from its `/v2/feed-report` status. */
        fun failureOutcome(reportStatus: String): FeedFetchOutcome = when {
            reportStatus in GONE_STATUSES -> FeedFetchOutcome.GONE
            reportStatus.startsWith(HTTP_PREFIX) -> FeedFetchOutcome.HTTP_ERROR
            reportStatus == "timeout" -> FeedFetchOutcome.TIMEOUT
            reportStatus == "parse" -> FeedFetchOutcome.PARSE_ERROR
            else -> FeedFetchOutcome.NETWORK_ERROR
        }
    }
}

private const val TAG = "FeedFirstShowPages"
private const val HTTP_PREFIX = "http_"

/** The `feed_fetch_result` of one show-page read of [target] that ended in [outcome]. */
internal fun showPageFetchReport(podcastId: String, target: V2FeedReadTarget, outcome: ReadOutcome): FeedFetchReport.ShowPage {
    val source = if (target is V2FeedReadTarget.ViaApi) FeedFetchSource.TRANSPORT else FeedFetchSource.FEED
    return when (outcome) {
        is ReadOutcome.Stored -> {
            val completeness = if (outcome.complete) FeedFetchCompleteness.COMPLETE else FeedFetchCompleteness.PARTIAL
            FeedFetchReport.ShowPage(podcastId, source, FeedFetchOutcome.OK, completeness)
        }
        ReadOutcome.NotModified -> FeedFetchReport.ShowPage(podcastId, source, FeedFetchOutcome.NOT_MODIFIED)
        is ReadOutcome.Failed -> {
            val status = outcome.reportStatus.takeIf { it.startsWith(HTTP_PREFIX) }?.removePrefix(HTTP_PREFIX)?.toIntOrNull()
            FeedFetchReport.ShowPage(podcastId, source, FeedFirstShowPages.failureOutcome(outcome.reportStatus), httpStatus = status)
        }
    }
}

/** [block]'s answer, or [fallback] when it fails; cancellation propagates. */
private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    android.util.Log.w(TAG, "Show page feed step failed", error)
    fallback
}
