package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.FeedFetchCompleteness
import cx.aswin.boxlore.core.model.FeedFetchOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** `feed_fetch_result` and `feed_url_corrected` property shapes, through the [AnalyticsHelper] façade. */
class FeedAnalyticsTracksTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
    }

    @Test
    fun `a successful show page read reports its show, source and completeness`() {
        AnalyticsHelper.trackFeedFetchResult(
            FeedFetchReport.ShowPage(
                showId = "920666",
                source = FeedFetchSource.FEED,
                outcome = FeedFetchOutcome.OK,
                completeness = FeedFetchCompleteness.PARTIAL,
            ),
        )

        assertEquals(
            listOf(
                "feed_fetch_result" to
                    mapOf(
                        "context" to "show_page",
                        "source" to "feed",
                        "result" to "ok",
                        "show_id" to "920666",
                        "completeness" to "partial",
                    ),
            ),
            recorder,
        )
    }

    @Test
    fun `a failed show page read reports the status but no completeness`() {
        AnalyticsHelper.trackFeedFetchResult(
            FeedFetchReport.ShowPage(
                showId = "rss:ab12",
                source = FeedFetchSource.TRANSPORT,
                outcome = FeedFetchOutcome.HTTP_ERROR,
                completeness = FeedFetchCompleteness.COMPLETE,
                httpStatus = 503,
            ),
        )

        val props = recorder.single().second
        assertEquals("http_error", props["result"])
        assertEquals("transport", props["source"])
        assertEquals(503, props["http_status"])
        assertEquals("rss:ab12", props["show_id"])
        assertFalse("completeness" in props)
    }

    @Test
    fun `an impossible http status is left out`() {
        AnalyticsHelper.trackFeedFetchResult(
            FeedFetchReport.ShowPage("1", FeedFetchSource.FEED, FeedFetchOutcome.HTTP_ERROR, httpStatus = -1),
        )

        assertFalse("http_status" in recorder.single().second)
    }

    @Test
    fun `a background pass sends one summary with counts and no show`() {
        AnalyticsHelper.trackFeedFetchResult(
            FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, okCount = 7, notModifiedCount = 2, failedCount = 1),
        )

        assertEquals(
            listOf(
                "feed_fetch_result" to
                    mapOf(
                        "context" to "background_pass",
                        "source" to "feed",
                        "result" to "mixed",
                        "shows_count" to 10,
                        "ok_count" to 7,
                        "not_modified_count" to 2,
                        "failed_count" to 1,
                    ),
            ),
            recorder,
        )
    }

    @Test
    fun `a background pass result is ok without failures and failed when every read failed`() {
        AnalyticsHelper.trackFeedFetchResult(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 3, 4, 0))
        AnalyticsHelper.trackFeedFetchResult(FeedFetchReport.BackgroundPass(FeedFetchSource.CACHE, 0, 0, 5))

        assertEquals(listOf("ok", "failed"), recorder.map { it.second["result"] })
    }

    @Test
    fun `a background pass that read no feeds sends nothing`() {
        AnalyticsHelper.trackFeedFetchResult(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 0, 0, 0))

        assertTrue(recorder.isEmpty())
    }

    @Test
    fun `an address swap reports show, reason and step`() {
        AnalyticsHelper.trackFeedUrlCorrected(
            FeedUrlCorrection("41504", FeedUrlCorrectionReason.MOVED, FeedUrlCorrectionStep.UPGRADE_REPAIR),
        )

        assertEquals(
            listOf(
                "feed_url_corrected" to
                    mapOf("show_id" to "41504", "reason" to "moved", "step" to "upgrade_repair"),
            ),
            recorder,
        )
    }

    @Test
    fun `a show id that looks like an address is never sent`() {
        val address = "https://feeds.example.com/show.xml"
        AnalyticsHelper.trackFeedFetchResult(
            FeedFetchReport.ShowPage(address, FeedFetchSource.FEED, FeedFetchOutcome.OK),
        )
        AnalyticsHelper.trackFeedUrlCorrected(
            FeedUrlCorrection(address, FeedUrlCorrectionReason.DEAD, FeedUrlCorrectionStep.DAILY_CHECK),
        )

        assertEquals(listOf("feed_fetch_result"), recorder.map { it.first })
        assertFalse("show_id" in recorder.single().second)
        assertTrue(recorder.flatMap { it.second.values }.none { it.toString().contains("example.com") })
    }

    @Test
    fun `every source, result, reason and step uses its glossary name`() {
        assertEquals(
            listOf("feed", "transport", "v2_episodes", "cache"),
            FeedFetchSource.entries.map { it.wireName },
        )
        assertEquals(
            listOf("ok", "not_modified", "network_error", "timeout", "http_error", "gone", "parse_error"),
            FeedFetchOutcome.entries.map { it.wireName },
        )
        assertEquals(listOf("dead", "moved", "registry"), FeedUrlCorrectionReason.entries.map { it.wireName })
        assertEquals(
            listOf("upgrade_repair", "daily_check", "feed_refresh", "feed_report"),
            FeedUrlCorrectionStep.entries.map { it.wireName },
        )
    }
}
