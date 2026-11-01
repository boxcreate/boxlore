package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The screen trail, `screen_viewed`, `screen_left`, and the screens a play is asked from. */
class ScreenViewsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        ScreenViews.resetForTest()
        PendingEntryPoint.resetForTest()
        PendingEntryPoint.consume()
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
        ScreenViews.resetForTest()
        PendingEntryPoint.resetForTest()
        PendingEntryPoint.consume()
    }

    private fun register(screen: String) = ScreenViews.Registration().also { ScreenViews.show(it, screen) }

    @Test
    fun `a new screen becomes current, with the one it covered before it`() {
        register("home")
        val show = ScreenViews.Registration()

        assertEquals("home", ScreenViews.show(show, "podcast_detail"))
        assertEquals("home", ScreenViews.show(show, "podcast_detail"), "showing the same screen again keeps the trail")
        assertEquals(ScreenViews.Trail(current = "podcast_detail", previous = "home"), ScreenViews.trail)
    }

    @Test
    fun `closing a story with a card open gives Insights back, whichever goes first`() {
        listOf(true, false).forEach { cardFirst ->
            ScreenViews.resetForTest()
            register("history_insights")
            val story = register("listening_story")
            val card = register("listening_story_card")

            if (cardFirst) ScreenViews.leave(card) else ScreenViews.leave(story)
            if (cardFirst) ScreenViews.leave(story) else ScreenViews.leave(card)

            assertEquals("history_insights", ScreenViews.trail.current, "card first: $cardFirst")
        }
    }

    @Test
    fun `a show page opened from another stays current when the first one goes away`() {
        register("home")
        val showA = register("podcast_detail")
        register("podcast_detail")

        ScreenViews.leave(showA)

        assertEquals(ScreenViews.Trail(current = "podcast_detail", previous = "podcast_detail"), ScreenViews.trail)
    }

    @Test
    fun `the next new-folder step replaces the last one, which becomes the screen before`() {
        register("subscriptions")
        val flow = register("new_folder_contents")

        assertEquals("new_folder_contents", ScreenViews.show(flow, "new_folder_name"))
        assertEquals(ScreenViews.Trail(current = "new_folder_name", previous = "new_folder_contents"), ScreenViews.trail)

        ScreenViews.leave(flow)
        assertEquals(ScreenViews.Trail(current = "subscriptions", previous = "new_folder_name"), ScreenViews.trail)
    }

    @Test
    fun `a sheet closing gives the page back, with the sheet as the screen before`() {
        val player = register("player_full")
        val sheet = register("player_speed")

        ScreenViews.leave(sheet)
        assertEquals(ScreenViews.Trail(current = "player_full", previous = "player_speed"), ScreenViews.trail)

        register("player_queue")
        ScreenViews.leave(player)
        assertEquals("player_queue", ScreenViews.trail.current, "the page under a sheet going away changes nothing")
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
                "library",
                "subscriptions",
                "liked_episodes",
                "downloads",
                "listening_history",
                "learn",
            ),
            recorder.map { it.second["screen"] },
        )
        recorder.forEach { (_, props) -> assertFalse(props["time_spent_seconds"] == null, "${props["screen"]} keeps its summary") }
    }

    @Test
    fun `a play carries the screens showing when it was asked for, without overriding the caller`() {
        var now = 1_000L
        PendingEntryPoint.clock = { now }
        register("home")
        register("podcast_detail")
        PendingEntryPoint.noteRequest()
        register("settings")
        now += 2_000L

        assertEquals(
            mapOf("entry_point" to "podcast_detail", "screen" to "podcast_detail", "previous_screen" to "home"),
            PendingEntryPoint.withScreens(mapOf("entry_point" to "podcast_detail")),
            "the screens at the request, not the one the listener moved to while it prepared",
        )
        assertEquals("custom", PendingEntryPoint.withScreens(mapOf("screen" to "custom"))["screen"])

        now += 10_001L
        assertEquals(mapOf("entry_point" to "x"), PendingEntryPoint.withScreens(mapOf("entry_point" to "x")), "an old request credits nothing")
    }

    @Test
    fun `without a request from the app's screens, nothing is added`() {
        register("home")

        assertEquals(mapOf("entry_point" to "notification"), PendingEntryPoint.withScreens(mapOf("entry_point" to "notification")))
    }
}
