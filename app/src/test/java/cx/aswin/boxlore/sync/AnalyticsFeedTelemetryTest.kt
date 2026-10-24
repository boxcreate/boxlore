package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.FeedFetchCompleteness
import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsFeedTelemetryTest {
    @Test
    fun reportsReachTheGlossaryEvents() {
        val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(recorder)
        try {
            val telemetry = AnalyticsFeedTelemetry()
            telemetry.feedFetched(
                FeedFetchReport.ShowPage("920666", FeedFetchSource.CACHE, FeedFetchOutcome.OK, FeedFetchCompleteness.COMPLETE),
            )
            telemetry.feedUrlCorrected(
                FeedUrlCorrection("920666", FeedUrlCorrectionReason.REGISTRY, FeedUrlCorrectionStep.UPGRADE_REPAIR),
            )
        } finally {
            restore()
        }

        assertEquals(listOf("feed_fetch_result", "feed_url_corrected"), recorder.map { it.first })
        assertEquals("cache", recorder[0].second["source"])
        assertEquals("registry", recorder[1].second["reason"])
    }

    @Test
    fun aReportingFailureNeverReachesTheCaller() {
        val telemetry =
            AnalyticsFeedTelemetry(
                fetched = { error("analytics down") },
                corrected = { error("analytics down") },
            )

        telemetry.feedFetched(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 1, 0, 0))
        telemetry.feedUrlCorrected(
            FeedUrlCorrection("1", FeedUrlCorrectionReason.DEAD, FeedUrlCorrectionStep.DAILY_CHECK),
        )
    }
}
