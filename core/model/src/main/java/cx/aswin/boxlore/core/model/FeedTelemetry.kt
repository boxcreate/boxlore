package cx.aswin.boxlore.core.model

/**
 * Where a feed read got its episodes, reported as `source` on `feed_fetch_result`.
 *
 * Phase P4 (feed-first show pages and address repair), plan section "PostHog".
 * COMPAT(own-ids): analytics vocabulary for feed reads. Permanent: never remove (MIGRATION.md §5).
 */
enum class FeedFetchSource(val wireName: String) {
    /** The show's own feed at its publisher address. */
    FEED("feed"),

    /** The same feed through boxlore's transport address. */
    TRANSPORT("transport"),

    /** The server's episode list for the show, used when its feed can't be read. */
    V2_EPISODES("v2_episodes"),

    /** The copy already stored on the phone. */
    CACHE("cache"),
}

/**
 * How one feed read ended, reported as `result` on a show-page `feed_fetch_result`.
 *
 * Phase P4, plan section "PostHog". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
enum class FeedFetchOutcome(val wireName: String) {
    OK("ok"),

    /** The server answered 304; the stored copy is current. */
    NOT_MODIFIED("not_modified"),

    /** No connection, DNS or TLS failure. */
    NETWORK_ERROR("network_error"),
    TIMEOUT("timeout"),

    /** An HTTP error other than [GONE]; the status goes in `http_status`. */
    HTTP_ERROR("http_error"),

    /** 404 or 410: the address looks dead. */
    GONE("gone"),

    /** The body was read but isn't a usable feed. */
    PARSE_ERROR("parse_error"),
    ;

    val isFailure: Boolean get() = this != OK && this != NOT_MODIFIED
}

/**
 * Whether a successful read covered the newest items only or the whole feed (`completeness`).
 *
 * Phase P4, plan section "Feed-first show page". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
enum class FeedFetchCompleteness(val wireName: String) {
    PARTIAL("partial"),
    COMPLETE("complete"),
}

/**
 * Summary `result` of a background pass: every read worked, some failed, or all failed.
 *
 * Phase P4, plan section "PostHog". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
enum class FeedPassResult(val wireName: String) {
    OK("ok"),
    MIXED("mixed"),
    FAILED("failed"),
}

/**
 * One `feed_fetch_result` event: once per show-page read ([ShowPage]), or once per background
 * pass and source as an aggregate ([BackgroundPass]), never once per show per pass. Carries
 * no addresses.
 *
 * Phase P4, plan section "PostHog". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
sealed interface FeedFetchReport {
    val source: FeedFetchSource

    /** A show page read the show's episodes. [completeness] applies to successful reads. */
    data class ShowPage(
        val showId: String,
        override val source: FeedFetchSource,
        val outcome: FeedFetchOutcome,
        val completeness: FeedFetchCompleteness? = null,
        val httpStatus: Int? = null,
    ) : FeedFetchReport

    /** A background pass over many shows (daily check, refresh pass, upgrade repair). */
    data class BackgroundPass(
        override val source: FeedFetchSource,
        val okCount: Int,
        val notModifiedCount: Int,
        val failedCount: Int,
    ) : FeedFetchReport {
        /** Shows read in the pass; negative counts are treated as zero. */
        val showsCount: Int
            get() = okCount.coerceAtLeast(0) + notModifiedCount.coerceAtLeast(0) + failedCount.coerceAtLeast(0)

        val passResult: FeedPassResult
            get() =
                when {
                    failedCount <= 0 -> FeedPassResult.OK
                    failedCount >= showsCount -> FeedPassResult.FAILED
                    else -> FeedPassResult.MIXED
                }
    }
}

/**
 * Why a followed show's saved address was replaced (`reason` on `feed_url_corrected`).
 *
 * Phase P4, plan section "Upgrade repair". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
enum class FeedUrlCorrectionReason(val wireName: String) {
    /** The saved address stopped answering. */
    DEAD("dead"),

    /** The feed said it moved (permanent redirect or `itunes:new-feed-url`). */
    MOVED("moved"),

    /** The server's show registry holds a different confirmed address. */
    REGISTRY("registry"),
}

/**
 * Which path replaced the address (`step` on `feed_url_corrected`).
 *
 * Phase P4, plan section "Upgrade repair". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
enum class FeedUrlCorrectionStep(val wireName: String) {
    /** The one-time repair after the update. */
    UPGRADE_REPAIR("upgrade_repair"),

    /** The daily address check of followed shows. */
    DAILY_CHECK("daily_check"),

    /** A feed read that followed a move. */
    FEED_REFRESH("feed_refresh"),

    /** A broken-feed report that answered with another address. */
    FEED_REPORT("feed_report"),
}

/**
 * One `feed_url_corrected` event, sent once per address swap. Carries no addresses.
 *
 * Phase P4, plan section "Upgrade repair". COMPAT(own-ids): permanent (MIGRATION.md §5).
 */
data class FeedUrlCorrection(
    val showId: String,
    val reason: FeedUrlCorrectionReason,
    val step: FeedUrlCorrectionStep,
)
