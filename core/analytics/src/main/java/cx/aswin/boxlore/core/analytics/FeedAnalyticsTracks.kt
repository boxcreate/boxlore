package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.UpgradeRepairReport

/**
 * Feed reliability events: `feed_fetch_result`, `feed_url_corrected` and the one
 * `own_ids_upgrade_completed` per upgraded install. Properties are IDs,
 * categories and counts only; a show ID that looks like an address is left out rather than sent.
 *
 * Phase P4, plan section "PostHog".
 */
internal object FeedAnalyticsTracks {
    const val FEED_FETCH_RESULT = "feed_fetch_result"
    const val FEED_URL_CORRECTED = "feed_url_corrected"
    const val OWN_IDS_UPGRADE_COMPLETED = "own_ids_upgrade_completed"

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

    /**
     * `own_ids_upgrade_completed`: what the one-time upgrade repair did, in counts. The catalog
     * sends it at most once per install; nothing here identifies a show or an address.
     *
     * Phase P4 PR 18, plan section "Upgrade repair".
     */
    fun trackOwnIdsUpgradeCompleted(report: UpgradeRepairReport) {
        AnalyticsEmit.event(
            OWN_IDS_UPGRADE_COMPLETED,
            mapOf(
                "outcome" to report.outcome.wireName,
                "shows_count" to report.showsCount.coerceAtLeast(0),
                "shows_gave_up_count" to report.showsGaveUp.coerceAtLeast(0),
                "addresses_swapped_count" to report.addressesSwapped.coerceAtLeast(0),
                "notifications_reregistered_count" to report.notificationsReregistered.coerceAtLeast(0),
                "runs_count" to report.runs.coerceAtLeast(0),
                "elapsed_hours" to (report.elapsedMillis.coerceAtLeast(0L) / MILLIS_PER_TENTH_HOUR) / TENTHS_PER_HOUR,
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

    private fun backgroundPassProperties(report: FeedFetchReport.BackgroundPass): Map<String, Any> = buildMap {
        put("context", CONTEXT_BACKGROUND_PASS)
        put("source", report.source.wireName)
        put("result", report.passResult.wireName)
        put("shows_count", report.showsCount)
        put("ok_count", report.okCount.coerceAtLeast(0))
        put("not_modified_count", report.notModifiedCount.coerceAtLeast(0))
        put("failed_count", report.failedCount.coerceAtLeast(0))
        // Sent only when a show's namespace differs (decision D12), so ordinary passes stay as they were.
        if (report.namespaceMismatchCount > 0) put("namespace_mismatch_count", report.namespaceMismatchCount)
    }

    /** Show IDs are Podcast Index numbers or hashed `rss:` IDs; anything with a scheme or host is dropped. */
    private fun safeShowId(showId: String): String? = showId.trim().takeIf { it.isNotEmpty() && "/" !in it && "." !in it }

    private val HTTP_STATUS_RANGE = 100..599

    /** `elapsed_hours` is rounded down to a tenth of an hour. */
    private const val MILLIS_PER_TENTH_HOUR = 360_000L
    private const val TENTHS_PER_HOUR = 10.0

    /** `http_status` is sent only with these results, so ok and not_modified rows never carry 200 or 304. */
    private val HTTP_STATUS_OUTCOMES = setOf(FeedFetchOutcome.HTTP_ERROR, FeedFetchOutcome.GONE)
}
