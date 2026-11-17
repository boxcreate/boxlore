package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode

/** Room fields needed to resolve a direct-feed library tip. */
internal data class DirectFeedTipMeta(
    val feedUrl: String?,
    val title: String?,
    val imageUrl: String?,
    val genre: String?,
    val artist: String?,
    val knownTip: Episode?,
)

/**
 * Result of an opted-in publisher-feed resolve (tip plus whether extras were persisted).
 * [failed]: the feed couldn't be read this pass; [addressGone]: and its saved address looks dead
 * ([LocalEpisodeCatalogPort.RefreshOutcome.Failure.addressGone]); [reportStatus]: what the read saw,
 * for `/v2/feed-report` ([LocalEpisodeCatalogPort.RefreshOutcome.Failure.reportStatus]);
 * [readFeedUrl]: the saved address the failed read used, the only one a recovered address may replace.
 */
internal data class DirectFeedResolveResult(
    val tip: Episode?,
    val persisted: Boolean,
    val failed: Boolean = false,
    val addressGone: Boolean = false,
    val reportStatus: String? = null,
    val readFeedUrl: String? = null,
)

/**
 * What the daily address check and broken-feed reports need from the app (P4 PR 14): where report
 * times are kept across launches, and whether the phone is online (reports are never sent offline).
 * [reportLimiter] is the app's one limiter, shared with the show page (P4 PR 16) so both stay inside
 * one daily limit; the default builds one on [reportStamps].
 */
data class FeedAddressUpkeep(
    val reportStamps: FeedReportStamps = MemoryFeedReportStamps(),
    val isOnline: () -> Boolean = { true },
    val reportLimiter: FeedReportLimiter = FeedReportLimiter(reportStamps),
)

/** The daily address check and the broken-feed reporter of one [SubscriptionForegroundSync] (P4 PR 14). */
internal data class FeedAddressJobs(
    val dailyCheck: FeedAddressDailyCheck,
    val reporter: FeedReporter,
)
