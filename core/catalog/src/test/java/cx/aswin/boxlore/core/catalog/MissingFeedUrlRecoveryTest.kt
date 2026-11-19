package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Missing-address recovery in the foreground pass: least recently asked first, at most 20 a pass,
 * asked only while the `/podcast` route has room, and stamped only once the server answered.
 */
class MissingFeedUrlRecoveryTest {
    private val now = 10L * FeedUrlLookup.LOOKUP_INTERVAL_MS

    @Before
    fun forgetTries() = MissingFeedUrlRecovery.clearTriesForTests()

    @Test
    fun showsWhoseLookupKeepsFailingDontHoldLaterDueShowsBack() = runTest {
        // Reviewed: a failed lookup kept its old stamp, so 20 shows that kept failing took every pass.
        val ids = (1..21).map { "show-$it" }.toSet()
        val stamps = ids.associateWith { it.removePrefix("show-").toLong() }.toMutableMap()
        val first = dueTargets(ids, stamps)
        assertEquals((1..20).map { "show-$it" }, first)

        first.forEach { id -> MissingFeedUrlRecovery.recoverOne(id, now, { FeedUrlLookup.NoAnswer }, { show, at -> stamps[show] = at }, { _, _ -> }) }

        assertTrue("the show not tried yet goes next", "show-21" in dueTargets(ids, stamps))
        assertEquals("a failed lookup stamps nothing", (1..21).associate { "show-$it" to it.toLong() }, stamps)
    }

    @Test
    fun aPassTakesAtMost20ShowsLeastRecentlyAskedFirst() = runTest {
        val stamps = (1..40).associate { "show-$it" to it.toLong() }.toMutableMap()
        val targets = dueTargets((1..40).map { "show-$it" }.toSet() + "rss:skip" + "has-https", stamps)
        assertEquals((1..20).map { "show-$it" }, targets)
    }

    @Test
    fun showsAskedInTheLastDayOrStampedAheadWait() = runTest {
        val stamps =
            mutableMapOf(
                "fresh" to now - 1_000L,
                "http-only" to FeedUrlLookup.Answered(null, httpOnly = true).stampAt(now - 2 * FeedUrlLookup.LOOKUP_INTERVAL_MS),
                "due" to now - FeedUrlLookup.LOOKUP_INTERVAL_MS,
                "never" to 0L,
            )
        assertEquals(listOf("never", "due"), dueTargets(stamps.keys, stamps))
    }

    @Test
    fun showsThe429LeftUnansweredAreNotStampedAndComeFirstNextPass() = runTest {
        val ids = (1..35).map { "show-$it" }.toSet()
        val stamps = ids.associateWith { 0L }.toMutableMap()
        val saved = mutableMapOf<String, String>()
        var calls = 0
        val api: (String) -> PodcastDetailsAnswer = { id ->
            if (++calls > 12) {
                PodcastDetailsAnswer.RateLimited(60_000L)
            } else {
                PodcastDetailsAnswer.Found(TestFixtures.podcast(id = id).copy(feedUrl = "https://feeds.example/$id"))
            }
        }

        val firstPass = dueTargets(ids, stamps)
        firstPass.forEach { id -> recover(id, now, api, stamps, saved) }

        assertEquals(20, firstPass.size)
        assertEquals("12 answered and were saved", 12, saved.size)
        val unanswered = firstPass.drop(12)
        assertTrue("a 429 stamps nothing", unanswered.all { stamps.getValue(it) == 0L })

        val nextPass = dueTargets(ids, stamps)
        val notReached = ids - firstPass.toSet()
        assertEquals(20, nextPass.size)
        assertTrue("the shows not reached go first", nextPass.containsAll(notReached))
        assertTrue("then the unanswered ones, still due", (nextPass - notReached).all { it in unanswered })
    }

    @Test
    fun aFullRouteBudgetAsksNothingAndStampsNothing() = runTest {
        val budget = ApiRouteBudget(perMinute = 0, nowMs = { 0L })
        var asked = 0
        val lookups =
            PacedPodcastLookups(budget) {
                asked++
                PodcastDetailsAnswer.NotFound
            }
        val stamps = mutableMapOf("show" to 0L)

        MissingFeedUrlRecovery.recoverOne("show", now, { FeedUrlLookup.of(lookups.whenRoom(it)) }, { id, at -> stamps[id] = at }, { _, _ -> })

        assertEquals(0, asked)
        assertEquals(0L, stamps.getValue("show"))
    }

    @Test
    fun anHttpOnlyAnswerWaitsAWeekAndANotFoundADay() = runTest {
        val stamps = mutableMapOf("http" to 0L, "gone" to 0L)
        val answers =
            mapOf(
                "http" to PodcastDetailsAnswer.Found(TestFixtures.podcast(id = "http").copy(feedUrl = "http://feeds.example/http")),
                "gone" to PodcastDetailsAnswer.NotFound,
            )
        stamps.keys.toList().forEach { id -> recover(id, now, { answers.getValue(it) }, stamps, mutableMapOf()) }

        assertEquals(now, stamps.getValue("gone"))
        assertFalse("not due the next day", FeedUrlLookup.isDue(stamps.getValue("http"), now + 2 * FeedUrlLookup.LOOKUP_INTERVAL_MS))
        assertTrue("due after a week", FeedUrlLookup.isDue(stamps.getValue("http"), now + FeedUrlLookup.HTTP_ONLY_BACKOFF_MS))
    }

    private suspend fun dueTargets(ids: Set<String>, stamps: Map<String, Long>): List<String> = MissingFeedUrlRecovery.dueTargets(
        ids = ids,
        now = now,
        feedUrlOf = { id -> if (id == "has-https") "https://feeds.example/ok" else null },
        lastLookupAt = { id -> stamps[id] ?: 0L },
    )

    private suspend fun recover(id: String, at: Long, api: (String) -> PodcastDetailsAnswer, stamps: MutableMap<String, Long>, saved: MutableMap<String, String>) =
        MissingFeedUrlRecovery.recoverOne(
            id = id,
            now = at,
            lookup = { FeedUrlLookup.of(api(it)) },
            stamp = { show, stamp -> stamps[show] = stamp },
            saveHttps = { show, url -> saved[show] = url },
        )
}
