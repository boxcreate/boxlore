package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection

/**
 * Reports feed reads and saved-address swaps without depending on `:core:analytics`, so catalog
 * code can report them. `AppContainer` wires the analytics-backed implementation; [NONE] is the
 * default for tests and callers built without it. Implementations must not throw.
 *
 * Phase P4, plan section "PostHog" (`feed_fetch_result`, `feed_url_corrected`).
 */
interface FeedTelemetryPort {
    /** One show-page read, or one background pass per source as an aggregate. */
    fun feedFetched(report: FeedFetchReport)

    /** One saved address replaced for a followed show. */
    fun feedUrlCorrected(correction: FeedUrlCorrection)

    companion object {
        /** Reports nothing. */
        val NONE: FeedTelemetryPort =
            object : FeedTelemetryPort {
                override fun feedFetched(report: FeedFetchReport) = Unit

                override fun feedUrlCorrected(correction: FeedUrlCorrection) = Unit
            }
    }
}
