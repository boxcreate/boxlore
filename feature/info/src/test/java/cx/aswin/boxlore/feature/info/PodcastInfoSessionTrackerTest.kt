package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PodcastInfoSessionTrackerTest {
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    private val tracker =
        PodcastInfoSessionTracker(
            PodcastInfoRouteArgs(entryPoint = "search", genreFilter = "Comedy", scrollDepth = 3, searchQuery = "news"),
        )

    @BeforeEach
    fun installSink() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @AfterEach
    fun restoreSink() {
        restore()
    }

    /** The session events: screen views carry no time spent. */
    private fun sessions() = events.filter { (name, props) -> name == "podcast_detail_viewed" && "time_spent_seconds" in props }.map { it.second }

    @Test
    fun `the session is sent once per visit, with the follow state the page opened with`() {
        tracker.followStateAtOpen(true)
        tracker.followStateAtOpen(false)

        tracker.screenExited("p1") { "Show" }
        tracker.screenExited("p1") { "Show" }

        val session = sessions().single()
        assertEquals("p1", session["podcast_id"])
        assertEquals("Show", session["podcast_name"])
        assertEquals(true, session["is_subscribed"])
    }

    @Test
    fun `nothing is sent before a show loads, and the name isn't read`() {
        var nameReads = 0

        tracker.screenExited("") {
            nameReads++
            "Show"
        }
        assertEquals(emptyList<Map<String, Any>>(), sessions())
        assertEquals(0, nameReads)

        tracker.screenExited("p1") { "Show" }
        assertEquals(1, sessions().size)
    }

    @Test
    fun `coming back after an exit starts a new session, and only then`() {
        tracker.screenResumed()
        tracker.screenExited("p1") { "Show" }
        tracker.screenExited("p1") { "Show" }
        assertEquals(1, sessions().size)

        tracker.screenResumed()
        tracker.screenExited("p1") { "Show" }
        assertEquals(2, sessions().size)
    }

    @Test
    fun `the session reports what the listener did, counting each episode once`() {
        tracker.subscriptionToggled(wasSubscribed = false)
        tracker.subscriptionToggled(wasSubscribed = true)
        tracker.searched()
        tracker.sorted()
        tracker.played(listOf("e1", "e2"))
        tracker.played(listOf("e1"))
        tracker.clicked("e3")
        tracker.clicked("e3")

        tracker.screenExited("p1") { "Show" }

        val session = sessions().single()
        assertEquals(false, session["is_subscribed"])
        assertEquals(true, session["did_subscribe"])
        assertEquals(true, session["did_unsubscribe"])
        assertEquals(true, session["did_search"])
        assertEquals(true, session["did_sort_episodes"])
        assertEquals(2, session["episodes_played_count"])
        assertEquals(1, session["episodes_clicked_count"])
    }

    @Test
    fun `a quiet visit reports nothing done`() {
        tracker.screenExited("p1") { "Show" }

        val session = sessions().single()
        listOf("did_subscribe", "did_unsubscribe", "did_search", "did_sort_episodes").forEach { flag ->
            assertEquals(false, session[flag], flag)
        }
        assertEquals(0, session["episodes_played_count"])
        assertEquals(0, session["episodes_clicked_count"])
    }

    @Test
    fun `a screen view carries the route it was opened from`() {
        tracker.screenViewed("p1", "Show")
        tracker.screenViewed("p2", null)

        val views = events.filter { it.first == "podcast_detail_viewed" }.map { it.second }
        assertEquals(
            mapOf(
                "podcast_id" to "p1",
                "podcast_name" to "Show",
                "source" to "search",
                "genre_filter" to "Comedy",
                "scroll_depth" to 3,
                "search_query" to "news",
            ),
            views[0],
        )
        assertFalse("podcast_name" in views[1])
        assertEquals(emptyList<Map<String, Any>>(), sessions())
    }
}
