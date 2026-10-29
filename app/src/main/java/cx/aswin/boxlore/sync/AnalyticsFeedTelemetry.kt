package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.UpgradeRepairReport

/**
 * The analytics-backed [FeedTelemetryPort] that `AppContainer` hands to catalog code, so feed
 * reads, address swaps and the end of the upgrade repair reach `feed_fetch_result`,
 * `feed_url_corrected` and `own_ids_upgrade_completed` without catalog depending on analytics. A
 * reporting failure never reaches the caller.
 *
 * Phase P4, plan section "PostHog".
 */
internal class AnalyticsFeedTelemetry(
    private val fetched: (FeedFetchReport) -> Unit = AnalyticsHelper::trackFeedFetchResult,
    private val corrected: (FeedUrlCorrection) -> Unit = AnalyticsHelper::trackFeedUrlCorrected,
    private val upgraded: (UpgradeRepairReport) -> Unit = AnalyticsHelper::trackOwnIdsUpgradeCompleted,
) : FeedTelemetryPort {
    override fun feedFetched(report: FeedFetchReport) {
        runCatching { fetched(report) }
    }

    override fun feedUrlCorrected(correction: FeedUrlCorrection) {
        runCatching { corrected(correction) }
    }

    override fun upgradeRepairCompleted(report: UpgradeRepairReport) {
        runCatching { upgraded(report) }
    }
}
