package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private fun episode(id: String, podcastId: String) = Episode(
        id = id,
        title = "T",
        description = "",
        audioUrl = "https://example.com/a.mp3",
        publishedDate = 1L,
        duration = 60,
        podcastId = podcastId,
    )
}
