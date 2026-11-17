package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ApiRouteBudget
import cx.aswin.boxlore.core.catalog.PacedPodcastLookups
import cx.aswin.boxlore.core.catalog.PodcastDetailsAnswer
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.testing.TestFixtures
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A cloud pull on a new phone with a large library: it fills in placeholder rows only while the
 * `/podcast` route has room and stops at the first 429, and the paced catch-up ([healStubRows])
 * fills in every row left, waiting out the API's 429s, so no "Loading..." row survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionStubHealTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao

    /** Virtual time: the budget advances it instead of sleeping, and the fake API reads it. */
    private val clock = AtomicLong(0L)
    private val calls = mutableListOf<Long>()
    private var apiPerMinute = 30

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aPullOf154FollowsLeavesNoPlaceholderAfterTheCatchUpAlthoughTheApiAnswers429After30() = runBlocking {
        val resolver = resolver()

        pull(resolver, 154)
        assertTrue("the pull stays within one minute's budget", calls.size <= ApiRouteBudget.DEFAULT_PER_MINUTE)
        assertTrue("the rest are placeholders for now", stubs() > 0)

        resolver.healStubRows()

        assertEquals("no row is left as Loading...", 0, stubs())
        dao.getSubscribedPodcastsList().forEach { row ->
            assertEquals("Show ${row.podcastId}", row.title)
            assertTrue(row.imageUrl.isNotBlank())
        }
    }

    @Test
    fun thePullStopsAskingAfterThe429() = runBlocking {
        apiPerMinute = 10
        pull(resolver(), 154)
        assertEquals("10 answers, one 429, then nothing more this pull", 11, calls.size)
    }

    @Test
    fun catchUpRunsWaitOut429sAndLaterRunsFinishWhatOneLeft() = runBlocking {
        // Other requests share the minute: the API refuses past 20, below the budget's 25.
        apiPerMinute = 20
        val resolver = resolver()
        pull(resolver, 154)

        var runs = 0
        while (stubs() > 0 && runs < 20) {
            resolver.healStubRows()
            runs++
        }

        assertEquals(0, stubs())
        assertTrue("a run that met too many 429s left the rest for the next one", runs > 1)
    }

    @Test
    fun failedAnswersForSeveralRowsInARowStopTheRunAndKeepThePlaceholders() = runBlocking {
        var asked = 0
        val resolver =
            SubscriptionSyncResolver(
                podcastDao = dao,
                detailsLookups =
                PacedPodcastLookups(budget()) {
                    asked++
                    PodcastDetailsAnswer.Unavailable
                },
            )
        pull(placeholdersOnly(), 6)
        assertEquals(0, resolver.healStubRows())
        assertEquals(6, stubs())
        assertEquals("no connection or the server down: the run stops after three rows", 3, asked)
    }

    @Test
    fun oneRowTheServerKeepsFailingForDoesNotHoldUpTheRowsAfterIt() = runBlocking {
        val failing = "1001"
        val resolver =
            SubscriptionSyncResolver(
                podcastDao = dao,
                detailsLookups =
                PacedPodcastLookups(budget()) { id ->
                    if (id == failing) PodcastDetailsAnswer.Unavailable else fakeLookup(id)
                },
            )
        pull(placeholdersOnly(), 8)

        resolver.healStubRows()

        val rows = dao.getSubscribedPodcastsList()
        assertEquals("only the failing show is still a placeholder", listOf(failing), rows.filter(SubscriptionSyncResolver::isStub).map { it.podcastId })
        // It is asked again on the next run, not given up on.
        resolver.healStubRows()
        assertEquals(1, stubs())
    }

    /** A pull that saves every follow as a placeholder and asks nothing. */
    private fun placeholdersOnly() = SubscriptionSyncResolver(podcastDao = dao, detailsLookups = null)

    private fun resolver() = SubscriptionSyncResolver(podcastDao = dao, detailsLookups = PacedPodcastLookups(budget(), ::fakeLookup))

    private fun budget() = ApiRouteBudget(nowMs = { clock.get() }, sleep = { clock.addAndGet(it) })

    /** `GET /podcast` with the API's per-minute limit: past [apiPerMinute] in a minute it answers 429, Retry-After 60. */
    private fun fakeLookup(id: String): PodcastDetailsAnswer {
        val now = clock.get()
        val recent = calls.count { now - it < ApiRouteBudget.WINDOW_MS }
        calls += now
        if (recent >= apiPerMinute) return PodcastDetailsAnswer.RateLimited(retryAfterMs = 60_000L)
        return PodcastDetailsAnswer.Found(TestFixtures.podcast(id = id, title = "Show $id", imageUrl = "https://cdn.example/$id.jpg"))
    }

    private suspend fun pull(resolver: SubscriptionSyncResolver, count: Int) {
        (1..count).forEach { index ->
            resolver.resolveSubscription(
                UserSubscriptionSyncDto(podcastId = "${1000 + index}", subscribedAt = 1_000L, feedUrl = "https://feeds.example/$index", updatedAt = 1_000L),
                syncedAt = 2_000L,
            )
        }
    }

    private suspend fun stubs(): Int = dao.getSubscribedPodcastsList().count(SubscriptionSyncResolver::isStub)
}
