package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection

/**
 * The analytics-backed [FeedTelemetryPort] that `AppContainer` hands to catalog code, so feed
 * reads and address swaps reach `feed_fetch_result` and `feed_url_corrected` without catalog
 * depending on analytics. A reporting failure never reaches the caller.
 *
 * Phase P4, plan section "PostHog".
 */
internal class AnalyticsFeedTelemetry(
    private val fetched: (FeedFetchReport) -> Unit = AnalyticsHelper::trackFeedFetchResult,
    private val corrected: (FeedUrlCorrection) -> Unit = AnalyticsHelper::trackFeedUrlCorrected,
) : FeedTelemetryPort {
    override fun feedFetched(report: FeedFetchReport) {
        runCatching { fetched(report) }
    }

    override fun feedUrlCorrected(correction: FeedUrlCorrection) {
        runCatching { corrected(correction) }
    }
}
