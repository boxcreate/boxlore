package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The screen trail, `screen_viewed`, `screen_left`, and the screens a play is asked from. */
class ScreenViewsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        ScreenViews.resetForTest()
        PendingEntryPoint.consume()
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
        ScreenViews.resetForTest()
        PendingEntryPoint.consume()
    }

    @Test
    fun `entering screens moves the trail, and showing the current screen again keeps it`() {
        assertNull(ScreenViews.enter("home"))
        assertEquals("home", ScreenViews.enter("podcast_detail"))
        assertEquals("home", ScreenViews.enter("podcast_detail"), "a rotation or a return keeps the screen before")
        assertEquals(ScreenViews.Trail(current = "podcast_detail", previous = "home"), ScreenViews.trail)
    }

    @Test
    fun `a sheet closing gives the page back, with the sheet as the screen before`() {
        ScreenViews.enter("player_full")
        ScreenViews.enter("player_speed_sheet")

        ScreenViews.leave("player_speed_sheet")
        assertEquals(ScreenViews.Trail(current = "player_full", previous = "player_speed_sheet"), ScreenViews.trail)

        ScreenViews.leave("home")
        assertEquals("player_full", ScreenViews.trail.current, "leaving a screen that isn't current changes nothing")
    }

    @Test
    fun `screen_viewed names the screen and the one before`() {
        AnalyticsHelper.trackScreenViewed("history_insights", previousScreen = "library_hub", properties = mapOf("tab" to "insights"))
        AnalyticsHelper.trackScreenViewed("settings", previousScreen = null)

        val (first, second) = recorder.filter { it.first == "screen_viewed" }.map { it.second }
        assertEquals(mapOf("screen" to "history_insights", "previous_screen" to "library_hub", "tab" to "insights"), first)
        assertEquals(mapOf("screen" to "settings"), second)
    }

    @Test
    fun `leaving summaries go out as screen_left, never under the screen's view event`() {
        AnalyticsHelper.trackPodcastInfoScreenSession("p1", "Pod", 12f, false, true, false, false, false, 1, 2)
        AnalyticsHelper.trackEpisodeInfoScreenSession(mapOf("episode_id" to "e1", "time_spent_seconds" to 5f))
        AnalyticsHelper.trackExploreScreenSession(30f, 1, 2, 3, 4, 5, "for_you", null, null)
        AnalyticsHelper.trackLibraryHubSession(8f, "downloads")
        AnalyticsHelper.trackLibrarySubscriptionsSession(9f, 1, false, null, 2, 3)
        AnalyticsHelper.trackLibraryLikedSession(4f, 1, 0)
        AnalyticsHelper.trackLibraryDownloadsSession(4f, 1, 0)
        AnalyticsHelper.trackLibraryHistorySession(4f, 1, 0)
        AnalyticsHelper.trackLearnScreenSession(6f, 1, 1, 1, 1, 1)

        assertEquals(setOf("screen_left"), recorder.map { it.first }.toSet())
        assertEquals(
            listOf(
                "podcast_detail",
                "episode_detail",
                "explore",
                "library_hub",
                "library_subscriptions",
                "library_liked",
                "library_downloads",
                "library_history",
                "learn",
            ),
            recorder.map { it.second["screen"] },
        )
        recorder.forEach { (_, props) -> assertFalse(props["time_spent_seconds"] == null, "${props["screen"]} keeps its summary") }
    }

    @Test
    fun `a play asked from a screen carries that screen and the one before, without overriding the caller`() {
        ScreenViews.enter("home")
        ScreenViews.enter("podcast_detail")

        assertEquals(
            mapOf("entry_point" to "podcast_detail", "screen" to "podcast_detail", "previous_screen" to "home"),
            PendingEntryPoint.withScreens(mapOf("entry_point" to "podcast_detail")),
        )
        assertEquals("custom", PendingEntryPoint.withScreens(mapOf("screen" to "custom"))["screen"])
    }

    @Test
    fun `before any screen, nothing is added`() {
        assertEquals(mapOf("entry_point" to "notification"), PendingEntryPoint.withScreens(mapOf("entry_point" to "notification")))
    }
}
