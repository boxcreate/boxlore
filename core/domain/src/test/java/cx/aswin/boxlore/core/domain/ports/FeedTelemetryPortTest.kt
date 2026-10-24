package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeedTelemetryPortTest {
    @Test
    fun `the default port accepts reports and does nothing`() {
        FeedTelemetryPort.NONE.feedFetched(FeedFetchReport.ShowPage("1", FeedFetchSource.FEED, FeedFetchOutcome.OK))
        FeedTelemetryPort.NONE.feedUrlCorrected(
            FeedUrlCorrection("1", FeedUrlCorrectionReason.DEAD, FeedUrlCorrectionStep.DAILY_CHECK),
        )
    }

    @Test
    fun `a fake port records what catalog code reports`() {
        val seen = mutableListOf<Any>()
        val port =
            object : FeedTelemetryPort {
                override fun feedFetched(report: FeedFetchReport) {
                    seen += report
                }

                override fun feedUrlCorrected(correction: FeedUrlCorrection) {
                    seen += correction
                }
            }
        val pass = FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 1, 0, 0)

        port.feedFetched(pass)

        assertEquals(listOf<Any>(pass), seen)
    }
}
