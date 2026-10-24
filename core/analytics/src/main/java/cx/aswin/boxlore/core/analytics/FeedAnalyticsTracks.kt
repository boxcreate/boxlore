package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection

/**
 * Feed reliability events: `feed_fetch_result` and `feed_url_corrected`. Properties are IDs,
 * categories and counts only; a show ID that looks like an address is left out rather than sent.
 *
 * Phase P4, plan section "PostHog".
 */
internal object FeedAnalyticsTracks {
    const val FEED_FETCH_RESULT = "feed_fetch_result"
    const val FEED_URL_CORRECTED = "feed_url_corrected"

    const val CONTEXT_SHOW_PAGE = "show_page"
    const val CONTEXT_BACKGROUND_PASS = "background_pass"

    fun trackFeedFetchResult(report: FeedFetchReport) {
        val properties =
            when (report) {
                is FeedFetchReport.ShowPage -> showPageProperties(report)
                is FeedFetchReport.BackgroundPass -> {
                    // A pass that read no shows has nothing to report.
                    if (report.showsCount == 0) return
                    backgroundPassProperties(report)
                }
            }
        AnalyticsEmit.event(FEED_FETCH_RESULT, properties)
    }

    fun trackFeedUrlCorrected(correction: FeedUrlCorrection) {
        val showId = safeShowId(correction.showId) ?: return
        AnalyticsEmit.event(
            FEED_URL_CORRECTED,
            mapOf(
                "show_id" to showId,
                "reason" to correction.reason.wireName,
                "step" to correction.step.wireName,
            ),
        )
    }

    private fun showPageProperties(report: FeedFetchReport.ShowPage): Map<String, Any> = buildMap {
        put("context", CONTEXT_SHOW_PAGE)
        put("source", report.source.wireName)
        put("result", report.outcome.wireName)
        safeShowId(report.showId)?.let { put("show_id", it) }
        if (!report.outcome.isFailure) {
            report.completeness?.let { put("completeness", it.wireName) }
        }
        if (report.outcome in HTTP_STATUS_OUTCOMES) {
            report.httpStatus?.takeIf { it in HTTP_STATUS_RANGE }?.let { put("http_status", it) }
        }
    }

    private fun backgroundPassProperties(report: FeedFetchReport.BackgroundPass): Map<String, Any> = mapOf(
        "context" to CONTEXT_BACKGROUND_PASS,
        "source" to report.source.wireName,
        "result" to report.passResult.wireName,
        "shows_count" to report.showsCount,
        "ok_count" to report.okCount.coerceAtLeast(0),
        "not_modified_count" to report.notModifiedCount.coerceAtLeast(0),
        "failed_count" to report.failedCount.coerceAtLeast(0),
    )

    /** Show IDs are Podcast Index numbers or hashed `rss:` IDs; anything with a scheme or host is dropped. */
    private fun safeShowId(showId: String): String? = showId.trim().takeIf { it.isNotEmpty() && "/" !in it && "." !in it }

    private val HTTP_STATUS_RANGE = 100..599

    /** `http_status` is sent only with these results, so ok and not_modified rows never carry 200 or 304. */
    private val HTTP_STATUS_OUTCOMES = setOf(FeedFetchOutcome.HTTP_ERROR, FeedFetchOutcome.GONE)
}
