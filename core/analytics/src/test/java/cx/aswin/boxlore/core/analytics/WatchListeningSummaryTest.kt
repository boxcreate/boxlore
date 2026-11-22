package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The watch's merges go out as one `watch_changes_merged` a day, counted across app launches. */
class WatchListeningSummaryTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    /** One SharedPreferences file that outlives each simulated launch. */
    private val disk =
        object : WatchListeningSummary.Store {
            var day: WatchListeningSummary.Day? = null
            var sentThrough: Long? = null

            override fun load(): WatchListeningSummary.Day? = day

            override fun save(day: WatchListeningSummary.Day?) {
                this.day = day
            }

            override fun lastSent(): Long? = sentThrough

            override fun saveLastSent(epochDay: Long) {
                sentThrough = epochDay
            }
        }

    private val nov21 = 20_778L
    private val nov22 = nov21 + 1

    private fun sent() = recorder.filter { it.first == "watch_changes_merged" }.map { it.second }

    @BeforeEach
    fun setUp() {
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
    }

    // Regression: a watch playing for four hours sent 211 events, one per place it reported.
    @Test
    fun `a whole day of merges sends nothing until the next day, then one event with the totals`() {
        repeat(240) { WatchListeningSummary.add(disk, episodes = 1, follows = 0, today = nov21) }
        WatchListeningSummary.add(disk, episodes = 0, follows = 2, today = nov21)
        assertEquals(emptyList<Map<String, Any>>(), sent())

        WatchListeningSummary.add(disk, episodes = 1, follows = 0, today = nov22)

        assertEquals(
            listOf(mapOf("day" to "2026-11-21", "merge_count" to 241, "episode_count" to 240, "follow_change_count" to 2)),
            sent(),
        )
        assertEquals(WatchListeningSummary.Day(nov22, merges = 1, episodes = 1, follows = 0), disk.day)
    }

    @Test
    fun `startup sends a finished day once and leaves today's counts waiting`() {
        WatchListeningSummary.add(disk, episodes = 3, follows = 1, today = nov21)

        WatchListeningSummary.sendFinished(disk, today = nov21)
        assertEquals(emptyList<Map<String, Any>>(), sent())

        WatchListeningSummary.sendFinished(disk, today = nov22)
        WatchListeningSummary.sendFinished(disk, today = nov22)

        assertEquals(listOf(mapOf("day" to "2026-11-21", "merge_count" to 1, "episode_count" to 3, "follow_change_count" to 1)), sent())
        assertNull(disk.day)
    }

    @Test
    fun `a clock that goes back and forth sends each day once, and never before it ends`() {
        WatchListeningSummary.add(disk, episodes = 1, follows = 0, today = nov21)
        WatchListeningSummary.add(disk, episodes = 1, follows = 0, today = nov22)
        // The clock goes back: the merge joins the open 22 November instead of finishing it early.
        WatchListeningSummary.add(disk, episodes = 1, follows = 0, today = nov21)
        WatchListeningSummary.sendFinished(disk, today = nov21)
        WatchListeningSummary.add(disk, episodes = 1, follows = 1, today = nov22)
        assertEquals(listOf("2026-11-21"), sent().map { it["day"] })

        WatchListeningSummary.sendFinished(disk, today = nov22 + 1)

        assertEquals(
            listOf(
                mapOf("day" to "2026-11-21", "merge_count" to 1, "episode_count" to 1, "follow_change_count" to 0),
                mapOf("day" to "2026-11-22", "merge_count" to 3, "episode_count" to 3, "follow_change_count" to 1),
            ),
            sent(),
        )
    }

    @Test
    fun `a merge dated on a day already sent counts toward the next day`() {
        WatchListeningSummary.add(disk, episodes = 2, follows = 0, today = nov21)
        WatchListeningSummary.sendFinished(disk, today = nov22)

        WatchListeningSummary.add(disk, episodes = 1, follows = 0, today = nov21)

        assertEquals(listOf("2026-11-21"), sent().map { it["day"] })
        assertEquals(WatchListeningSummary.Day(nov22, merges = 1, episodes = 1, follows = 0), disk.day)
    }

    @Test
    fun `the stored day reads back, and anything unreadable starts over`() {
        val day = WatchListeningSummary.Day(nov21, merges = 12, episodes = 11, follows = 1)

        assertEquals(day, WatchListeningSummary.decode(WatchListeningSummary.encode(day)))
        assertNull(WatchListeningSummary.decode(null))
        assertNull(WatchListeningSummary.decode("20778;12"))
        assertNull(WatchListeningSummary.decode("20778;x;11;1"))
    }
}
