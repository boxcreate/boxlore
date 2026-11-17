package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import cx.aswin.boxlore.core.network.model.V2FeedReportResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Shows whose own feed failed in a foreground pass (`DirectFeedRecovery`; reports since own-episode-IDs
 * P4 PR 14, plan section 11): every failed read of a Podcast Index show is reported, a dead address
 * tries the report's answer before the daily check's `/podcast` fallback, and that fallback runs
 * at most once a day per show.
 */
class DirectFeedRecoveryTest {
    @Test
    fun `every failed read is reported, and a dead address tries the report's answer before the podcast lookup`() = runTest {
        val urls =
            mapOf(
                "reported" to "https://old.example/reported.xml",
                "fallback" to "https://old.example/fallback.xml",
                "slow" to "https://feeds.example/slow.xml",
                "rss:own" to "https://feeds.example/own.xml",
            )
        val reports = mutableListOf<Pair<String, String?>>()
        val rechecked = mutableListOf<String>()
        val saved = mutableMapOf<String, AddressCandidate>()
        SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
            loadIds = { urls.keys },
            loadPodcastMeta = { id -> DirectFeedTipMeta(urls[id], id, null, null, null, null) },
            syncChunk = { emptyMap() },
            saveLatest = { _, _ -> },
            directFeed =
            DirectFeedSyncSeams(
                loadOptedInIds = { urls.keys },
                resolveFeedTip = { id, meta ->
                    when {
                        meta.feedUrl?.startsWith("https://new.example/") == true ->
                            DirectFeedResolveResult(tip = episode(id = "-9", podcastId = id), persisted = true)
                        id == "slow" -> DirectFeedResolveResult(tip = null, persisted = false, failed = true, reportStatus = "timeout")
                        else -> DirectFeedResolveResult(tip = null, persisted = false, failed = true, addressGone = true, reportStatus = "http_410")
                    }
                },
                reportFeed = { id, status ->
                    reports += id to status
                    if (id == "reported" || id == "slow") "https://new.example/$id.xml" else null
                },
                recheckAddress = { id ->
                    rechecked += id
                    "https://new.example/$id.xml"
                },
                saveAddress = { id, candidate -> saved[id] = candidate },
            ),
        )
        assertEquals(
            setOf("reported" to "http_410", "fallback" to "http_410", "slow" to "timeout"),
            reports.toSet(),
            "every failed read of a Podcast Index show is reported, never an rss: show",
        )
        assertEquals(listOf("fallback"), rechecked, "the podcast lookup only when the report offered nothing")
        assertEquals(FeedUrlCorrectionStep.FEED_REPORT, saved["reported"]?.step)
        assertEquals(FeedUrlCorrectionStep.DAILY_CHECK, saved["fallback"]?.step)
        assertFalse(saved.containsKey("slow"), "a passing failure keeps its address, whatever the report answered")
        val change = saved.getValue("reported").change("reported", nowMillis = 5L)
        assertEquals("https://old.example/reported.xml", change.onlyReplacing, "only the address that failed is replaced")
        assertEquals("https://old.example/fallback.xml", saved.getValue("fallback").change("fallback", 5L).onlyReplacing)
    }

    @Test
    fun `a dead address is re-checked once a day, and only once the server answers`() = runTest {
        // Same per-show stamp as the daily address check, so a show is never checked twice a day.
        val day = FeedAddressDailyCheck.CHECK_INTERVAL_MS
        var stamped: Long? = null
        suspend fun check(now: Long, last: Long, confirmed: String?, saved: String? = "https://old.example/f.xml", id: String = "123") =
            DirectFeedRecovery.recheckGoneFeedAddress(
                id = id,
                now = now,
                savedFeedUrl = saved,
                lastLookupAt = { last },
                markLookup = { stamped = it },
                lookupFeedUrl = { confirmed },
            )

        assertEquals("https://new.example/f.xml", check(now = 2 * day, last = 0L, confirmed = "https://new.example/f.xml"))
        assertEquals(2 * day, stamped)

        stamped = null
        assertEquals(null, check(now = 2 * day, last = 2 * day - 1_000L, confirmed = "https://new.example/f.xml"), "within a day")
        assertEquals(null, check(now = 3 * day, last = 0L, confirmed = null), "no answer")
        assertEquals(null, stamped, "an offline pass doesn't use up the day")
        assertEquals(null, check(now = 3 * day, last = 0L, confirmed = "https://old.example/f.xml"), "same address")
        assertEquals(3 * day, stamped)
        assertEquals(null, check(now = 3 * day, last = 0L, confirmed = "http://new.example/f.xml"), "http only")
        assertEquals(null, check(now = 3 * day, last = 0L, confirmed = "https://new.example/f.xml", id = "rss:abc"), "rss: shows keep theirs")
        assertEquals(null, check(now = 3 * day, last = 0L, confirmed = "https://new.example/f.xml", saved = null), "no saved address: recovery owns it")
    }

    @Test
    fun `with more failing shows than the daily limit, a newly failing show is still reported by the second day`() = runTest {
        // 25 followed shows fail on every read and come first in the pass; one more starts failing too.
        val chronic = (1..25).map { (1000 + it).toString() }
        val failed = (chronic + NEWLY_BROKEN).associateWith { gone("http_404") }
        var now = 100 * DAY
        val (reporter, sent) = reporter { now }

        recoverOnce(failed, reporter)
        now += DAY + 1
        recoverOnce(failed, reporter)

        assertEquals(chronic.take(FeedReportLimiter.DAILY_CAP), sent.take(FeedReportLimiter.DAILY_CAP), "day one: the limit, in the pass's order")
        assertEquals(FeedReportLimiter.DAILY_CAP * 2, sent.size, "the limit still holds each day")
        val secondDay = sent.drop(FeedReportLimiter.DAILY_CAP)
        assertEquals((chronic.drop(FeedReportLimiter.DAILY_CAP) + NEWLY_BROKEN).toSet(), secondDay.take(6).toSet(), "day two starts with the shows not reported yet")
        assertTrue(NEWLY_BROKEN in secondDay, "reported by the second day, not left behind the same 20 every day")
        assertEquals((chronic + NEWLY_BROKEN).toSet(), sent.toSet(), "every failing show takes its turn")
    }

    @Test
    fun `a passing failure never takes the day's limit ahead of a dead address`() = runTest {
        val slow = (1..FeedReportLimiter.DAILY_CAP).map { (2000 + it).toString() }
        val failed = slow.associateWith { DirectFeedResolveResult(tip = null, persisted = false, failed = true, reportStatus = "timeout") } + (NEWLY_BROKEN to gone("http_410"))
        val (reporter, sent) = reporter { 100 * DAY }

        recoverOnce(failed, reporter)

        assertEquals(NEWLY_BROKEN, sent.first())
        assertEquals(FeedReportLimiter.DAILY_CAP, sent.size)
    }

    @Test
    fun `report order puts never-reported shows first, then the ones reported longest ago`() {
        val failed = listOf("a", "b", "c", "d").associateWith { gone("http_404") }

        val order = DirectFeedRecovery.reportOrder(failed, lastReportedAt = mapOf("a" to 300L, "b" to 100L, "c" to 200L))

        assertEquals(listOf("d", "b", "c", "a"), order.map { it.first })
    }

    @Test
    fun everyFailingShowIsReportedInTurnOverManyDailyCapsWithoutAnyGroupStarving() {
        // Reviewed: report times were kept 7 days, so with 180 failing shows the ones reported first
        // ranked with never-reported shows again and the last group could wait forever.
        val limiter = FeedReportLimiter(MemoryFeedReportStamps())
        val ids = (1..180).map { "show-$it" }
        val failed = ids.associateWith { DirectFeedResolveResult(tip = null, persisted = false, failed = true) }
        val reportedOn = mutableMapOf<String, Int>()
        var now = 1_000_000L
        for (day in 1..20) {
            now += FeedReportLimiter.PER_SHOW_INTERVAL_MS + 1
            for ((id, _) in DirectFeedRecovery.reportOrder(failed, limiter.lastReportedAt())) {
                if (!limiter.allows(id, now)) continue
                limiter.record(id, now)
                reportedOn.putIfAbsent(id, day)
            }
        }
        // 180 shows at 20 a day: every one within the first 9 days, then the rotation repeats.
        assertEquals(ids.toSet(), reportedOn.keys)
        assertTrue(reportedOn.values.all { it <= 9 })
    }

    private fun gone(status: String) = DirectFeedResolveResult(tip = null, persisted = false, failed = true, addressGone = true, reportStatus = status)

    /** A [FeedReporter] on one report store whose API answers every report; the shows it sent, in order. */
    private fun reporter(now: () -> Long): Pair<FeedReporter, List<String>> {
        val sent = mutableListOf<String>()
        val reporter =
            FeedReporter(
                send = { request ->
                    sent += request.id.toString()
                    V2FeedReportResponse(queued = true)
                },
                limiter = FeedReportLimiter(MemoryFeedReportStamps()),
                isOnline = { true },
                enabled = { true },
                nowMillis = now,
            )
        return reporter to sent
    }

    private suspend fun recoverOnce(failed: Map<String, DirectFeedResolveResult>, reporter: FeedReporter) = DirectFeedRecovery.recover(
        failed = failed,
        directFeed =
        DirectFeedSyncSeams(
            reportFeed = { id, status -> reporter.report(id, status, "https://old.example/$id.xml") },
            lastReportedAt = reporter::lastReportedAt,
        ),
        readAgain = { _, _ -> null },
        syncFromServer = {},
    )

    private fun episode(id: String, podcastId: String) = Episode(
        id = id,
        title = "T",
        description = "",
        audioUrl = "https://example.com/a.mp3",
        publishedDate = 1L,
        duration = 60,
        podcastId = podcastId,
    )

    private companion object {
        const val DAY = FeedReportLimiter.PER_SHOW_INTERVAL_MS
        const val NEWLY_BROKEN = "9999"
    }
}
